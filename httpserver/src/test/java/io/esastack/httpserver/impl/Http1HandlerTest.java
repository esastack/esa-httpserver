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

import esa.commons.ExceptionUtils;
import esa.commons.http.HttpHeaders;
import esa.commons.http.HttpMethod;
import esa.commons.http.HttpVersion;
import esa.commons.netty.http.Http1HeadersImpl;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.esastack.httpserver.core.Request;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.utils.Constants;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static org.junit.jupiter.api.Assertions.*;

class Http1HandlerTest {

    @Test
    void testHandleFullGetHttpRequest() {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
            r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
        }));

        channel.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTP);
        channel.writeInbound(new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.GET,
                "/foo?a=1&b=2",
                new Http1HeadersImpl()));
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final Request r = req.get();
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertNull(onError.get());
        assertTrue(onEnd.get());

        assertEquals(HttpVersion.HTTP_1_1, r.version());
        assertEquals(Constants.SCHEMA_HTTP, r.scheme());
        assertEquals("/foo?a=1&b=2", r.uri());
        assertEquals("/foo", r.path());
        assertEquals("a=1&b=2", r.query());
        assertEquals(HttpMethod.GET, r.method());
        assertEquals(HttpMethod.GET.name(), r.rawMethod());
        assertEquals("1", r.getParam("a"));
        assertEquals("2", r.getParam("b"));
        final FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        assertEquals("456", response.content().toString(StandardCharsets.UTF_8));

    }

    @Test
    void testHandleFullPostHttpRequest() {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
            r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
        }));
        channel.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTP);

        channel.writeInbound(new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo?a=1&b=2",
                new Http1HeadersImpl()));
        final HttpContent body = new DefaultHttpContent(copiedBuffer("123".getBytes()));
        channel.writeInbound(body);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final Request r = req.get();
        assertTrue(ByteBufUtil.equals(data, body.content()));
        assertNull(trailers.get());
        assertNull(onError.get());
        assertTrue(onEnd.get());

        assertEquals(HttpVersion.HTTP_1_1, r.version());
        assertEquals(Constants.SCHEMA_HTTP, r.scheme());
        assertEquals("/foo?a=1&b=2", r.uri());
        assertEquals("/foo", r.path());
        assertEquals("a=1&b=2", r.query());
        assertEquals(HttpMethod.POST, r.method());
        assertEquals(HttpMethod.POST.name(), r.rawMethod());
        assertEquals("1", r.getParam("a"));
        assertEquals("2", r.getParam("b"));
        final FullHttpResponse response = channel.readOutbound();
        assertEquals(200, response.status().code());
        assertEquals("456", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void testHandleFullPostHttpRequestWithTrailer() {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
            r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
        }));


        channel.writeInbound(new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo?a=1&b=2",
                new Http1HeadersImpl()));
        final HttpContent body = new DefaultHttpContent(copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(body);
        final LastHttpContent last = (new DefaultLastHttpContent(copiedBuffer("45".getBytes(StandardCharsets.UTF_8))));
        last.trailingHeaders().set("a", "1");
        channel.writeInbound(last);

        assertNotNull(req.get());
        assertEquals("12345", data.toString(StandardCharsets.UTF_8));
        assertNotNull(trailers.get());
        assertTrue(trailers.get().contains("a", "1"));
        assertNull(onError.get());
        assertTrue(onEnd.get());
    }

    @Test
    void testHttpMessageDecodeError() {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.response().headers().set("a", "1");
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onError(onError::set);
            r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        final Exception ex = new IllegalStateException("foo");
        request.setDecoderResult(DecoderResult.failure(ex));
        channel.writeInbound(request);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);
        assertEquals(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.BAD_REQUEST, res.status());
        assertEquals(request.decoderResult().toString(), res.content().toString(StandardCharsets.UTF_8));
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH,
                String.valueOf(res.content().readableBytes()), true));
        assertTrue(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));

        assertNull(req.get());
        assertNull(onError.get());
        assertFalse(onEnd.get());

        assertFalse(channel.isActive());
    }

    @Test
    void testHttpContentDecodeError() {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.response().headers().set("a", "1");
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onError(onError::set);
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());

        channel.writeInbound(request);
        final HttpContent content = new DefaultHttpContent(copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        final Exception ex = new IllegalStateException("foo");
        content.setDecoderResult(DecoderResult.failure(ex));
        channel.writeInbound(content);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);
        assertEquals(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.BAD_REQUEST, res.status());
        assertEquals(content.decoderResult().toString(), res.content().toString(StandardCharsets.UTF_8));
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH,
                String.valueOf(res.content().readableBytes()), true));
        assertTrue(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertFalse(res.headers().contains("a", "1", true));

        assertNotNull(req.get());
        assertTrue(req.get().isEnded());
        assertSame(ex, onError.get());
        assertFalse(onEnd.get());

        assertFalse(channel.isActive());
    }

    @Test
    void testHttpContentDecodeErrorButResponseHasBeenCommitted() {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.response().headers().set("a", "1");
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onError(onError::set);
            r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());

        channel.writeInbound(request);
        final HttpContent content = new DefaultHttpContent(copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        final Exception ex = new IllegalStateException("foo");
        content.setDecoderResult(DecoderResult.failure(ex));
        channel.writeInbound(content);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);
        assertEquals(io.netty.handler.codec.http.HttpVersion.HTTP_1_1, res.protocolVersion());
        assertEquals(HttpResponseStatus.OK, res.status());
        assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH,
                String.valueOf(res.content().readableBytes()), true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertTrue(res.headers().contains("a", "1", true));

        assertNotNull(req.get());
        assertTrue(req.get().isEnded());
        assertSame(ex, onError.get());
        assertFalse(onEnd.get());

        assertTrue(channel.isActive());
    }

    @Test
    void testInvalidContentLengthHeaderValue() {
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .maxContentLength(4L)
                .configured());


        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(runtime, r -> {
            req.set(r);
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "6");
        channel.writeInbound(request);
        final HttpContent content = new DefaultHttpContent(copiedBuffer("123456".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(content);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, res.status());
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION));
        assertNull(req.get());
        assertTrue(channel.isActive());
        // content should be ignored and released
        assertEquals(0, content.refCnt());

        final HttpRequest request1 = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        request1.headers().set(HttpHeaderNames.CONTENT_LENGTH, "6");
        request1.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        assertFalse(res.content().isReadable());
        channel.writeInbound(request1);

        // channel should be closed
        assertFalse(channel.isActive());

        final FullHttpResponse res1 = channel.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, res.status());
        assertTrue(res1.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
        assertTrue(res1.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true));
        assertFalse(res1.content().isReadable());
        assertNull(req.get());
    }

    @Test
    void testValidContentLengthHeaderValue() {
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .maxContentLength(4L)
                .configured());


        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(runtime, r -> {
            req.set(r);
            r.aggregate(true)
                    .onEnd(p -> {
                        r.response().end(r.aggregated().body().retain());
                        return p.setSuccess(null);
                    });
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "4");
        channel.writeInbound(request);
        final HttpContent content = new DefaultHttpContent(copiedBuffer("1234".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(content);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, res.status());
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "4", true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION));
        assertNotNull(req.get());
        assertEquals("1234", res.content().toString(StandardCharsets.UTF_8));

        assertTrue(channel.isActive());
        assertTrue(content.release());
    }

    @Test
    void testInvalidContentLengthHeaderValueIn100ContinueRequest() {
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .maxContentLength(4L)
                .configured());


        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(runtime, r -> {
            req.set(r);
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "6");
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        channel.writeInbound(request);

        final HttpContent content = new DefaultHttpContent(copiedBuffer("123456".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(content);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, res.status());
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION));
        assertFalse(res.content().isReadable());
        assertNull(req.get());
        assertTrue(channel.isActive());

        // content should be ignored and released
        assertEquals(0, content.refCnt());
    }

    @Test
    void testExpectationFailed() {
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .maxContentLength(4L)
                .configured());

        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(runtime, r -> {
            req.set(r);
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "4");
        request.headers().set(HttpHeaderNames.EXPECT, "invalid value");
        channel.writeInbound(request);

        final HttpContent content = new DefaultHttpContent(copiedBuffer("1234".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(content);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, res.status());
        assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
        assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION));
        assertFalse(res.content().isReadable());
        assertNull(req.get());
        assertTrue(channel.isActive());

        // content should be ignored and released
        assertEquals(0, content.refCnt());

        final HttpRequest request1 = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST, "/foo");
        request1.headers().set(HttpHeaderNames.CONTENT_LENGTH, "4");
        request1.headers().set(HttpHeaderNames.EXPECT, "invalid value");
        request1.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        channel.writeInbound(request1);

        // channel should be closed
        assertFalse(channel.isActive());

        final FullHttpResponse res1 = channel.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, res1.status());
        assertTrue(res1.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
        assertFalse(res1.headers().contains(HttpHeaderNames.CONNECTION));
        assertFalse(res1.content().isReadable());
        assertNull(req.get());
    }

    @Test
    void testValidContentLengthIn100ContinueRequest() {
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .maxContentLength(4L)
                .configured());

        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(runtime, r -> {
            req.set(r);
            r.aggregate(true)
                    .onEnd(p -> {
                        r.response().end(r.aggregated().body().retain());
                        return p.setSuccess(null);
                    });
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST, "/foo", new Http1HeadersImpl());
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, "4");
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        channel.writeInbound(request);

        final HttpContent content = new DefaultHttpContent(copiedBuffer("1234".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(content);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertEquals(HttpResponseStatus.CONTINUE, res.status());
        assertTrue(res.headers().isEmpty());

        assertNotNull(req.get());
        assertEquals(HttpMethod.POST, req.get().method());
        assertEquals("/foo", req.get().uri());
        assertFalse(req.get().headers().contains(HttpHeaderNames.EXPECT));

        final FullHttpResponse res1 = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, res1.status());
        assertTrue(res1.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "4", true));
        assertFalse(res1.headers().contains(HttpHeaderNames.CONNECTION));
        assertEquals("1234", res1.content().toString(StandardCharsets.UTF_8));

        assertTrue(res1.release());
        assertTrue(channel.isActive());
    }

    @Test
    void testInvalidContentLengthInChunkedRequest() {
        testInvalidContentLengthInChunkedRequest(true);
        testInvalidContentLengthInChunkedRequest(false);
    }

    private static void testInvalidContentLengthInChunkedRequest(boolean ended) {
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .maxContentLength(4L)
                .configured());

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(runtime, r -> {
            req.set(r);
            r.response().headers().set("a", "1");
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            }).onEnd(p -> {
                onEnd.set(true);
                r.response().end();
                return p.setSuccess(null);
            }).onError(onError::set);
            if (ended) {
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
            }
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        channel.writeInbound(request);

        final HttpContent chunk1 = new DefaultHttpContent(copiedBuffer("12".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(chunk1);
        final HttpContent chunk2 = new DefaultHttpContent(copiedBuffer("345".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(chunk2);
        final HttpContent chunk3 = new DefaultHttpContent(copiedBuffer("34".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(chunk3);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        if (ended) {
            assertEquals(HttpResponseStatus.OK, res.status());
            assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "2", true));
            assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION));
            assertTrue(res.headers().contains("a", "1", true));
            assertTrue(res.content().isReadable());
            assertEquals("ok", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, res.status());
            assertTrue(res.headers().contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
            assertFalse(res.headers().contains(HttpHeaderNames.CONNECTION));
            assertFalse(res.headers().contains("a", "1", true));
            assertFalse(res.content().isReadable());
        }

        assertNotNull(req.get());
        assertEquals("1234", data.toString(StandardCharsets.UTF_8));
        assertTrue(data.release());
        assertEquals(0, chunk1.refCnt());
        assertEquals(0, chunk2.refCnt());
        assertEquals(0, chunk3.refCnt());
        assertTrue(onError.get() instanceof TooLongFrameException);
        assertFalse(onEnd.get());
        assertTrue(channel.isActive());
    }

    @Test
    void testChannelEnvironmentChangedWhenHandingRequest() {
        testChannelInactiveWhenHandingRequest(true, false, true);
        testChannelInactiveWhenHandingRequest(true, false, false);
        testChannelInactiveWhenHandingRequest(false, true, true);
        testChannelInactiveWhenHandingRequest(false, true, false);
        testChannelInactiveWhenHandingRequest(false, false, true);
        testChannelInactiveWhenHandingRequest(false, false, false);
    }

    static void testChannelInactiveWhenHandingRequest(boolean idleEventTriggered,
                                                      boolean handlerRemoved,
                                                      boolean ended) {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final List<Object> events = new CopyOnWriteArrayList<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
            if (ended) {
                r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
            }
        }), new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                events.add(evt);
            }
        });

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());

        channel.writeInbound(request);

        if (!idleEventTriggered) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        }

        if (idleEventTriggered) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
        } else if (handlerRemoved) {
            channel.pipeline().remove(Http1Handler.class);
        } else {
            channel.close();
        }

        if (idleEventTriggered) {
            assertTrue(events.isEmpty());
        } else {
            assertEquals(1, events.size());
            assertSame(IdleStateEvent.READER_IDLE_STATE_EVENT, events.get(0));
        }

        if (ended) {
            final FullHttpResponse res = channel.readOutbound();
            assertNotNull(res);
            assertEquals(HttpResponseStatus.OK, res.status());
            assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertNull(channel.readOutbound());
        }

        assertNotNull(req.get());
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertTrue(onError.get() instanceof ChannelException);

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        if (ended) {
            assertTrue(req.get().response().endFuture().isSuccess());
            assertTrue(req.get().response().onEndFuture().isSuccess());
        } else {
            assertFalse(req.get().response().endFuture().isSuccess());
            assertTrue(req.get().response().endFuture().cause() instanceof ChannelException);
            assertFalse(req.get().response().onEndFuture().isSuccess());
            assertTrue(req.get().response().onEndFuture().cause() instanceof ChannelException);
        }
    }

    @Test
    void testExceptionCaught() {
        testExceptionCaught(true);
        testExceptionCaught(false);
    }

    private static void testExceptionCaught(boolean ended) {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
            if (ended) {
                r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
            }
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());

        channel.writeInbound(request);

        final Exception ex = new IllegalStateException();
        channel.pipeline().fireExceptionCaught(ex);

        assertFalse(channel.isActive());

        assertNotNull(req.get());
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        if (ended) {
            assertTrue(req.get().response().endFuture().isSuccess());
            assertTrue(req.get().response().onEndFuture().isSuccess());
        } else {
            assertFalse(req.get().response().endFuture().isSuccess());
            assertSame(ex, req.get().response().endFuture().cause());
            assertFalse(req.get().response().onEndFuture().isSuccess());
            assertSame(ex, req.get().response().onEndFuture().cause());
        }
    }

    @Test
    void testErrorInRequestHandle() {
        testErrorInRequestHandle(true);
        testErrorInRequestHandle(false);
    }

    private static void testErrorInRequestHandle(boolean ended) {

        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
            if (ended) {
                r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
            }
            ExceptionUtils.throwException(ex);
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());

        channel.writeInbound(request);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);

        if (ended) {
            assertEquals(HttpResponseStatus.OK, res.status());
            assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, res.status());
            assertEquals(ex.getMessage(), res.content().toString(StandardCharsets.UTF_8));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));
        }

        assertNotNull(req.get());
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());
        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnDataHandler() {
        testErrorInOnDataHandler(true);
        testErrorInOnDataHandler(false);
    }

    private static void testErrorInOnDataHandler(boolean ended) {

        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();

        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (ended) {
                    r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(ex);
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        channel.writeInbound(request);

        final HttpContent chunk1 = new DefaultHttpContent(copiedBuffer("12".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(chunk1);
        final HttpContent chunk2 = new DefaultHttpContent(copiedBuffer("345".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(chunk2);
        final HttpContent chunk3 = new DefaultHttpContent(copiedBuffer("34".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(chunk3);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);

        if (ended) {
            assertEquals(HttpResponseStatus.OK, res.status());
            assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, res.status());
            assertEquals(ex.getMessage(), res.content().toString(StandardCharsets.UTF_8));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));
        }

        assertNotNull(req.get());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());
        assertEquals(0, chunk1.refCnt());
        assertEquals(0, chunk2.refCnt());
        assertEquals(0, chunk3.refCnt());
        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnTrailerHandler() {
        testErrorInOnTrailerHandler(true);
        testErrorInOnTrailerHandler(false);
    }

    private static void testErrorInOnTrailerHandler(boolean ended) {

        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(t -> {
                trailers.set(t);
                if (ended) {
                    r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(ex);
            });
            r.onError(onError::set);
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        channel.writeInbound(request);

        final HttpContent body = new DefaultHttpContent(copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(body);
        final LastHttpContent last = (new DefaultLastHttpContent(copiedBuffer("45".getBytes(StandardCharsets.UTF_8))));
        last.trailingHeaders().set("a", "1");
        channel.writeInbound(last);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);

        if (ended) {
            assertEquals(HttpResponseStatus.OK, res.status());
            assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, res.status());
            assertEquals(ex.getMessage(), res.content().toString(StandardCharsets.UTF_8));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));
        }

        assertNotNull(req.get());
        assertNotNull(trailers.get());
        assertFalse(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnEndHandler() {
        testErrorInOnEndHandler(true);
        testErrorInOnEndHandler(false);
    }

    private static void testErrorInOnEndHandler(boolean ended) {

        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                if (ended) {
                    r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(ex);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        channel.writeInbound(request);

        final HttpContent body = new DefaultHttpContent(copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(body);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);

        if (ended) {
            assertEquals(HttpResponseStatus.OK, res.status());
            assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, res.status());
            assertEquals(ex.getMessage(), res.content().toString(StandardCharsets.UTF_8));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));
        }

        assertNotNull(req.get());
        assertNull(trailers.get());
        assertTrue(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnErrorHandler() {
        testErrorInOnErrorHandler(true);
        testErrorInOnErrorHandler(false);
    }

    private static void testErrorInOnErrorHandler(boolean ended) {

        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final EmbeddedChannel channel = new EmbeddedChannel(new Http1Handler(Helper.serverRuntime(), r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                ExceptionUtils.throwException(ex);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (buf.isReadable()) {
                    data.addComponent(true, buf.retain());
                }
            });
            r.onTrailer(trailers::set);
            r.onError(t -> {
                onError.set(t);
                if (ended) {
                    r.response().end(copiedBuffer("456".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(new IllegalStateException("bar"));
            });
        }));

        final HttpRequest request = new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/foo",
                new Http1HeadersImpl());
        channel.writeInbound(request);

        final HttpContent body = new DefaultHttpContent(copiedBuffer("123".getBytes(StandardCharsets.UTF_8)));
        channel.writeInbound(body);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final FullHttpResponse res = channel.readOutbound();
        assertNotNull(res);

        if (ended) {
            assertEquals(HttpResponseStatus.OK, res.status());
            assertEquals("456", res.content().toString(StandardCharsets.UTF_8));
        } else {
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, res.status());
            assertEquals(ex.getMessage(), res.content().toString(StandardCharsets.UTF_8));
            assertTrue(res.headers()
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));
        }

        assertNotNull(req.get());
        assertNull(trailers.get());
        assertTrue(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        // channel should be closed if error occurred in onError Handler
        assertFalse(channel.isActive());
    }
}
