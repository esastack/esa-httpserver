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
import io.esastack.commons.net.http.HttpHeaders;
import io.esastack.commons.net.http.HttpMethod;
import io.esastack.commons.net.http.HttpVersion;
import io.esastack.httpserver.ServerOptions;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.esastack.httpserver.core.Request;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http2HandlerTest {

    private static final int STREAM_ID = 3;
    private EmbeddedChannel channel;
    private Http2Connection connection;
    private Http2FrameInboundWriter frameInboundWriter;
    private Http2FrameWriter frameWriter;
    private List<Object> usrEvts = new CopyOnWriteArrayList<>();

    void setUp(Consumer<RequestHandle> handler) {
        setUp(ServerOptionsConfigure.newOpts().configured(), handler);
    }

    void setUp(ServerOptions options, Consumer<RequestHandle> handler) {
        connection = new DefaultHttp2Connection(true);
        frameWriter = Helper.mockHttp2FrameWriter();
        Http2FrameReader reader = new DefaultHttp2FrameReader();

        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, frameWriter);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        final Http2Handler frameListener = new Http2Handler(Helper.serverRuntime(options), encoder, handler);
        final Http2ConnectionChunkHandler connectionHandler = new Http2ConnectionChunkHandlerBuilder()
                .codec(decoder, encoder)
                .frameListener(frameListener)
                .build();
        channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTP);
        frameInboundWriter = new Http2FrameInboundWriter(channel);
        channel.pipeline().addLast(connectionHandler);
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                usrEvts.add(evt);
            }
        });
        channel.pipeline().fireChannelActive();
        verify(frameWriter).writeSettings(any(ChannelHandlerContext.class), any(), any(ChannelPromise.class));
        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());
        frameInboundWriter.writeInboundSettings(Http2Settings.defaultSettings());
        frameInboundWriter.writeInboundSettingsAck();
        Http2Settings settingsFrame = channel.readOutbound();
        assertNotNull(settingsFrame);

        Object settingAck = channel.readOutbound();
        assertSame(Helper.SETTINGS_ACK, settingAck);
    }

    ChannelHandlerContext ctx() {
        return channel.pipeline().firstContext();
    }

    @Test
    void testHandleFullGetHttpRequest() {

        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.GET.name())
                .path("/foo?a=1&b=2");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, true);

        final Request r = req.get();
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertNull(onError.get());
        assertTrue(onEnd.get());

        assertEquals(HttpVersion.HTTP_2, r.version());
        assertEquals(Constants.SCHEMA_HTTP, r.scheme());
        assertEquals("/foo?a=1&b=2", r.uri());
        assertEquals("/foo", r.path());
        assertEquals("a=1&b=2", r.query());
        assertEquals(HttpMethod.GET, r.method());
        assertEquals(HttpMethod.GET.name(), r.rawMethod());
        assertEquals("1", r.getParam("a"));
        assertEquals("2", r.getParam("b"));

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.OK.codeAsText().toString(), head.headers.status().toString());
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertFalse(head.endStream);

        final Helper.DataFrame body = channel.readOutbound();
        assertEquals(STREAM_ID, body.streamId);
        assertEquals(0, body.padding);
        assertEquals("ok", body.data.toString(StandardCharsets.UTF_8));
        assertTrue(body.endStream);

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testHandleFullPostHttpRequest() {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo?a=1&b=2");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf body = copiedBuffer("123".getBytes());
        frameInboundWriter.writeInboundData(STREAM_ID, body, 0, true);

        final Request r = req.get();
        assertEquals("123", data.toString(StandardCharsets.UTF_8));
        assertNull(trailers.get());
        assertNull(onError.get());
        assertTrue(onEnd.get());

        assertEquals(HttpVersion.HTTP_2, r.version());
        assertEquals(Constants.SCHEMA_HTTP, r.scheme());
        assertEquals("/foo?a=1&b=2", r.uri());
        assertEquals("/foo", r.path());
        assertEquals("a=1&b=2", r.query());
        assertEquals(HttpMethod.POST, r.method());
        assertEquals(HttpMethod.POST.name(), r.rawMethod());
        assertEquals("1", r.getParam("a"));
        assertEquals("2", r.getParam("b"));

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.OK.codeAsText().toString(), head.headers.status().toString());
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertFalse(head.endStream);

        final Helper.DataFrame resBody = channel.readOutbound();
        assertEquals(STREAM_ID, resBody.streamId);
        assertEquals(0, resBody.padding);
        assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
        assertTrue(resBody.endStream);

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testHandleFullPostHttpRequestWithTrailer() {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo?a=1&b=2");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf chunk1 = copiedBuffer("123".getBytes());
        final ByteBuf chunk2 = copiedBuffer("45".getBytes());
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, false);

        final Http2Headers trailer = new DefaultHttp2Headers().set("a", "1");
        frameInboundWriter.writeInboundHeaders(STREAM_ID, trailer, 0, true);

        assertNotNull(req.get());
        assertEquals("12345", data.toString(StandardCharsets.UTF_8));
        assertNotNull(trailers.get());
        assertTrue(trailers.get().contains("a", "1"));
        assertNull(onError.get());
        assertTrue(onEnd.get());

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.OK.codeAsText().toString(), head.headers.status().toString());
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertFalse(head.endStream);

        final Helper.DataFrame resBody = channel.readOutbound();
        assertEquals(STREAM_ID, resBody.streamId);
        assertEquals(0, resBody.padding);
        assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
        assertTrue(resBody.endStream);

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testInvalidContentLengthHeaderValue() {

        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        setUp(ServerOptionsConfigure.newOpts().maxContentLength(4L).configured(), r -> {
            req.set(r);
            r.aggregate(true).onEnd(p -> {
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
                return p.setSuccess(null);
            });
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo")
                .set(HttpHeaderNames.CONTENT_LENGTH, "6");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1 = copiedBuffer("1234".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk2 = copiedBuffer("56".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.codeAsText().toString(),
                head.headers.status().toString());
        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "0"));
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertTrue(head.endStream);

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testValidContentLengthHeaderValue() {

        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        setUp(ServerOptionsConfigure.newOpts().maxContentLength(6L).configured(), r -> {
            req.set(r);
            r.aggregate(true).onEnd(p -> {
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
                return p.setSuccess(null);
            });
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo")
                .set(HttpHeaderNames.CONTENT_LENGTH, "6");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1 = copiedBuffer("1234".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk2 = copiedBuffer("56".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                head.headers.status().toString());
        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertFalse(head.endStream);

        final Helper.DataFrame resBody = channel.readOutbound();
        assertEquals(STREAM_ID, resBody.streamId);
        assertEquals(0, resBody.padding);
        assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
        assertTrue(resBody.endStream);

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testInvalidContentLengthHeaderValueIn100ContinueRequest() {
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        setUp(ServerOptionsConfigure.newOpts().maxContentLength(4L).configured(), r -> {
            req.set(r);
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo")
                .set(HttpHeaderNames.CONTENT_LENGTH, "6")
                .set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1 = copiedBuffer("1234".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk2 = copiedBuffer("56".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.codeAsText().toString(),
                head.headers.status().toString());
        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "0"));
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertTrue(head.endStream);

        assertEquals(0, chunk1.refCnt());
        assertEquals(0, chunk2.refCnt());

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testExpectationFailed() {
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        setUp(ServerOptionsConfigure.newOpts().maxContentLength(6L).configured(), r -> {
            req.set(r);
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo")
                .set(HttpHeaderNames.CONTENT_LENGTH, "6")
                .set(HttpHeaderNames.EXPECT, "invalid value");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1 = copiedBuffer("1234".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk2 = copiedBuffer("56".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED.codeAsText().toString(),
                head.headers.status().toString());
        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "0"));
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertTrue(head.endStream);

        assertEquals(0, chunk1.refCnt());
        assertEquals(0, chunk2.refCnt());

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testValidContentLengthIn100ContinueRequest() {
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        setUp(ServerOptionsConfigure.newOpts().maxContentLength(6L).configured(), r -> {
            req.set(r);
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo")
                .set(HttpHeaderNames.CONTENT_LENGTH, "6")
                .set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1 = copiedBuffer("1234".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk2 = copiedBuffer("56".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame continueRes = channel.readOutbound();
        assertEquals(STREAM_ID, continueRes.streamId);
        assertEquals(HttpResponseStatus.CONTINUE.codeAsText().toString(),
                continueRes.headers.status().toString());
        assertFalse(continueRes.headers.contains(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals(0, continueRes.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, continueRes.weight);
        assertFalse(continueRes.exclusive);
        assertEquals(0, continueRes.padding);
        assertFalse(continueRes.endStream);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                head.headers.status().toString());
        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertFalse(head.endStream);

        final Helper.DataFrame resBody = channel.readOutbound();
        assertEquals(STREAM_ID, resBody.streamId);
        assertEquals(0, resBody.padding);
        assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
        assertTrue(resBody.endStream);

        assertNotNull(req.get());
        assertEquals(HttpMethod.POST, req.get().method());
        assertEquals("/foo", req.get().uri());
        assertFalse(req.get().headers().contains(HttpHeaderNames.EXPECT));

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testContentLengthOverSizedBytesShouldBeIgnored() {
        testContentLengthOverSizedBytesShouldBeIgnored0(true);
        testContentLengthOverSizedBytesShouldBeIgnored0(false);
    }

    private void testContentLengthOverSizedBytesShouldBeIgnored0(boolean exactly) {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
            r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo")
                .set(HttpHeaderNames.CONTENT_LENGTH, "4");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1;
        if (exactly) {
            chunk1 = copiedBuffer("1234".getBytes(StandardCharsets.UTF_8));
        } else {
            chunk1 = copiedBuffer("01234".getBytes(StandardCharsets.UTF_8));
        }
        final ByteBuf chunk2 = copiedBuffer("56".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                head.headers.status().toString());
        assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
        assertEquals(0, head.streamDependency);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertFalse(head.exclusive);
        assertEquals(0, head.padding);
        assertFalse(head.endStream);

        final Helper.DataFrame resBody = channel.readOutbound();
        assertEquals(STREAM_ID, resBody.streamId);
        assertEquals(0, resBody.padding);
        assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
        assertTrue(resBody.endStream);

        assertNotNull(req.get());
        assertEquals(HttpMethod.POST, req.get().method());
        assertEquals("/foo", req.get().uri());
        assertFalse(req.get().headers().contains(HttpHeaderNames.EXPECT));
        assertEquals(exactly ? "1234" : "0123", data.toString(StandardCharsets.UTF_8));

        assertEquals(0, chunk2.refCnt());
        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testInvalidContentLengthInChunkedRequest() {
        testInvalidContentLengthInChunkedRequest(true);
        testInvalidContentLengthInChunkedRequest(false);
    }

    private void testInvalidContentLengthInChunkedRequest(boolean ended) {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(ServerOptionsConfigure.newOpts().maxContentLength(4L).configured(), r -> {
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
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        final ByteBuf chunk1 = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk2 = copiedBuffer("456".getBytes(StandardCharsets.UTF_8));
        final ByteBuf chunk3 = copiedBuffer("78".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, false);
        frameInboundWriter.writeInboundData(STREAM_ID, chunk3, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertEquals(0, head.streamDependency);
        assertFalse(head.exclusive);

        if (ended) {
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertTrue(head.headers.contains("a", "1", true));
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "0", true));
            assertFalse(head.headers.contains("a", "1", true));
            assertTrue(head.endStream);
        }

        assertNotNull(req.get());
        assertEquals("1234", data.toString(StandardCharsets.UTF_8));
        assertTrue(data.release());
        assertEquals(0, chunk1.refCnt());
        assertEquals(0, chunk2.refCnt());
        assertEquals(0, chunk3.refCnt());
        assertTrue(onError.get() instanceof TooLongFrameException);
        assertFalse(onEnd.get());
        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testChannelCloseWhenHandingRequest() {
        testChannelCloseWhenHandingRequest(true, true);
        testChannelCloseWhenHandingRequest(true, false);
        testChannelCloseWhenHandingRequest(false, true);
        testChannelCloseWhenHandingRequest(false, false);
    }

    void testChannelCloseWhenHandingRequest(boolean idleEventTriggered,
                                            boolean ended) {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
                r.response().setStatus(500);
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
            }
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        if (idleEventTriggered) {
            channel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
        } else {
            channel.close();
        }

        final ByteBuf chunk = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk, 0, true);

        if (ended) {
            final Helper.HeaderFrame head = channel.readOutbound();
            assertEquals(STREAM_ID, head.streamId);
            assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
            assertEquals(0, head.streamDependency);
            assertFalse(head.exclusive);
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);

            final Helper.GoawayFrame goaway = channel.readOutbound();
            assertEquals(NO_ERROR.code(), goaway.errorCode);
            assertEquals(STREAM_ID, goaway.lastStreamId);
        } else {
            final Helper.GoawayFrame goaway = channel.readOutbound();
            assertEquals(NO_ERROR.code(), goaway.errorCode);
            assertEquals(STREAM_ID, goaway.lastStreamId);

            final Helper.HeaderFrame head = channel.readOutbound();
            assertEquals(STREAM_ID, head.streamId);
            assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
            assertEquals(0, head.streamDependency);
            assertFalse(head.exclusive);
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertEquals(0, head.padding);
            assertTrue(head.endStream);
        }

        assertNotNull(req.get());
        assertEquals("123", data.toString(StandardCharsets.UTF_8));
        assertNull(trailers.get());
        assertTrue(onEnd.get());
        assertNull(onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());
        assertTrue(req.get().response().endFuture().isSuccess());
        assertTrue(req.get().response().onEndFuture().isSuccess());

        assertNull(connection.stream(STREAM_ID));
        assertFalse(channel.isActive());
    }

    @Test
    void testChannelInactiveWhenHandingRequest() {
        testChannelInactiveWhenHandingRequest(true, true);
        testChannelInactiveWhenHandingRequest(true, false);
        testChannelInactiveWhenHandingRequest(false, true);
        testChannelInactiveWhenHandingRequest(false, false);
    }

    void testChannelInactiveWhenHandingRequest(boolean isReset, boolean ended) {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
                r.response().setStatus(500);
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
            }
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        if (isReset) {
            frameInboundWriter.writeInboundRstStream(STREAM_ID, Http2Error.INTERNAL_ERROR.code());
        } else {
            channel.pipeline().fireChannelInactive();
        }

        if (ended) {
            final Helper.HeaderFrame head = channel.readOutbound();
            assertEquals(STREAM_ID, head.streamId);
            assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
            assertEquals(0, head.streamDependency);
            assertFalse(head.exclusive);
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertNull(channel.readOutbound());
        }

        assertNotNull(req.get());
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertTrue(onError.get() instanceof Http2Exception);
        if (isReset) {
            assertEquals(Http2Error.INTERNAL_ERROR, ((Http2Exception) onError.get()).error());
        } else {
            assertEquals(Http2Error.STREAM_CLOSED, ((Http2Exception) onError.get()).error());
        }

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        if (ended) {
            assertTrue(req.get().response().endFuture().isSuccess());
            assertTrue(req.get().response().onEndFuture().isSuccess());
        } else {
            assertFalse(req.get().response().endFuture().isSuccess());
            assertFalse(req.get().response().onEndFuture().isSuccess());
            assertSame(req.get().response().endFuture().cause(), onError.get());
            assertSame(req.get().response().onEndFuture().cause(), onError.get());
        }

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testHttp2ExceptionCaught() {
        testHttp2ExceptionCaught(true);
        testHttp2ExceptionCaught(false);
    }

    void testHttp2ExceptionCaught(boolean ended) {
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
                r.response().setStatus(500);
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
            }
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);

        channel.pipeline()
                .fireExceptionCaught(Http2Exception.streamError(STREAM_ID, Http2Error.INTERNAL_ERROR, "err"));

        if (ended) {
            final Helper.HeaderFrame head = channel.readOutbound();
            assertEquals(STREAM_ID, head.streamId);
            assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
            assertEquals(0, head.streamDependency);
            assertFalse(head.exclusive);
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertNull(channel.readOutbound());
        }

        assertNotNull(req.get());
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertTrue(onError.get() instanceof Http2Exception);
        assertEquals(Http2Error.STREAM_CLOSED, ((Http2Exception) onError.get()).error());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        if (ended) {
            assertTrue(req.get().response().endFuture().isSuccess());
            assertTrue(req.get().response().onEndFuture().isSuccess());
        } else {
            assertFalse(req.get().response().endFuture().isSuccess());
            assertFalse(req.get().response().onEndFuture().isSuccess());
            assertSame(req.get().response().endFuture().cause(), onError.get());
            assertSame(req.get().response().onEndFuture().cause(), onError.get());
        }

        assertTrue(usrEvts.isEmpty());
        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInRequestHandle() {
        testErrorInRequestHandle(true);
        testErrorInRequestHandle(false);
    }

    private void testErrorInRequestHandle(boolean ended) {
        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        setUp(r -> {
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
                r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
            }
            ExceptionUtils.throwException(ex);
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf chunk = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertEquals(0, head.streamDependency);
        assertFalse(head.exclusive);
        if (ended) {
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertTrue(head.headers
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals(ex.getMessage(), resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        }

        assertNotNull(req.get());
        assertFalse(data.isReadable());
        assertNull(trailers.get());
        assertFalse(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnDataHandler() {
        testErrorInOnDataHandler(true);
        testErrorInOnDataHandler(false);
    }

    private void testErrorInOnDataHandler(boolean ended) {
        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        setUp(r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                return p.setSuccess(null);
            });
            r.onData(buf -> {
                if (ended) {
                    r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(ex);
            });
            r.onTrailer(trailers::set);
            r.onError(onError::set);
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf chunk1 = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        final ByteBuf chunk2 = copiedBuffer("456".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk2, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertEquals(0, head.streamDependency);
        assertFalse(head.exclusive);
        if (ended) {
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertTrue(head.headers
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals(ex.getMessage(), resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
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

        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnTrailerHandler() {
        testErrorInOnTrailerHandler(true);
        testErrorInOnTrailerHandler(false);
    }

    private void testErrorInOnTrailerHandler(boolean ended) {
        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        setUp(r -> {
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
                    r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(ex);
            });
            r.onError(onError::set);
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf chunk1 = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, false);
        final Http2Headers trailer = new DefaultHttp2Headers().set("a", "1");
        frameInboundWriter.writeInboundHeaders(STREAM_ID, trailer, 0, true);


        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertEquals(0, head.streamDependency);
        assertFalse(head.exclusive);
        if (ended) {
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertTrue(head.headers
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals(ex.getMessage(), resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        }

        assertNotNull(req.get());
        assertNotNull(trailers.get());
        assertTrue(trailers.get() instanceof Http2HeadersImpl);
        assertEquals(trailer, ((Http2HeadersImpl) trailers.get()).unwrap());
        assertFalse(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnEndHandler() {
        testErrorInOnEndHandler(true);
        testErrorInOnEndHandler(false);
    }

    private void testErrorInOnEndHandler(boolean ended) {
        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        setUp(r -> {
            req.set(r);
            r.onEnd(p -> {
                onEnd.set(true);
                if (ended) {
                    r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
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
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf chunk1 = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, true);

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertEquals(0, head.streamDependency);
        assertFalse(head.exclusive);
        if (ended) {
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertTrue(head.headers
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals(ex.getMessage(), resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        }

        assertNotNull(req.get());
        assertNull(trailers.get());
        assertTrue(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testErrorInOnErrorHandler() {
        testErrorInOnErrorHandler(true);
        testErrorInOnErrorHandler(false);
    }

    private void testErrorInOnErrorHandler(boolean ended) {
        final Exception ex = new IllegalStateException("foo");
        final AtomicBoolean onEnd = new AtomicBoolean();
        final AtomicReference<Throwable> onError = new AtomicReference<>();
        final AtomicReference<RequestHandle> req = new AtomicReference<>();
        final CompositeByteBuf data = Unpooled.compositeBuffer();
        final AtomicReference<HttpHeaders> trailers = new AtomicReference<>();
        setUp(r -> {
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
                    r.response().end(copiedBuffer("ok".getBytes(StandardCharsets.UTF_8)));
                }
                ExceptionUtils.throwException(new IllegalStateException("bar"));
            });
        });

        final Http2Headers request = new DefaultHttp2Headers()
                .method(HttpMethod.POST.name())
                .path("/foo");

        frameInboundWriter.writeInboundHeaders(STREAM_ID, request, 0, false);
        final ByteBuf chunk1 = copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        frameInboundWriter.writeInboundData(STREAM_ID, chunk1, 0, true);

        verify(frameWriter).writeData(any(ChannelHandlerContext.class),
                anyInt(),
                any(ByteBuf.class),
                anyInt(),
                anyBoolean(),
                any(ChannelPromise.class));

        final Helper.HeaderFrame head = channel.readOutbound();
        assertEquals(STREAM_ID, head.streamId);
        assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, head.weight);
        assertEquals(0, head.streamDependency);
        assertFalse(head.exclusive);
        if (ended) {
            assertEquals(HttpResponseStatus.OK.codeAsText().toString(),
                    head.headers.status().toString());
            assertTrue(head.headers.contains(HttpHeaderNames.CONTENT_LENGTH, "2"));
            assertEquals(0, head.padding);
            assertFalse(head.endStream);

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals("ok", resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        } else {
            assertTrue(head.headers
                    .contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN, true));

            final Helper.DataFrame resBody = channel.readOutbound();
            assertEquals(STREAM_ID, resBody.streamId);
            assertEquals(0, resBody.padding);
            assertEquals(ex.getMessage(), resBody.data.toString(StandardCharsets.UTF_8));
            assertTrue(resBody.endStream);
        }

        assertNotNull(req.get());
        assertNull(trailers.get());
        assertTrue(onEnd.get());
        assertSame(ex, onError.get());

        assertTrue(req.get().isEnded());
        assertTrue(req.get().response().isCommitted());
        assertTrue(req.get().response().isEnded());

        assertNull(connection.stream(STREAM_ID));
        assertTrue(channel.isActive());
    }

    @Test
    void testOnHeadersReadOverride() {
        final AtomicReference<Helper.HeaderFrame> headerFrame = new AtomicReference<>();
        final Http2ConnectionEncoder encoder = mock(Http2ConnectionEncoder.class);
        final Http2Connection connection = new DefaultHttp2Connection(true);
        when(encoder.connection()).thenReturn(connection);
        final Http2Handler handler =
                spy(new Http2Handler(Helper.serverRuntime(), encoder, r -> {
                }));

        final Http2Headers headers = new DefaultHttp2Headers();
        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        final IllegalStateException e = new IllegalStateException();
        doThrow(e).when(handler).onHeadersRead(any(ChannelHandlerContext.class),
                anyInt(),
                any(Http2Headers.class),
                anyInt(),
                anyShort(),
                anyBoolean(),
                anyInt(),
                anyBoolean());

        assertThrows(IllegalStateException.class,
                () -> handler.onHeadersRead(ctx, STREAM_ID, headers, 0, true));

        verify(handler).onHeadersRead(same(ctx),
                eq(STREAM_ID),
                same(headers),
                eq(0),
                eq(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT),
                eq(false),
                eq(0),
                eq(true));
    }
}
