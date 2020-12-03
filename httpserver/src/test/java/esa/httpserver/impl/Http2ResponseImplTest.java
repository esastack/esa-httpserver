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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.Signal;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2ResponseImplTest {

    @Test
    void testHeaders() {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;

        assertTrue(res.headers().isEmpty());
        assertTrue(res.trailers().isEmpty());

        res.addCookie("a", "1");
        res.addCookie("b", "2");
        assertEquals("a=1", res.headers().get(HttpHeaderNames.SET_COOKIE));
        assertEquals(2, res.headers().getAll(HttpHeaderNames.SET_COOKIE).size());
        assertEquals("b=2", res.headers().getAll(HttpHeaderNames.SET_COOKIE).get(1));
    }

    @Test
    void testWritable() {
        final ResTuple r = newResponse(HttpMethod.POST, true);
        assertTrue(r.res.isWritable());
        final ResTuple r1 = newResponse(HttpMethod.POST, false);
        assertFalse(r1.res.isWritable());
    }

    @Test
    void testChunkWriteIfResponseHasBeenCommitted() throws InterruptedException {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
        final AtomicBoolean committed = new AtomicBoolean();
        final Thread t = new Thread(() -> committed.set(res.ensureCommittedExclusively()), "base-response-test");
        t.start();
        t.join();
        assertTrue(committed.get());
        assertThrows(IllegalStateException.class, () -> res.write(Unpooled.copiedBuffer("foo".getBytes())));
    }

    @Test
    void testChunkWriteIfResponseHasBeenEnded() {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
        assertTrue(res.ensureEndedExclusively());
        assertThrows(IllegalStateException.class, () -> res.write(Unpooled.copiedBuffer("foo".getBytes())));
    }

    @Test
    void testChunkWriteNullData() {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
        assertTrue(res.write((byte[]) null).isSuccess());
        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http2ResponseImpl res1 = r1.res;
        assertTrue(res1.write((ByteBuf) null).isSuccess());
        assertTrue(res1.isCommitted());
        assertFalse(res1.isEnded());
    }

    @Test
    void testChunkWriteEmptyData() {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
        assertTrue(res.write(Unpooled.EMPTY_BUFFER).isSuccess());
        assertTrue(res.isCommitted());
        assertFalse(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http2ResponseImpl res1 = r1.res;
        assertTrue(res1.write(Utils.EMPTY_BYTES).isSuccess());
        assertTrue(res1.isCommitted());
        assertFalse(res1.isEnded());

        final ResTuple r2 = newResponse();
        final Http2ResponseImpl res2 = r2.res;
        final ByteBuf buf = Unpooled.buffer(0);
        assertTrue(res2.write(buf).isSuccess());
        assertTrue(res2.isCommitted());
        assertFalse(res2.isEnded());
        assertEquals(0, buf.refCnt());
    }

    @Test
    void testChunkWrite() {
        testChunkedWrite(true, true);
        testChunkedWrite(false, false);

        testChunkedWrite(true, false);
        testChunkedWrite(false, false);
    }

    private void testChunkedWrite(boolean writeTrailer, boolean bytes) {
        final ResTuple r = newResponse(HttpMethod.POST);
        final Http2ResponseImpl res = r.res;
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
            res.end("789".getBytes(StandardCharsets.UTF_8));
        } else {
            res.write(Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
            assertTrue(res.isCommitted());
            res.write(Unpooled.copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
            res.end(Unpooled.copiedBuffer("789".getBytes(StandardCharsets.UTF_8)));
        }

        final Helper.HeaderFrame head = channel.readOutbound();
        assertNotNull(head);

        assertFalse(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(head.headers
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertFalse(res.headers()
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertFalse(head.headers.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));

        assertEquals(String.valueOf(res.status()), head.headers.status());
        assertFalse(head.endStream);
        assertEquals(0, head.padding);

        final Helper.DataFrame chunk1 = channel.readOutbound();
        assertNotNull(chunk1);
        assertEquals("123", chunk1.data.toString(StandardCharsets.UTF_8));
        assertFalse(chunk1.endStream);
        assertEquals(0, chunk1.padding);

        final Helper.DataFrame chunk2 = channel.readOutbound();
        assertNotNull(chunk2);
        assertEquals("456", chunk2.data.toString(StandardCharsets.UTF_8));
        assertEquals(0, chunk2.padding);
        assertFalse(chunk2.endStream);

        final Helper.DataFrame chunk3 = channel.readOutbound();
        assertNotNull(chunk3);
        assertEquals("789", chunk3.data.toString(StandardCharsets.UTF_8));
        assertEquals(0, chunk3.padding);


        if (writeTrailer) {
            assertFalse(chunk3.endStream);

            final Helper.HeaderFrame trailers = channel.readOutbound();
            assertNotNull(trailers);

            assertEquals(2, trailers.headers.size());
            assertTrue(trailers.headers.contains("a", "1", true));
            assertTrue(trailers.headers.contains("b", "2", true));
            assertTrue(trailers.endStream);
            assertEquals(0, trailers.padding);
        } else {
            assertTrue(chunk3.endStream);
        }

        assertTrue(res.isEnded());
        assertTrue(onEnd.get() instanceof Signal);
        assertTrue(ended.get() instanceof Signal);
        assertTrue(channel.isActive());
    }

    @Test
    void testEndWithNullData() {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
        assertTrue(res.end((byte[]) null).isSuccess());
        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http2ResponseImpl res1 = r1.res;
        assertTrue(res1.end((ByteBuf) null).isSuccess());
        assertTrue(res1.isCommitted());
        assertTrue(res1.isEnded());
    }

    @Test
    void testEndWithEmptyData() {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
        assertTrue(res.end(Unpooled.EMPTY_BUFFER).isSuccess());
        assertTrue(res.isCommitted());
        assertTrue(res.isEnded());

        final ResTuple r1 = newResponse();
        final Http2ResponseImpl res1 = r1.res;
        assertTrue(res1.end(Utils.EMPTY_BYTES).isSuccess());
        assertTrue(res1.isCommitted());
        assertTrue(res1.isEnded());

        final ResTuple r2 = newResponse();
        final Http2ResponseImpl res2 = r2.res;
        final ByteBuf buf = Unpooled.buffer(0);
        assertTrue(res2.end(buf).isSuccess());
        assertTrue(res2.isCommitted());
        assertTrue(res2.isEnded());
        assertEquals(0, buf.refCnt());
    }

    @Test
    void testEnd() {
        testEnd(true, true);
        testEnd(false, true);

        testEnd(true, false);
        testEnd(false, false);
    }

    private void testEnd(boolean writeTrailer, boolean bytes) {
        final ResTuple r = newResponse(HttpMethod.POST);
        final Http2ResponseImpl res = r.res;
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
            res.end("123".getBytes(StandardCharsets.UTF_8));
        } else {
            res.end(Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        }

        final Helper.HeaderFrame head = channel.readOutbound();
        assertNotNull(head);

        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "3"));
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "3"));
        assertFalse(head.headers
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertFalse(res.headers()
                .contains(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertFalse(head.headers.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));

        assertEquals(String.valueOf(res.status()), head.headers.status());
        assertFalse(head.endStream);
        assertEquals(0, head.padding);

        final Helper.DataFrame body = channel.readOutbound();
        assertNotNull(body);
        assertEquals("123", body.data.toString(StandardCharsets.UTF_8));
        assertEquals(0, body.padding);


        if (writeTrailer) {
            assertFalse(body.endStream);

            final Helper.HeaderFrame trailers = channel.readOutbound();
            assertNotNull(trailers);

            assertEquals(2, trailers.headers.size());
            assertTrue(trailers.headers.contains("a", "1", true));
            assertTrue(trailers.headers.contains("b", "2", true));
            assertTrue(trailers.endStream);
            assertEquals(0, trailers.padding);
        } else {
            assertTrue(body.endStream);
        }

        assertTrue(res.isEnded());
        assertTrue(onEnd.get() instanceof Signal);
        assertTrue(ended.get() instanceof Signal);
        assertTrue(channel.isActive());
    }

    @Test
    void testEndHeaders() {
        testEndHeaders0(HttpMethod.POST, HttpResponseStatus.NOT_MODIFIED, null);
        testEndHeaders0(HttpMethod.POST, HttpResponseStatus.RESET_CONTENT, "0");
        testEndHeaders0(HttpMethod.POST, HttpResponseStatus.SWITCHING_PROTOCOLS, null);
        testEndHeaders0(HttpMethod.HEAD, HttpResponseStatus.OK, null);
    }

    private static void testEndHeaders0(HttpMethod method, HttpResponseStatus status, String contentLength) {
        final ResTuple r = newResponse(method);
        final Http2ResponseImpl res = r.res;
        final EmbeddedChannel channel = r.channel;

        res.setStatus(status.code());
        res.end().syncUninterruptibly();
        final Helper.HeaderFrame head = channel.readOutbound();
        assertNotNull(head);

        if (contentLength != null) {
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, contentLength));
            assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH), contentLength);
        } else {
            assertFalse(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH));
            assertFalse(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH));
        }
        assertFalse(head.headers.contains(HttpHeaderNames.TRANSFER_ENCODING));
        assertFalse(res.headers().contains(HttpHeaderNames.TRANSFER_ENCODING));
    }

    @Test
    void testSendFile() throws Exception {
        testSendFile(true);
        testSendFile(false);
    }

    private static void testSendFile(boolean writeTrailer) throws Exception {
        final ResTuple r = newResponse();
        final Http2ResponseImpl res = r.res;
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

        Http2ChunkedInput chunkedInput = null;
        try {
            res.sendFile(file).syncUninterruptibly();

            final Helper.HeaderFrame head = channel.readOutbound();
            assertNotNull(head);
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_TYPE));
            assertTrue(head.headers
                    .contains(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(data.length), true));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(data.length), true));
            assertFalse(head.endStream);
            assertEquals(0, head.padding);

            chunkedInput = channel.readOutbound();
            assertNotNull(chunkedInput);
            assertEquals(data.length, chunkedInput.length());

            final CompositeByteBuf chunked = Unpooled.compositeBuffer();
            do {
                Http2ChunkedInput.Content c = chunkedInput.readChunk(Unpooled.EMPTY_BUFFER.alloc());
                if (c != null && c.content().isReadable()) {
                    chunked.addComponent(true, c.content());
                }

                if (chunkedInput.isEndOfInput() && writeTrailer) {
                    assertTrue(c instanceof Http2ChunkedInput.LastContent);
                    assertEquals(2, ((Http2ChunkedInput.LastContent) c).trailers.size());
                    assertTrue(((Http2ChunkedInput.LastContent) c).trailers
                            .contains("a", "1", true));
                    assertTrue(((Http2ChunkedInput.LastContent) c)
                            .trailers.contains("b", "2", true));
                }
            } while (!chunkedInput.isEndOfInput());
            assertArrayEquals(data, ByteBufUtil.getBytes(chunked));

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
        return newResponse(HttpMethod.POST);
    }

    private static ResTuple newResponse(HttpMethod method) {
        return newResponse(HttpMethod.POST, true);
    }

    private static ResTuple newResponse(HttpMethod method, boolean isWritable) {
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final ChannelHandlerContext ctx = channel.pipeline().firstContext();

        final Http2ConnectionEncoder encoder = mock(Http2ConnectionEncoder.class);
        Helper.mockHeaderAndDataFrameWrite(encoder);
        final Http2RemoteFlowController flowController = mock(Http2RemoteFlowController.class);
        when(encoder.flowController()).thenReturn(flowController);
        final Http2Headers headers = new DefaultHttp2Headers();

        headers.path("/foo")
                .method(method.name());
        final Http2Stream stream = mock(Http2Stream.class);
        when(flowController.isWritable(same(stream))).thenReturn(isWritable);
        final Http2RequestHandleImpl handle =
                Http2RequestHandleImpl.from(Helper.serverRuntime(),
                        ctx,
                        encoder,
                        headers,
                        stream,
                        0,
                        Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                        false);
        return new ResTuple(handle.response(), channel);
    }

    private static class ResTuple {
        private final Http2ResponseImpl res;
        private final EmbeddedChannel channel;


        private ResTuple(Http2ResponseImpl res, EmbeddedChannel channel) {
            this.res = res;
            this.channel = channel;
        }
    }
}
