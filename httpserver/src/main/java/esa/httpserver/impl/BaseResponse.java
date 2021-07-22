/*
 * Copyright 2020 OPPO ESA Stack Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package esa.httpserver.impl;

import esa.commons.Checks;
import esa.commons.ExceptionUtils;
import esa.commons.StringUtils;
import esa.commons.http.Cookie;
import esa.commons.http.MimeMappings;
import esa.commons.netty.http.CookieImpl;
import esa.httpserver.core.Response;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

import static esa.httpserver.impl.Utils.EMPTY_BYTES;
import static esa.httpserver.impl.Utils.checkIndex;
import static esa.httpserver.impl.Utils.tryFailure;
import static esa.httpserver.impl.Utils.trySuccess;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;

abstract class BaseResponse<REQ extends BaseRequestHandle> implements Response {

    final REQ request;
    final ChannelPromise onEndPromise;
    final ChannelPromise endPromise;
    int status = 200;
    /**
     * Indicates the committing status of current response.
     *
     * <ul>
     *     <li>{@code null}: INIT, response hasn't been committed or ended yet.</li>
     *     <li>An instance {@link Thread}: committing, response is being written by this thread and response has been
     *     committed but hasn't been ended.</li>
     *     <li>{@link #IDLE}: IDLE, response has been committed but hasn't been ended. and can be written now.</li>
     *     <li>{@link #IDLE}: END, response has been ended. and can not be written.</li>
     * </ul>
     */
    private volatile Object committed;

    private static final AtomicReferenceFieldUpdater<BaseResponse, Object> COMMITTED_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(BaseResponse.class, Object.class, "committed");
    private static final Object END = new Object();
    private static final Object IDLE = new Object();

    BaseResponse(REQ req) {
        this.request = req;
        this.onEndPromise = req.ctx.newPromise();
        this.endPromise = req.ctx.newPromise();
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public Response setStatus(int code) {
        this.status = code;
        return this;
    }

    @Override
    public boolean isKeepAlive() {
        return request.runtime.isRunning();
    }

    @Override
    public Response addCookie(Cookie cookie) {
        headers().add(SET_COOKIE, cookie.encode(true));
        return this;
    }

    @Override
    public Response addCookie(String name, String value) {
        return addCookie(new CookieImpl(name, value));
    }

    @Override
    public Future<Void> write(byte[] data) {
        if (data == null) {
            data = EMPTY_BYTES;
        }
        return write0(data, 0, data.length);
    }

    @Override
    public Future<Void> write(byte[] data, int offset) {
        if (data == null) {
            data = EMPTY_BYTES;
        }
        int len = data.length - offset;
        checkIndex(data, offset, len);
        return write0(data, offset, len);
    }

    @Override
    public Future<Void> write(byte[] data, int offset, int length) {
        if (data == null) {
            data = EMPTY_BYTES;
        }
        checkIndex(data, offset, length);
        return write0(data, offset, length);
    }

    private Future<Void> write0(byte[] data, int offset, int length) {
        boolean writeHead = ensureCommitExclusively();
        try {
            return doWrite(data, offset, length, writeHead);
        } finally {
            // set to IDlE state to indicates that the current write has been over and the next write is allowed.
            // we can use lazySet() because the IDLE value set here can be observed by other thread if the call of
            // write is kept in order which needs some means to ensure the memory visibility.
            COMMITTED_UPDATER.lazySet(this, IDLE);
        }
    }

    @Override
    public Future<Void> write(ByteBuf data) {
        if (data == null) {
            data = Unpooled.EMPTY_BUFFER;
        }
        boolean writeHead = ensureCommitExclusively();
        try {
            return doWrite(data, writeHead);
        } finally {
            // set to IDlE to indicates that the current write has been over and the next write is allowed.
            // we can use lazySet() because the IDLE value set here can be observed by other thread if the call of
            // write is kept in order which needs some means to ensure the memory visibility.
            COMMITTED_UPDATER.lazySet(this, IDLE);
        }
    }

    @Override
    public Future<Void> end(byte[] data) {
        if (data == null) {
            data = EMPTY_BYTES;
        }
        return end0(data, 0, data.length);
    }

    @Override
    public Future<Void> end(byte[] data, int offset) {
        if (data == null) {
            data = EMPTY_BYTES;
        }
        int len = data.length - offset;
        checkIndex(data, offset, len);
        return end0(data, offset, len);
    }

    @Override
    public Future<Void> end(byte[] data, int offset, int length) {
        if (data == null) {
            data = EMPTY_BYTES;
        }
        checkIndex(data, offset, length);
        return end0(data, offset, length);
    }

    private Future<Void> end0(byte[] data, int offset, int length) {
        final boolean writeHead = ensureEndExclusively(true);
        try {
            doEnd(data, offset, length, writeHead);
        } finally {
            if (!isKeepAlive()) {
                endPromise.addListener(closure());
            }
            trySuccess(onEndPromise, null);
        }
        return endPromise;
    }

    @Override
    public Future<Void> end(ByteBuf data) {
        if (data == null) {
            data = Unpooled.EMPTY_BUFFER;
        }
        final boolean writeHead = ensureEndExclusively(true);
        try {
            doEnd(data, writeHead, false);
        } finally {
            if (!isKeepAlive()) {
                endPromise.addListener(closure());
            }
            trySuccess(onEndPromise, null);
        }
        return endPromise;
    }

    @Override
    public Future<Void> sendFile(File file, long offset, long length) {
        Checks.checkNotNull(file, "file");
        Checks.checkArg(offset >= 0L, "negative offset");
        Checks.checkArg(length >= 0L, "negative length");

        if (file.isHidden() || !file.exists() || file.isDirectory() || !file.isFile()) {
            ExceptionUtils.throwException(new FileNotFoundException(file.getName()));
        }

        ensureEndExclusively(false);

        if (!headers().contains(CONTENT_TYPE)) {
            String contentType = MimeMappings.getMimeTypeOrDefault(file.getPath());
            headers().set(CONTENT_TYPE, contentType);
        }
        long len = Math.min(length, file.length() - offset);
        long position = Math.min(offset, file.length());
        try {
            doSendFile(file, position, len);
        } finally {
            if (!isKeepAlive()) {
                endPromise.addListener(closure());
            }
            trySuccess(onEndPromise, null);
        }
        return endPromise;
    }

    @Override
    public boolean isWritable() {
        return request.ctx.channel().isWritable();
    }

    @Override
    public boolean isCommitted() {
        return COMMITTED_UPDATER.get(this) != null;
    }

    @Override
    public boolean isEnded() {
        return COMMITTED_UPDATER.get(this) == END;
    }

    @Override
    public Future<Void> onEndFuture() {
        return onEndPromise;
    }

    @Override
    public Future<Void> endFuture() {
        return endPromise;
    }

    @Override
    public ByteBufAllocator alloc() {
        return ctx().alloc();
    }

    @Override
    public String toString() {
        Object committed = COMMITTED_UPDATER.get(this);
        String committedStatus;
        if (committed == null) {
            committedStatus = "-";
        } else if (committed == END) {
            committedStatus = "!";
        } else {
            committedStatus = "~";
        }
        return StringUtils.concat("Response",
                committedStatus, "[",
                request.rawMethod(),
                " ", request.path(),
                " ", Integer.toString(status),
                "]");
    }

    boolean ensureCommitExclusively() {
        final Object current = COMMITTED_UPDATER.get(this);
        if (current == null) {
            // INIT
            final Thread t = Thread.currentThread();
            if (COMMITTED_UPDATER.compareAndSet(this, null, t)) {
                return true;
            }
            throw new IllegalStateException("Concurrent committing['INIT' -> '" + t.getName() + "']");
        } else if (current == END) {
            // END
            throw new IllegalStateException("Already ended");
        } else if (current == IDLE) {
            // IDLE
            final Thread t = Thread.currentThread();
            if (COMMITTED_UPDATER.compareAndSet(this, IDLE, t)) {
                return false;
            }
            throw new IllegalStateException("Concurrent committing['IDLE' -> '" + t.getName() + "']");
        } else {
            // current response is being committing by another thread
            throw new IllegalStateException("Concurrent committing ['" + ((Thread) current).getName() +
                    "' -> '" + Thread.currentThread().getName() + "']");
        }
    }

    boolean ensureEndExclusively(boolean allowCommitted) {
        final Object current = COMMITTED_UPDATER.get(this);
        if (current == null) {
            // INIT
            if (COMMITTED_UPDATER.compareAndSet(this, null, END)) {
                return true;
            }
            throw new IllegalStateException("Concurrent ending['INIT' -> 'END']");
        } else if (current == IDLE) {
            // IDLE
            if (allowCommitted && COMMITTED_UPDATER.compareAndSet(this, IDLE, END)) {
                return false;
            }
            throw new IllegalStateException("Concurrent ending['IDLE' -> 'END']");
        } else if (current == END) {
            // END
            throw new IllegalStateException("Already ended");
        } else {
            // current response is being committing by another thread
            throw new IllegalStateException("Concurrent ending['" + ((Thread) current).getName() + "' -> 'END']");
        }
    }

    ChannelHandlerContext ctx() {
        return request.ctx;
    }

    boolean tryEndWithCrash(Throwable t) {
        if (tryEnd()) {
            // notify as failure
            tryFailure(onEndPromise, t);
            tryFailure(endPromise, t);
            return true;
        }
        return false;
    }

    boolean tryEnd(HttpResponseStatus status,
                   Supplier<ByteBuf> data,
                   boolean forceClose) {
        if (tryEnd()) {
            try {
                this.status = status.code();
                doEnd(data.get(), true, forceClose);
                return true;
            } finally {
                if (forceClose || !isKeepAlive()) {
                    endPromise.addListener(closure());
                }
                trySuccess(onEndPromise, null);
            }
        }
        return false;
    }

    private boolean tryEnd() {
        final Object current = COMMITTED_UPDATER.get(this);
        return current == null && COMMITTED_UPDATER.compareAndSet(this, null, END);
    }

    abstract Future<Void> doWrite(ByteBuf data, boolean writeHead);

    abstract Future<Void> doWrite(byte[] data, int offset, int length, boolean writeHead);

    abstract void doEnd(ByteBuf data, boolean writeHead, boolean forceClose);

    abstract void doEnd(byte[] data, int offset, int length, boolean writeHead);

    abstract void doSendFile(File file, long offset, long length);

    abstract ChannelFutureListener closure();
}
