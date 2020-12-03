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

import esa.commons.http.HttpHeaders;
import esa.commons.netty.http.CookieImpl;
import esa.commons.netty.http.Http1HeadersImpl;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static org.junit.jupiter.api.Assertions.*;

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
        res.addCookie(new CookieImpl("b", "2"));
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
    void testAlreadyEndedBeforeWriting() {
        final Res res = new Res();
        assertTrue(res.ensureEndedExclusively());
        assertTrue(res.isEnded());
        assertThrows(IllegalStateException.class, () -> res.write(new byte[2]));
    }

    @Test
    void testEnsureCommittedExclusively() throws InterruptedException {
        final Res res = new Res();
        final AtomicBoolean committed = new AtomicBoolean();
        final Thread t = new Thread(() -> {
            committed.set(res.ensureCommittedExclusively());
        }, "base-response-test");
        t.start();
        t.join();
        assertTrue(committed.get());
        assertThrows(IllegalStateException.class, res::ensureCommittedExclusively);
    }

    @Test
    void testEnsureEndedExclusively() {
        final Res res = new Res();
        assertTrue(res.ensureEndedExclusively());
        assertThrows(IllegalStateException.class, res::ensureEndedExclusively);
    }

    @Test
    void testEnsureEndedExclusivelyIfAlreadyCommittedByCurrentThread() {
        final Res res = new Res();
        assertTrue(res.ensureCommittedExclusively());
        assertFalse(res.ensureEndedExclusively());
        assertThrows(IllegalStateException.class, res::ensureEndedExclusively);
    }

    @Test
    void testEnsureEndedExclusivelyIfAlreadyCommittedByAnotherThread() throws InterruptedException {
        final Res res = new Res();

        final AtomicBoolean committed = new AtomicBoolean();
        final Thread t = new Thread(() -> committed.set(res.ensureCommittedExclusively()), "base-response-test");
        t.start();
        t.join();
        assertTrue(committed.get());
        assertThrows(IllegalStateException.class, res::ensureEndedExclusively);
    }

    @Test
    void testEnsureEndedExclusivelyIfAlreadyEndedByAnotherThread() throws InterruptedException {
        final Res res = new Res();

        final AtomicBoolean ended = new AtomicBoolean();
        final Thread t = new Thread(() -> ended.set(res.ensureEndedExclusively()), "base-response-test");
        t.start();
        t.join();
        assertTrue(ended.get());
        assertThrows(IllegalStateException.class, res::ensureEndedExclusively);
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
        assertTrue(res.ensureCommittedExclusively());

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

        assertTrue(res.ensureCommittedExclusively());
        final int code = res.status();
        assertFalse(res.tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR, () -> Unpooled.EMPTY_BUFFER, false));
        assertEquals(code, res.status());
        assertNull(res.doEnd);
        assertNull(onEnd.get());
    }

    @Test
    void testSendFileCheck() throws IOException {
        final Res res = new Res();
        assertThrows(NullPointerException.class, () -> res.sendFile(null));
        final File absent = new File("");
        assertThrows(IllegalArgumentException.class, () -> res.sendFile(absent, -1L));
        assertThrows(IllegalArgumentException.class, () -> res.sendFile(absent, 0L, -1L));
        assertThrows(FileNotFoundException.class, () -> res.sendFile(absent, 0L, 1L));

        final File file = File.createTempFile("httpserver-", ".tmp");
        file.deleteOnExit();
        assertTrue(res.ensureCommittedExclusively());
        try {
            assertThrows(IllegalStateException.class, () -> res.sendFile(file, 0L, 1L));
        } finally {
            file.delete();
        }
    }

    @Test
    void testSendRedirect() {
        final Res res = new Res();
        res.sendRedirect("foo");

        assertTrue(res.headers().contains(HttpHeaderNames.LOCATION, "foo"));
        assertEquals(HttpResponseStatus.FOUND.code(), res.status());
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

}
