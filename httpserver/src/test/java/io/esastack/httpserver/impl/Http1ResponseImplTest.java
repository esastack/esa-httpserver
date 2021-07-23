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

import esa.commons.netty.http.Http1HeadersImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.Signal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Http1ResponseImplTest {

    @Test
    void testHeaders() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;

        assertTrue(res.headers().isEmpty());
        assertTrue(res.trailers().isEmpty());

        res.addCookie("a", "1");
        res.addCookie("b", "2");
        assertEquals("a=1", res.headers().get(HttpHeaderNames.SET_COOKIE));
        assertEquals(2, res.headers().getAll(HttpHeaderNames.SET_COOKIE).size());
        assertEquals("b=2", res.headers().getAll(HttpHeaderNames.SET_COOKIE).get(1));
    }

    @Test
    void testChunkWriteIfResponseHasBeenCommitted() throws InterruptedException {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        final AtomicBoolean committed = new AtomicBoolean();
        final Thread t = new Thread(() -> committed.set(res.ensureCommitExclusively()), "base-response-test");
        t.start();
        t.join();
        assertTrue(committed.get());
        assertThrows(IllegalStateException.class, () -> res.write(Unpooled.copiedBuffer("foo".getBytes())));
    }

    @Test
    void testChunkWriteIfResponseHasBeenEnded() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        assertTrue(res.ensureEndExclusively(true));
        assertThrows(IllegalStateException.class, () -> res.write(Unpooled.copiedBuffer("foo".getBytes())));
    }

    @Test
    void testChunkWriteNullData() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        assertTrue(res.write((byte[]) null).isSuccess());
        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http1ResponseImpl res1 = r1.res;
        assertTrue(res1.write((ByteBuf) null).isSuccess());
        assertTrue(res1.isCommitted());
        assertFalse(res1.isEnded());
    }

    @Test
    void testChunkWriteEmptyData() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        assertTrue(res.write(Unpooled.EMPTY_BUFFER).isSuccess());
        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http1ResponseImpl res1 = r1.res;
        assertTrue(res1.write(Utils.EMPTY_BYTES).isSuccess());
        assertTrue(res1.isCommitted());
        assertFalse(res1.isEnded());

        final ResTuple r2 = newResponse();
        final Http1ResponseImpl res2 = r2.res;
        final ByteBuf buf = Unpooled.buffer(0);
        assertTrue(res2.write(buf).isSuccess());
        assertTrue(res2.isCommitted());
        assertFalse(res2.isEnded());
        // should be release
        assertEquals(0, buf.refCnt());
    }

    @Test
    void testChunkWrite() {
        testChunkedWrite(true, true, true);
        testChunkedWrite(false, false, true);

        testChunkedWrite(true, true, false);
        testChunkedWrite(false, false, false);
    }

    private void testChunkedWrite(boolean keepalive, boolean writeTrailer, boolean bytes) {
        final ResTuple r = newResponse(HttpMethod.POST, keepalive);
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;
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

        if (writeTrailer) {
            res.trailers().add("a", "1");
            res.trailers().add("b", "2");
        }

        res.setStatus(500);

        if (bytes) {
            res.write("123".getBytes(StandardCharsets.UTF_8));
            assertTrue(res.isCommitted());
            res.write("456".getBytes(StandardCharsets.UTF_8));
            res.end("789".getBytes(StandardCharsets.UTF_8)).syncUninterruptibly();
        } else {
            res.write(Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
            assertTrue(res.isCommitted());
            res.write(Unpooled.copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
            res.end(Unpooled.copiedBuffer("789".getBytes(StandardCharsets.UTF_8)))
                    .syncUninterruptibly();
        }

        final DefaultHttpResponse head = channel.readOutbound();
        assertNotNull(head);
        assertEquals(HttpVersion.HTTP_1_1, head.protocolVersion());
        assertFalse(head.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertTrue(head.headers()
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertTrue(res.headers()
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));

        if (keepalive) {
            assertFalse(head.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
            assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        } else {
            assertTrue(head.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
            assertTrue(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        }

        assertEquals(res.status(), head.status().code());

        final DefaultHttpContent chunk1 = channel.readOutbound();
        assertNotNull(chunk1);
        assertEquals("123", chunk1.content().toString(StandardCharsets.UTF_8));

        final DefaultHttpContent chunk2 = channel.readOutbound();
        assertNotNull(chunk2);
        assertEquals("456", chunk2.content().toString(StandardCharsets.UTF_8));

        final AggregatedLastHttpContent last = channel.readOutbound();
        assertNotNull(last);
        assertEquals("789", last.content().toString(StandardCharsets.UTF_8));
        if (writeTrailer) {
            assertEquals(2, last.trailingHeaders().size());
            assertTrue(last.trailingHeaders().contains("a", "1", true));
            assertTrue(last.trailingHeaders().contains("b", "2", true));
        }

        assertTrue(res.isEnded());
        assertTrue(onEnd.get() instanceof Signal);
        assertTrue(ended.get() instanceof Signal);

        if (keepalive) {
            assertTrue(channel.isActive());
        } else {
            assertFalse(channel.isActive());
        }
    }

    @Test
    void testEndWithNullData() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        assertTrue(res.end((byte[]) null).isSuccess());
        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http1ResponseImpl res1 = r1.res;
        assertTrue(res1.end((ByteBuf) null).isSuccess());
        assertTrue(res1.isCommitted());
        assertTrue(res1.isEnded());
    }

    @Test
    void testEndWithEmptyData() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        assertTrue(res.end(Unpooled.EMPTY_BUFFER).isSuccess());
        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http1ResponseImpl res1 = r1.res;
        assertTrue(res1.end(Utils.EMPTY_BYTES).isSuccess());
        assertTrue(res1.isCommitted());
        assertTrue(res1.isEnded());

        final ResTuple r2 = newResponse();
        final Http1ResponseImpl res2 = r2.res;
        final ByteBuf buf = Unpooled.buffer(0);
        assertTrue(res2.end(buf).isSuccess());
        assertTrue(res2.isCommitted());
        assertTrue(res2.isEnded());
    }

    @Test
    void testEnd() {
        testEnd(true, true, true);
        testEnd(false, false, true);

        testEnd(true, true, false);
        testEnd(false, false, false);
    }

    private void testEnd(boolean keepalive, boolean writeTrailer, boolean bytes) {
        final ResTuple r = newResponse(HttpMethod.POST, keepalive);
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;
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

        if (writeTrailer) {
            res.trailers().add("a", "1");
            res.trailers().add("b", "2");
        }

        res.setStatus(500);

        if (bytes) {
            res.end("123".getBytes(StandardCharsets.UTF_8)).syncUninterruptibly();
        } else {
            res.end(Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8))).syncUninterruptibly();
        }

        final DefaultFullHttpResponse response = channel.readOutbound();
        assertNotNull(response);
        assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
        assertTrue(response.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "3", true));
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "3", true));
        assertFalse(response.headers()
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertFalse(res.headers()
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        if (keepalive) {
            assertFalse(response.headers()
                    .contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
            assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        } else {
            assertTrue(response.headers()
                    .contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
            assertTrue(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        }
        assertEquals(res.status(), response.status().code());
        assertEquals("123", response.content().toString(StandardCharsets.UTF_8));

        if (writeTrailer) {
            assertEquals(2, response.trailingHeaders().size());
            assertTrue(response.trailingHeaders().contains("a", "1", true));
            assertTrue(response.trailingHeaders().contains("b", "2", true));
        }

        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());
        assertTrue(onEnd.get() instanceof Signal);
        assertTrue(ended.get() instanceof Signal);
        if (keepalive) {
            assertTrue(channel.isActive());
        } else {
            assertFalse(channel.isActive());
        }
    }

    @Test
    void testConnectionClosedIfNotKeepAlive() {
        final ResTuple r = newResponse(HttpMethod.POST, false);
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;

        res.end().syncUninterruptibly();
        final DefaultFullHttpResponse response = channel.readOutbound();
        assertNotNull(response);

        assertTrue(response.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertTrue(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertFalse(channel.isActive());
    }

    @Test
    void testEndHeadersIf304() {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;

        res.setStatus(304);
        res.end().syncUninterruptibly();
        final DefaultFullHttpResponse response = channel.readOutbound();
        assertNotNull(response);

        assertFalse(response.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING));
        assertFalse(res.headers().contains(HttpHeaderNames.TRANSFER_ENCODING));
    }

    @Test
    void testEndHeadersIfHeadRequest() {
        final ResTuple r = newResponse(HttpMethod.HEAD, true);
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;

        res.end().syncUninterruptibly();
        final DefaultFullHttpResponse response = channel.readOutbound();
        assertNotNull(response);

        assertFalse(response.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING));
        assertFalse(res.headers().contains(HttpHeaderNames.TRANSFER_ENCODING));
    }

    @Test
    void testSendFileByZeroCopy() throws IOException {
        testSendFileByZeroCopy(true);
        testSendFileByZeroCopy(false);
    }

    private void testSendFileByZeroCopy(boolean writeTrailer) throws IOException {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;

        final File file = File.createTempFile("httpserver-", ".tmp");
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        final byte[] data = new byte[1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        out.write(data);
        out.close();

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

        if (writeTrailer) {
            res.trailers().add("a", "1");
            res.trailers().add("b", "2");
        }

        DefaultFileRegion fileRegion = null;
        try {
            res.sendFile(file).syncUninterruptibly();

            final DefaultHttpResponse head = channel.readOutbound();
            assertNotNull(head);
            assertTrue(head.headers().contains(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(head.headers()
                    .contains(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(data.length), true));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(data.length), true));

            fileRegion = channel.readOutbound();
            assertNotNull(fileRegion);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            assertEquals(data.length, fileRegion.transferTo(Channels.newChannel(outputStream), 0));
            assertArrayEquals(data, outputStream.toByteArray());

            final LastHttpContent last = channel.readOutbound();
            if (writeTrailer) {
                assertTrue(last instanceof AggregatedLastHttpContent);
                final AggregatedLastHttpContent l = (AggregatedLastHttpContent) last;
                assertEquals(2, l.trailingHeaders().size());
                assertTrue(l.trailingHeaders().contains("a", "1", true));
                assertTrue(l.trailingHeaders().contains("b", "2", true));
            } else {
                assertSame(LastHttpContent.EMPTY_LAST_CONTENT, last);
            }

            assertEquals(0, fileRegion.position());
            assertEquals(data.length, fileRegion.count());
            assertTrue(onEnd.get() instanceof Signal);
            assertTrue(ended.get() instanceof Signal);
        } finally {
            if (fileRegion != null) {
                fileRegion.release();
            }
            file.delete();
        }
    }

    @Test
    void testSendFileByChunk() throws Exception {
        testSendFileByChunk(true);
        testSendFileByChunk(false);
    }

    private static void testSendFileByChunk(boolean writeTrailer) throws Exception {
        final ResTuple r = newResponse();
        final Http1ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;

        channel.pipeline().addLast(new HttpContentCompressor());

        final File file = File.createTempFile("httpserver-", ".tmp");
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        final byte[] data = new byte[1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        out.write(data);
        out.close();

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

        if (writeTrailer) {
            res.trailers().add("a", "1");
            res.trailers().add("b", "2");
        }

        HttpChunkedInput chunkedInput = null;
        try {
            res.sendFile(file).syncUninterruptibly();

            final DefaultHttpResponse head = channel.readOutbound();
            assertNotNull(head);
            assertTrue(head.headers().contains(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(head.headers()
                    .contains(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(data.length), true));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(data.length), true));


            chunkedInput = channel.readOutbound();
            assertNotNull(chunkedInput);

            assertEquals(data.length, chunkedInput.length());

            final CompositeByteBuf chunked = Unpooled.compositeBuffer();
            do {
                HttpContent c = chunkedInput.readChunk(Unpooled.EMPTY_BUFFER.alloc());
                if (c != null && c.content().isReadable()) {
                    chunked.addComponent(true, c.content());
                }

                if (chunkedInput.isEndOfInput() && writeTrailer) {
                    assertTrue(c instanceof LastHttpContent);
                    assertEquals(2, ((LastHttpContent) c).trailingHeaders().size());
                    assertTrue(((LastHttpContent) c).trailingHeaders().contains("a", "1", true));
                    assertTrue(((LastHttpContent) c).trailingHeaders().contains("b", "2", true));
                }
            } while (!chunkedInput.isEndOfInput());

            assertArrayEquals(data, ByteBufUtil.getBytes(chunked));

            chunkedInput.close();
            assertTrue(onEnd.get() instanceof Signal);
            assertTrue(ended.get() instanceof Signal);
        } finally {
            try {
                if (chunkedInput != null) {
                    chunkedInput.close();
                }
            } finally {
                file.delete();
            }
        }
    }

    private static ResTuple newResponse() {
        return newResponse(HttpMethod.POST, true);
    }

    private static ResTuple newResponse(HttpMethod method, boolean keepalive) {

        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final ChannelHandlerContext ctx = channel.pipeline().firstContext();

        final HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, "/foo", new Http1HeadersImpl());
        final Http1RequestHandleImpl handle =
                new Http1RequestHandleImpl(Helper.serverRuntime(),
                        ctx,
                        request,
                        keepalive);
        return new ResTuple(handle.response(), channel);
    }

    private static class ResTuple {
        private final Http1ResponseImpl res;
        private final EmbeddedChannel channel;


        private ResTuple(Http1ResponseImpl res,
                         EmbeddedChannel channel) {
            this.res = res;
            this.channel = channel;
        }
    }
}
