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

import esa.commons.Checks;
import io.esastack.commons.net.http.HttpHeaders;
import io.esastack.commons.net.http.HttpMethod;
import io.esastack.commons.net.http.HttpVersion;
import io.esastack.commons.net.netty.http.Http1HeadersImpl;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.utils.Constants;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;

import static io.esastack.httpserver.impl.Utils.standardHttp2Headers;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;

final class Http2RequestHandleImpl extends BaseRequestHandle implements RequestHandle {

    private final Http2HeadersImpl headers;
    private final Http2ResponseImpl response;
    private final String schema;
    final Http2Stream stream;
    long bytes;

    static Http2RequestHandleImpl from(
            ServerRuntime runtime,
            ChannelHandlerContext ctx,
            Http2ConnectionEncoder encoder,
            Http2Headers h2Headers,
            Http2Stream stream,
            int streamDependency,
            short weight,
            boolean exclusive) {

        final String path =
                Checks.checkNotNull(h2Headers.path(), "path").toString();
        final HttpMethod method =
                HttpMethod.fastValueOf(Checks.checkNotNull(h2Headers.method(), "method").toString());
        String schema;
        CharSequence c = h2Headers.scheme();
        if (c == null) {
            schema = ctx.channel().attr(Constants.SCHEME).get();
        } else {
            schema = c.toString();
        }
        standardHttp2Headers(h2Headers);
        return new Http2RequestHandleImpl(runtime,
                ctx,
                encoder,
                path,
                method,
                schema,
                h2Headers,
                stream,
                streamDependency,
                weight,
                exclusive);
    }

    private Http2RequestHandleImpl(ServerRuntime runtime,
                                   ChannelHandlerContext ctx,
                                   Http2ConnectionEncoder encoder,
                                   String uri,
                                   HttpMethod method,
                                   String schema,
                                   Http2Headers headers,
                                   Http2Stream stream,
                                   int streamDependency,
                                   short weight,
                                   boolean exclusive) {
        super(runtime, ctx, method, uri);
        this.schema = schema;
        this.headers = new Http2HeadersImpl(headers);
        this.bytes = headers.getLong(CONTENT_LENGTH, -1L);
        this.stream = stream;
        this.response = new Http2ResponseImpl(this,
                encoder,
                streamDependency,
                weight,
                exclusive);
    }

    @Override
    public HttpVersion version() {
        return HttpVersion.HTTP_2;
    }

    @Override
    public String scheme() {
        return schema;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public Http2ResponseImpl response() {
        return response;
    }

    @Override
    protected HttpRequest toHttpRequest() {
        return new DefaultHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.valueOf(rawMethod()),
                uri(), new Http1HeadersImpl().add(headers()));
    }
}
