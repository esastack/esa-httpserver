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

import io.esastack.commons.net.http.HttpHeaders;
import io.esastack.commons.net.http.HttpMethod;
import io.esastack.commons.net.http.HttpVersion;
import io.esastack.httpserver.core.RequestHandle;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

final class Http1RequestHandleImpl extends BaseRequestHandle implements RequestHandle {

    final HttpRequest req;
    private final Http1ResponseImpl response;

    Http1RequestHandleImpl(ServerRuntime runtime,
                           ChannelHandlerContext ctx,
                           HttpRequest req,
                           boolean isKeepAlive) {
        super(runtime, ctx, HttpMethod.fastValueOf(req.method().name()), req.uri());
        this.req = req;
        this.response = new Http1ResponseImpl(this, isKeepAlive);
    }

    @Override
    public HttpVersion version() {
        io.netty.handler.codec.http.HttpVersion version = req.protocolVersion();
        if (version == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
            return HttpVersion.HTTP_1_0;
        } else {
            return HttpVersion.HTTP_1_1;
        }
    }

    @Override
    public HttpHeaders headers() {
        return (HttpHeaders) req.headers();
    }

    @Override
    public Http1ResponseImpl response() {
        return response;
    }

    @Override
    protected HttpRequest toHttpRequest() {
        return req;
    }
}
