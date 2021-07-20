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
import esa.commons.http.HttpMethod;
import esa.commons.http.HttpVersion;
import esa.commons.netty.http.Http1HeadersImpl;
import esa.httpserver.core.MultipartFile;
import esa.httpserver.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseRequestHandleTest {

    @Test
    void testImplementationOfRequest() {
        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        final EmbeddedChannel ch = new EmbeddedChannel();
        ch.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTP);
        when(ctx.channel()).thenReturn(ch);
        final Req req = new Req(ctx, "/foo?a=1&b=2&b=3");
        req.headers.add(HttpHeaderNames.COOKIE, "c=1; d=2");
        assertEquals(HttpVersion.HTTP_1_1, req.version());
        assertEquals(Constants.SCHEMA_HTTP, req.scheme());
        assertEquals(HttpMethod.GET, req.method());
        assertEquals(HttpMethod.GET.name(), req.rawMethod());
        assertEquals("/foo?a=1&b=2&b=3", req.uri());
        assertEquals("/foo", req.path());
        assertEquals("a=1&b=2&b=3", req.query());

        assertEquals("1", req.getParam("a"));
        assertEquals("2", req.getParam("b"));
        final List<String> params = req.getParams("b");
        assertEquals(2, params.size());
        assertEquals("2", params.get(0));
        assertEquals("3", params.get(1));

        final Map<String, List<String>> p = req.paramMap();
        assertEquals(2, params.size());

        assertEquals(2, req.cookies().size());
        assertEquals("1", req.getCookie("c").value());
        assertEquals("2", req.getCookie("d").value());
        assertSame(ch.remoteAddress(), req.remoteAddress());
        assertSame(ch.remoteAddress(), req.tcpSourceAddress());
        assertSame(ch.localAddress(), req.localAddress());
        final ByteBufAllocator alloc = new UnpooledByteBufAllocator(false);
        when(ctx.alloc()).thenReturn(alloc);
        assertSame(alloc, req.alloc());
    }

    @Test
    void testPreferUsingHaProxiedRemoteAddress() {
        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        final EmbeddedChannel ch = new EmbeddedChannel();
        final SocketAddress address = new LocalAddress("foo");
        ch.attr(Utils.SOURCE_ADDRESS).set(address);
        when(ctx.channel()).thenReturn(ch);
        final Req req = new Req(ctx, "/foo");
        assertSame(address, req.remoteAddress());
    }

    @Test
    void testReturnEmptyIfCookieAbsent() {
        assertTrue(plainReq().cookies().isEmpty());
    }

    @Test
    void testSetAndCallOnDataHandler() {
        final Req req = plainReq();
        final List<ByteBuf> bufs = new LinkedList<>();
        assertSame(req, req.onData(bufs::add));
        final ByteBuf buf0 = Unpooled.copiedBuffer(new byte[0]);
        final ByteBuf buf1 = Unpooled.copiedBuffer(new byte[]{1, 2});
        final ByteBuf buf2 = Unpooled.copiedBuffer(new byte[]{3, 4});
        final ByteBuf buf3 = Unpooled.copiedBuffer(new byte[]{5, 6});
        req.handleContent(buf0);
        req.handleContent(buf1);
        req.handleContent(buf2);
        req.handleContent(buf3);
        assertEquals(3, bufs.size());
        assertSame(buf1, bufs.get(0));
        assertSame(buf2, bufs.get(1));
        assertSame(buf3, bufs.get(2));
        assertEquals(1, buf0.refCnt());
        assertEquals(1, buf1.refCnt());
        assertEquals(1, buf2.refCnt());
        assertEquals(1, buf3.refCnt());
    }

    @Test
    void testSetAndCallOnTrailerHandler() {
        final Req req = plainReq();
        final List<HttpHeaders> headers = new LinkedList<>();
        assertSame(req, req.onTrailer(headers::add));
        final HttpHeaders h1 = mock(HttpHeaders.class);
        final HttpHeaders h2 = mock(HttpHeaders.class);
        final HttpHeaders h3 = mock(HttpHeaders.class);
        req.handleTrailer(h1);
        req.handleTrailer(h2);
        req.handleTrailer(h3);
        assertEquals(3, headers.size());
        assertSame(h1, headers.get(0));
        assertSame(h2, headers.get(1));
        assertSame(h3, headers.get(2));
    }

    @Test
    void testSetAndCallOnEndHandler() {
        final Req req = plainReq();
        when(req.response.isCommitted()).thenReturn(true);
        when(req.response.isEnded()).thenReturn(true);
        final AtomicBoolean end = new AtomicBoolean();
        assertSame(req, req.onEnd(p -> {
            end.set(true);
            return p.setSuccess(null);
        }));
        req.handleEnd();
        assertTrue(end.get());
    }

    @Test
    void testResponseShouldBeEndedAfterEnd() {
        final Req req = plainReq();
        when(req.response.isCommitted()).thenReturn(false);
        when(req.response.isEnded()).thenReturn(false);
        when(req.response.tryEnd(any(), any(), anyBoolean())).thenReturn(true);
        req.handleEnd();
        verify(req.response).tryEnd(eq(HttpResponseStatus.valueOf(req.response.status())), any(), eq(false));
    }

    @Test
    void testResponseShouldBeEndedAfterEndByError() {
        final Req req = plainReq();
        when(req.response.isCommitted()).thenReturn(false);
        when(req.response.isEnded()).thenReturn(false);
        when(req.response.tryEnd(any(), any(), anyBoolean())).thenReturn(true);
        req.onEnd(p -> p.setFailure(new IllegalStateException("foo")));
        req.handleEnd();
        verify(req.response).tryEnd(eq(HttpResponseStatus.INTERNAL_SERVER_ERROR),
                argThat(msg -> {
                    final Object body = msg.get();
                    return "foo".equals(((ByteBuf) body).retain().toString(StandardCharsets.UTF_8));
                }), eq(false));
    }

    @Test
    void testResponseShouldBeEndedAfterEndByErrorWithEmptyErrorMessage() {
        final Req req = plainReq();
        when(req.response.isCommitted()).thenReturn(false);
        when(req.response.isEnded()).thenReturn(false);
        when(req.response.tryEnd(any(), any(), anyBoolean())).thenReturn(true);
        req.onEnd(p -> p.setFailure(new IllegalStateException()));
        req.handleEnd();
        verify(req.response).tryEnd(eq(HttpResponseStatus.INTERNAL_SERVER_ERROR),
                argThat(msg -> Unpooled.EMPTY_BUFFER == msg.get()), eq(false));
    }

    @Test
    void testSetAndCallOnErrorHandler() {
        final Req req = plainReq();
        final AtomicReference<Throwable> err = new AtomicReference<>();

        assertSame(req, req.onError(err::set));
        final Exception e = new IllegalStateException();
        req.handleError(e);
        assertSame(e, err.get());
    }

    @Test
    void testMultipartExpectWithEndedStatus() {
        final Req req = plainReq();
        req.isEnded = true;
        assertThrows(IllegalStateException.class, () -> req.multipart(true));
    }

    @Test
    void testMultipartExpectWithNoneMultiPartRequest() {
        final Req req = plainReq();
        req.request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.GET, "/foo");
        assertSame(MultipartHandle.EMPTY, req.multipart());
        assertSame(req, req.multipart(true));
        assertSame(MultipartHandle.EMPTY, req.multipart());
    }

    @Test
    void testMultipartExpect() throws IOException {
        final Req req = plainReq();
        req.request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.GET, "/foo");
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        req.request.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.request.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        assertSame(MultipartHandle.EMPTY, req.multipart());
        assertSame(req, req.multipart(true));
        assertNotSame(MultipartHandle.EMPTY, req.multipart());
        assertTrue(req.multipart().attributes().isEmpty());
        assertTrue(req.multipart().uploadFiles().isEmpty());

        final String body0 = "";

        final String body1 =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                        "Content-Type: image/gif\r\n" +
                        "\r\n" +
                        "foo" + "\r\n" +
                        "--" + boundary;
        final String body2 = "\r\n" +
                "Content-Disposition: form-data; name=\"foo\"\r\n" +
                "\r\n" +
                "bar\r\n" +
                "--" + boundary + "--\r\n";

        final ByteBuf buf0 = Unpooled.copiedBuffer(body0, StandardCharsets.UTF_8);
        final ByteBuf buf1 = Unpooled.copiedBuffer(body1, StandardCharsets.UTF_8);
        final ByteBuf buf2 = Unpooled.copiedBuffer(body2, StandardCharsets.UTF_8);

        req.handleContent(buf0);
        req.handleContent(buf1);
        req.handleContent(buf2);

        final CompletableFuture<Void> cf = new CompletableFuture<>();
        req.onEnd(p -> {
            cf.thenRun(() -> p.setSuccess(null));
            return p;
        });
        req.handleEnd();

        assertEquals(1, req.multipart().uploadFiles().size());
        final MultipartFile upload = req.multipart().uploadFiles().get(0);
        assertEquals("file", upload.name());
        assertEquals("tmp-0.txt", upload.fileName());
        assertEquals("foo", upload.string(StandardCharsets.UTF_8));

        assertEquals(1, req.multipart().attributes().size());
        assertEquals("bar", req.multipart().attributes().getFirst("foo"));

        cf.complete(null);
        assertNull(upload.getByteBuf());
        assertEquals(1, buf0.refCnt());
        assertEquals(1, buf1.refCnt());
        assertEquals(1, buf2.refCnt());
    }

    @Test
    void testMultipartExpectCleared() {
        final Req req = plainReq();
        req.request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST, "/foo");
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        req.request.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        req.request.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        assertSame(MultipartHandle.EMPTY, req.multipart());
        assertSame(req, req.multipart(true));
        assertNotSame(MultipartHandle.EMPTY, req.multipart());

        final String body0 = "";
        final String body1 =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                        "Content-Type: image/gif\r\n" +
                        "\r\n" +
                        "foo" + "\r\n" +
                        "--" + boundary;
        final ByteBuf buf0 = Unpooled.copiedBuffer(body0, StandardCharsets.UTF_8);
        final ByteBuf buf1 = Unpooled.copiedBuffer(body1, StandardCharsets.UTF_8);
        req.handleContent(buf0);
        req.handleContent(buf1);

        assertEquals(1, buf0.refCnt());
        assertEquals(1, buf1.refCnt());

        req.multipart(false);
        assertSame(MultipartHandle.EMPTY, req.multipart());
        assertEquals(1, buf0.refCnt());
        assertEquals(1, buf1.refCnt());
    }

    @Test
    void testAggregationExpectWithEndedStatus() {
        final Req req = plainReq();
        req.isEnded = true;
        assertThrows(IllegalStateException.class, () -> req.aggregate(true));
    }

    @Test
    void testSetAggregatedTwice() {
        final Req req = plainReq();
        assertSame(AggregationHandle.EMPTY, req.aggregated());
        assertSame(req, req.aggregate(true));
        assertNotSame(AggregationHandle.EMPTY, req.aggregated());

        assertSame(req, req.aggregate(false));
        assertSame(AggregationHandle.EMPTY, req.aggregated());
    }

    @Test
    void testAggregated() {
        final Req req = plainReq();
        assertSame(AggregationHandle.EMPTY, req.aggregated());
        assertSame(req, req.aggregate(true));
        assertNotSame(AggregationHandle.EMPTY, req.aggregated());

        final CompletableFuture<Void> end = new CompletableFuture<>();
        req.onEnd(p -> {
            end.thenRun(() -> p.setSuccess(null));
            return p;
        });

        final ByteBuf buf0 = Unpooled.copiedBuffer("".getBytes(StandardCharsets.UTF_8));
        final ByteBuf buf1 = Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        final ByteBuf buf2 = Unpooled.copiedBuffer("456".getBytes(StandardCharsets.UTF_8));
        req.handleContent(buf0);
        req.handleContent(buf1);
        req.handleContent(buf2);

        final HttpHeaders trailers = new Http1HeadersImpl();
        req.handleTrailer(trailers);
        req.handleEnd();

        assertEquals("123456", req.aggregated().body().toString(StandardCharsets.UTF_8));
        assertEquals(1, req.aggregated().body().refCnt());

        // empty content will be ignored
        assertEquals(1, buf0.refCnt());
        // retained
        assertEquals(2, buf1.refCnt());
        assertEquals(2, buf2.refCnt());
        assertSame(trailers, req.aggregated().trailers());
        end.complete(null);
        assertEquals(1, buf0.refCnt());
        assertEquals(1, buf1.refCnt());
        assertEquals(1, buf2.refCnt());
        assertSame(AggregationHandle.EMPTY, req.aggregated());
    }

    @Test
    void testAggregatedWithDuplicatedData() {
        final Req req = plainReq();
        final CompletableFuture<Void> end = new CompletableFuture<>();
        req.onEnd(p -> {
            end.thenRun(() -> p.setSuccess(null));
            return p;
        }).onData(data -> data.skipBytes(data.readableBytes()))
                .aggregate(true);

        final ByteBuf buf1 = Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        final ByteBuf buf2 = Unpooled.copiedBuffer("456".getBytes(StandardCharsets.UTF_8));
        req.handleContent(buf1);
        req.handleContent(buf2);
        req.handleEnd();

        assertTrue(req.aggregated().body().isReadable());
        assertEquals("123456", req.aggregated().body().toString(StandardCharsets.UTF_8));
        assertEquals(1, req.aggregated().body().refCnt());

        // retainedDuplicate
        assertEquals(2, buf1.refCnt());
        assertEquals(2, buf2.refCnt());
        // onData is passed directly
        assertEquals(0, buf1.readableBytes());
        assertEquals(0, buf1.readableBytes());

        end.complete(null);
        assertEquals(1, buf1.refCnt());
        assertEquals(1, buf2.refCnt());
        assertSame(AggregationHandle.EMPTY, req.aggregated());
    }

    static Req plainReq() {
        return new Req(new EmbeddedChannel(new ChannelInboundHandlerAdapter()).pipeline().firstContext(), "/foo");
    }

    static class Req extends BaseRequestHandle {

        private final Http1HeadersImpl headers = new Http1HeadersImpl();
        private final BaseResponse response = mock(BaseResponse.class);
        private HttpRequest request;

        Req(ChannelHandlerContext ctx, String uri) {
            super(Helper.serverRuntime(), ctx, HttpMethod.GET, uri);
        }

        @Override
        public BaseResponse<? extends BaseRequestHandle> response() {
            return response;
        }

        @Override
        protected HttpRequest toHttpRequest() {
            return request;
        }

        @Override
        public HttpVersion version() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }
    }

}
