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

import esa.commons.http.HttpMethod;
import esa.commons.http.HttpVersion;
import esa.httpserver.utils.Constants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http2RequestHandleImplTest {

    @Test
    void testGeneration() {

        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        final EmbeddedChannel channel = new EmbeddedChannel();
        when(ctx.channel()).thenReturn(channel);
        final Http2ConnectionEncoder encoder = mock(Http2ConnectionEncoder.class);
        final Http2Headers headers = new DefaultHttp2Headers()
                .path("/foo?a=1&b=2")
                .method(HttpMethod.GET.name())
                .scheme(Constants.SCHEMA_HTTP)
                .add("a", "a");
        final Http2Stream stream = mock(Http2Stream.class);
        final Http2RequestHandleImpl handle =
                Http2RequestHandleImpl.from(Helper.serverRuntime(),
                        ctx,
                        encoder,
                        headers,
                        stream,
                        0,
                        Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                        false);

        assertEquals(Constants.SCHEMA_HTTP, handle.scheme());
        assertEquals(HttpVersion.HTTP_2, handle.version());
        assertEquals(HttpMethod.GET, handle.method());
        assertEquals("/foo?a=1&b=2", handle.uri());
        assertEquals("/foo", handle.path());
        assertEquals("a=1&b=2", handle.query());
        assertTrue(handle.headers() instanceof Http2HeadersImpl);
        assertEquals("a", handle.headers().get("a"));
        assertNotNull(handle.response());

        final HttpRequest req = handle.toHttpRequest();
        assertEquals(io.netty.handler.codec.http.HttpMethod.GET, req.method());
        assertEquals("/foo?a=1&b=2", req.uri());
        assertEquals("a", req.headers().get("a"));
    }

    @Test
    void testUsingSchemaInChannelAttribute() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        channel.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTPS);
        final Http2ConnectionEncoder encoder = mock(Http2ConnectionEncoder.class);
        final Http2Headers headers = new DefaultHttp2Headers()
                .path("/foo")
                .method(HttpMethod.GET.name());
        final Http2Stream stream = mock(Http2Stream.class);
        final Http2RequestHandleImpl handle =
                Http2RequestHandleImpl.from(Helper.serverRuntime(),
                        channel.pipeline().firstContext(),
                        encoder,
                        headers,
                        stream,
                        0,
                        Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                        false);

        assertEquals(Constants.SCHEMA_HTTPS, handle.scheme());
    }

}
