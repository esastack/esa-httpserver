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
package io.esastack.httpserver.impl;

import io.esastack.commons.net.http.CookieUtil;
import io.esastack.commons.net.http.HttpHeaders;
import io.esastack.commons.net.netty.http.Http1HeadersImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.Signal;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseResponseTest {

    @Test
    void testImplementationOfResponse() {
        final Res res = new Res();
        assertEquals(200, res.status());
        assertFalse(res.isCommitted());
        assertFalse(res.isEnded());
        res.setStatus(201);
        assertEquals(201, res.status());
        assertTrue(res.isKeepAlive());
        res.addCookie("a", "1");
        res.addCookie(CookieUtil.cookie("b", "2"));
        assertEquals(2, res.headers().getAll(SET_COOKIE).size());
        assertEquals("a=1", res.headers().getAll(SET_COOKIE).get(0));
        assertEquals("b=2", res.headers().getAll(SET_COOKIE).get(1));
        assertTrue(res.isWritable());
        assertSame(res.request.ctx.alloc(), res.alloc());
    }

    @Test
    void testWriteNullData() {
        final Res res = new Res();

        assertThrows(IndexOutOfBoundsException.class, () -> res.write(new byte[2], -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> res.write(new byte[2], 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> res.write(new byte[2], 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> res.write(new byte[2], 1, 2));

        assertThrows(IndexOutOfBoundsException.class, () -> res.write(new byte[2], -1));
        assertThrows(IndexOutOfBoundsException.class, () -> res.write(new byte[2], 3));

        assertFalse(res.isEnded());
        assertFalse(res.onEndFuture().isSuccess());
        assertFalse(res.endFuture().isSuccess());
        assertFalse(res.isCommitted());

        assertTrue(res.write((byte[]) null).isSuccess());
        assertSame(Unpooled.EMPTY_BUFFER, res.doWrite1);

        assertTrue(res.write(new byte[0]).isSuccess());
        assertSame(Unpooled.EMPTY_BUFFER, res.doWrite1);

        assertTrue(res.write((ByteBuf) null).isSuccess());
        assertSame(Unpooled.EMPTY_BUFFER, res.doWrite);

        final ByteBuf buf = Unpooled.copiedBuffer("abc".getBytes());
        assertTrue(res.write(buf).isSuccess());
        assertSame(buf, res.doWrite);

        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());
        assertFalse(res.onEndFuture().isSuccess());
        assertFalse(res.endFuture().isSuccess());
    }

    @Test
    void testEndWithNullData() {
        final Res res = new Res();
        assertTrue(res.end((byte[]) null).isSuccess());
        assertSame(Unpooled.EMPTY_BUFFER, res.doEnd1);
        verifyEndStatus(res, true);

        final Res res1 = new Res();
        assertTrue(res1.end(new byte[0]).isSuccess());
        assertSame(Unpooled.EMPTY_BUFFER, res1.doEnd1);
        verifyEndStatus(res1, true);

        final Res res2 = new Res();
        assertTrue(res2.end((ByteBuf) null).isSuccess());
        assertSame(Unpooled.EMPTY_BUFFER, res2.doEnd);
        verifyEndStatus(res2, true);

        final Res res3 = new Res();
        final ByteBuf buf = Unpooled.copiedBuffer("abc".getBytes());
        assertTrue(res3.end(buf).isSuccess());
        assertSame(buf, res3.doEnd);
        verifyEndStatus(res3, true);

        final Res res4 = new Res();
        assertThrows(IndexOutOfBoundsException.class, () -> res4.end(new byte[2], -1, 1));
        verifyEndStatus(res4, false);
        final Res res5 = new Res();
        assertThrows(IndexOutOfBoundsException.class, () -> res5.end(new byte[2], 0, -1));
        verifyEndStatus(res5, false);
        final Res res6 = new Res();
        assertThrows(IndexOutOfBoundsException.class, () -> res6.end(new byte[2], 0, 3));
        verifyEndStatus(res5, false);
        final Res res7 = new Res();
        assertThrows(IndexOutOfBoundsException.class, () -> res7.end(new byte[2], 1, 2));
        verifyEndStatus(res7, false);

        final Res res8 = new Res();
        assertThrows(IndexOutOfBoundsException.class, () -> res8.end(new byte[2], -1));
        verifyEndStatus(res8, false);

        final Res res9 = new Res();
        assertThrows(IndexOutOfBoundsException.class, () -> res9.end(new byte[2], 3));
        verifyEndStatus(res9, false);
    }

    private static void verifyEndStatus(Res res, boolean ok) {
        if (ok) {
            assertTrue(res.isCommitted());
            assertTrue(res.isEnded());
            assertTrue(res.onEndFuture().isSuccess());
            assertTrue(res.endFuture().isSuccess());
        } else {
            assertFalse(res.isCommitted());
            assertFalse(res.isEnded());
            assertFalse(res.onEndFuture().isSuccess());
            assertFalse(res.endFuture().isSuccess());
        }
    }

    @Test
    void testOrderedWrite() {
        final Res res = new Res();
        assertTrue(res.write(new byte[2]).isSuccess());
        assertTrue(res.write(new byte[2]).isSuccess());
        assertTrue(res.write(new byte[2]).isSuccess());
        assertTrue(res.end(new byte[2]).isSuccess());
        verifyEndStatus(res, true);

        final Res res1 = new Res();
        assertTrue(res1.write(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertTrue(res1.write(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertTrue(res1.write(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertTrue(res1.end(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        verifyEndStatus(res1, true);


        final Res res2 = new Res();
        assertTrue(res2.write(new byte[2]).isSuccess());
        assertTrue(res2.write(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertTrue(res2.write(new byte[2]).isSuccess());
        assertTrue(res2.end(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        verifyEndStatus(res2, true);

        final Res res3 = new Res();
        assertTrue(res3.write(new byte[2]).isSuccess());
        assertTrue(res3.write(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertTrue(res3.write(new byte[2]).isSuccess());
        assertTrue(res3.end(new byte[2]).isSuccess());
        verifyEndStatus(res3, true);
    }

    @Test
    void testAlreadyEndedBeforeWriting() {
        final Res res = new Res();
        assertTrue(res.end(new byte[2]).isSuccess());
        assertThrows(IllegalStateException.class, () -> res.write(new byte[2]));
        verifyEndStatus(res, true);

        final Res res1 = new Res();
        assertTrue(res1.end(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertThrows(IllegalStateException.class, () -> res1.write(Unpooled.copiedBuffer("abc".getBytes())));
        verifyEndStatus(res1, true);

        final Res res2 = new Res();
        assertTrue(res2.end(new byte[2]).isSuccess());
        assertThrows(IllegalStateException.class, () -> res2.write(Unpooled.copiedBuffer("abc".getBytes())));
        verifyEndStatus(res2, true);

        final Res res3 = new Res();
        assertTrue(res3.end(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertThrows(IllegalStateException.class, () -> res3.write(new byte[2]));
        verifyEndStatus(res3, true);
    }

    @Test
    void testAlreadyEndedBeforeEnding() {
        final Res res = new Res();
        assertTrue(res.end(new byte[2]).isSuccess());
        assertThrows(IllegalStateException.class, () -> res.end(new byte[2]));
        verifyEndStatus(res, true);

        final Res res1 = new Res();
        assertTrue(res1.end(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertThrows(IllegalStateException.class, () -> res1.end(Unpooled.copiedBuffer("abc".getBytes())));
        verifyEndStatus(res1, true);

        final Res res2 = new Res();
        assertTrue(res2.end(new byte[2]).isSuccess());
        assertThrows(IllegalStateException.class, () -> res2.end(Unpooled.copiedBuffer("abc".getBytes())));
        verifyEndStatus(res2, true);

        final Res res3 = new Res();
        assertTrue(res3.end(Unpooled.copiedBuffer("abc".getBytes())).isSuccess());
        assertThrows(IllegalStateException.class, () -> res3.end(new byte[2]));
    }

    @Test
    void testConcurrentWriting() throws InterruptedException {
        final DelayedRes res = new DelayedRes();

        res.block();
        final Thread t = new Thread(() -> {
            res.write(new byte[2]);
        }, "base-response-test");
        t.start();

        // wait for writing
        while (res.writePromise.isEmpty()) {
        }

        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());

        res.unblock();
        assertThrows(IllegalStateException.class, () -> res.write(new byte[2]));
        assertThrows(IllegalStateException.class, () -> res.write(Unpooled.copiedBuffer("abc".getBytes())));
        assertThrows(IllegalStateException.class, () -> res.end(new byte[2]));
        assertThrows(IllegalStateException.class, () -> res.end(Unpooled.copiedBuffer("abc".getBytes())));

        res.writePromise.get(0).complete(null);
        t.join();

        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());

        assertDoesNotThrow(() -> res.write(new byte[2]));
        assertDoesNotThrow(() -> res.write(Unpooled.copiedBuffer("abc".getBytes())));
        assertDoesNotThrow(() -> res.end(new byte[2]));

        verifyEndStatus(res, true);
    }

    @Test
    void testTryEndWithCrash() {
        final Res res = new Res();

        final AtomicReference<Throwable> onEnd = new AtomicReference<>();
        final AtomicReference<Throwable> ended = new AtomicReference<>();
        res.onEndFuture().addListener(f -> {
            if (f.isSuccess()) {
                onEnd.set(Signal.valueOf("ok"));
            } else {
                onEnd.set(f.cause());
            }
        });
        res.endFuture().addListener(f -> {
            if (f.isSuccess()) {
                ended.set(Signal.valueOf("ok"));
            } else {
                ended.set(f.cause());
            }
        });
        final Exception ex = new IllegalStateException("foo");
        assertTrue(res.tryEndWithCrash(ex));
        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());
        assertSame(ex, onEnd.get());
        assertSame(ex, ended.get());
    }

    @Test
    void testTryEndWithCrashButCasFailed() {
        final Res res = new Res();
        final AtomicReference<Throwable> onEnd = new AtomicReference<>();
        final AtomicReference<Throwable> ended = new AtomicReference<>();
        res.onEndFuture().addListener(f -> {
            if (f.isSuccess()) {
                onEnd.set(Signal.valueOf("ok"));
            } else {
                onEnd.set(f.cause());
            }
        });
        res.endFuture().addListener(f -> {
            if (f.isSuccess()) {
                ended.set(Signal.valueOf("ok"));
            } else {
                ended.set(f.cause());
            }
        });

        // already committed
        assertTrue(res.ensureCommitExclusively());

        assertFalse(res.tryEndWithCrash(new IllegalStateException("foo")));
        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());
        assertNull(onEnd.get());
        assertNull(ended.get());
    }

    @Test
    void testTryEnd() {
        final Res res = new Res();

        final AtomicReference<Throwable> onEnd = new AtomicReference<>();
        res.onEndFuture().addListener(f -> {
            if (f.isSuccess()) {
                onEnd.set(Signal.valueOf("ok"));
            } else {
                onEnd.set(f.cause());
            }
        });
        assertTrue(res.tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR, () -> Unpooled.EMPTY_BUFFER, false));
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), res.status());
        assertSame(Unpooled.EMPTY_BUFFER, res.doEnd);
        assertTrue(onEnd.get() instanceof Signal);
    }

    @Test
    void testTryEndButCasFailed() {
        final Res res = new Res();

        final AtomicReference<Throwable> onEnd = new AtomicReference<>();
        res.onEndFuture().addListener(f -> {
            if (f.isSuccess()) {
                onEnd.set(Signal.valueOf("ok"));
            } else {
                onEnd.set(f.cause());
            }
        });

        assertTrue(res.ensureCommitExclusively());
        final int code = res.status();
        assertFalse(res.tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR, () -> Unpooled.EMPTY_BUFFER, false));
        assertEquals(code, res.status());
        assertNull(res.doEnd);
        assertNull(onEnd.get());
    }

    @Test
    void testSendFileCheck() {
        final Res res = new Res();
        assertThrows(NullPointerException.class, () -> res.sendFile(null));
        final File absent = new File("");
        assertThrows(IllegalArgumentException.class, () -> res.sendFile(absent, -1L));
        assertThrows(IllegalArgumentException.class, () -> res.sendFile(absent, 0L, -1L));
        assertThrows(FileNotFoundException.class, () -> res.sendFile(absent, 0L, 1L));
    }

    @Test
    void testSendFileAfterWriting() throws IOException {
        final Res res = new Res();
        final File file = File.createTempFile("httpserver-", ".tmp");
        file.deleteOnExit();
        assertTrue(res.write(new byte[1]).isSuccess());
        try {
            assertThrows(IllegalStateException.class, () -> res.sendFile(file, 0L, 1L));
        } finally {
            file.delete();
        }

        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());
    }

    @Test
    void testSendFileAfterEnding() throws IOException {
        final Res res = new Res();
        final File file = File.createTempFile("httpserver-", ".tmp");
        file.deleteOnExit();
        assertTrue(res.end(new byte[1]).isSuccess());
        try {
            assertThrows(IllegalStateException.class, () -> res.sendFile(file, 0L, 1L));
        } finally {
            file.delete();
        }

        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());
    }

    @Test
    void testSendFileWhenWriting() throws IOException {
        final File file = File.createTempFile("httpserver-", ".tmp");
        file.deleteOnExit();
        final DelayedRes res = new DelayedRes();

        res.block();
        final Thread t = new Thread(() -> {
            res.write(new byte[2]);
        }, "base-response-test");
        t.start();

        // wait for writing
        while (res.writePromise.isEmpty()) {
        }

        res.unblock();

        try {
            assertThrows(IllegalStateException.class, () -> res.sendFile(file, 0L, 1L));
        } finally {
            file.delete();
        }

        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());
    }

    @Test
    void testSendRedirect() {
        final Res res = new Res();
        res.sendRedirect("foo");

        assertTrue(res.headers().contains(HttpHeaderNames.LOCATION, "foo"));
        assertEquals(HttpResponseStatus.FOUND.code(), res.status());
    }

    @Test
    void testToString() {
        final Res res = new Res();
        assertEquals("Response-[GET /foo 200]", res.toString());
        res.write(new byte[2]);
        assertEquals("Response~[GET /foo 200]", res.toString());
        res.end();
        assertEquals("Response![GET /foo 200]", res.toString());
    }

    private static class Res extends BaseResponse<BaseRequestHandleTest.Req> {

        private ByteBuf doEnd;
        private ByteBuf doEnd1;
        private ByteBuf doWrite;
        private ByteBuf doWrite1;
        private final HttpHeaders headers = new Http1HeadersImpl();
        private final HttpHeaders trailers = new Http1HeadersImpl();
        private final AtomicBoolean closure = new AtomicBoolean();

        Res() {
            super(BaseRequestHandleTest.plainReq());
        }

        @Override
        void doEnd(ByteBuf data, boolean writeHead, boolean forceClose) {
            doEnd = data;
            endPromise.setSuccess();
        }

        @Override
        void doEnd(byte[] data, int offset, int length, boolean writeHead) {
            doEnd1 = Unpooled.copiedBuffer(data, offset, length);
            endPromise.setSuccess();
        }

        @Override
        void doSendFile(File file, long offset, long length) {
            endPromise.setSuccess();
        }

        @Override
        ChannelFutureListener closure() {
            return f -> closure.set(true);
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public HttpHeaders trailers() {
            return trailers;
        }

        @Override
        Future<Void> doWrite(ByteBuf data, boolean writeHead) {
            doWrite = data;
            return ctx().newSucceededFuture();
        }

        @Override
        Future<Void> doWrite(byte[] data, int offset, int length, boolean writeHead) {
            doWrite1 = Unpooled.copiedBuffer(data, offset, length);
            return ctx().newSucceededFuture();
        }
    }

    private static final class DelayedRes extends Res {

        private volatile boolean block;
        private final List<CompletableFuture<Void>> writePromise = new CopyOnWriteArrayList<>();

        @Override
        Future<Void> doWrite(ByteBuf data, boolean writeHead) {
            doBlock();
            return super.doWrite(data, writeHead);
        }

        @Override
        Future<Void> doWrite(byte[] data, int offset, int length, boolean writeHead) {
            doBlock();
            return super.doWrite(data, offset, length, writeHead);
        }

        private void doBlock() {
            if (block) {
                CompletableFuture<Void> cf = new CompletableFuture<>();
                writePromise.add(cf);
                cf.join();
            }
        }

        void block() {
            block = true;
        }

        void unblock() {
            block = false;
        }
    }


}
