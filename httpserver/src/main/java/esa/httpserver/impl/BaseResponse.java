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
    private volatile Thread committed;
    private volatile boolean ended;

    private static final AtomicReferenceFieldUpdater<BaseResponse, Thread> COMMITTED_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(BaseResponse.class, Thread.class, "committed");

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
        if (isCommitted()) {
            return this;
        }
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
        final boolean casWon = ensureCommittedExclusively();
        if (!casWon && isEnded()) {
            throw new IllegalStateException("Already ended");
        }
        return doWrite(data, offset, length, casWon);
    }

    @Override
    public Future<Void> write(ByteBuf data) {
        if (data == null) {
            data = Unpooled.EMPTY_BUFFER;
        }
        final boolean casWon = ensureCommittedExclusively();
        if (!casWon && isEnded()) {
            throw new IllegalStateException("Already ended");
        }
        return doWrite(data, casWon);
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
        final boolean casWon = ensureEndedExclusively();
        try {
            doEnd(data, offset, length, casWon);
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
        final boolean casWon = ensureEndedExclusively();
        try {
            doEnd(data, casWon, false);
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

        if (!ensureEndedExclusively()) {
            throw new IllegalStateException("Already committed");
        }
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
        return committed != null;
    }

    @Override
    public boolean isEnded() {
        return ended;
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
        return StringUtils.concat("Response",
                isCommitted() ? "![" : "-[",
                request.rawMethod(),
                " ", request.path(),
                " ", Integer.toString(status),
                "]");
    }

    boolean ensureEndedExclusively() {
        boolean committed = ensureCommittedExclusively();
        if (ended) {
            throw new IllegalStateException("Already ended");
        }
        ended = true;
        return committed;
    }

    boolean ensureCommittedExclusively() {
        final Thread current = Thread.currentThread();
        if (COMMITTED_UPDATER.compareAndSet(this, null, current)) {
            return true;
        }

        if (current == committed) {
            return false;
        }

        throw new IllegalStateException("Should be committed by same thread. expected '"
                + committed.getName() + "' but '" + current.getName() + "'");
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
        if (committed == null) {
            final Thread current = Thread.currentThread();
            if (COMMITTED_UPDATER.compareAndSet(this, null, current)) {
                ended = true;
                return true;
            }
        }
        return false;
    }

    abstract Future<Void> doWrite(ByteBuf data, boolean writeHead);

    abstract Future<Void> doWrite(byte[] data, int offset, int length, boolean writeHead);

    abstract void doEnd(ByteBuf data, boolean writeHead, boolean forceClose);

    abstract void doEnd(byte[] data, int offset, int length, boolean writeHead);

    abstract void doSendFile(File file, long offset, long length);

    abstract ChannelFutureListener closure();
}
