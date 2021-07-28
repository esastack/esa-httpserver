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

import io.esastack.commons.net.http.EmptyHttpHeaders;
import io.esastack.commons.net.http.HttpHeaders;
import io.esastack.httpserver.core.Aggregation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import static io.esastack.httpserver.impl.Utils.tryRelease;

final class AggregationHandle implements Aggregation {

    static final Empty EMPTY = new Empty();
    private static final int MAX_COMPOSITE_BUFFER_COMPONENTS = 1024;
    private final ChannelHandlerContext ctx;
    private CompositeByteBuf content;
    private HttpHeaders trailers;

    AggregationHandle(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public ByteBuf body() {
        if (content == null) {
            return EMPTY.body();
        }
        return content;
    }

    @Override
    public HttpHeaders trailers() {
        if (trailers == null) {
            return EMPTY.trailers();
        }
        return trailers;
    }

    void setTrailers(HttpHeaders trailers) {
        this.trailers = trailers;
    }

    void appendPartialContent(ByteBuf partialContent) {
        if (content == null) {
            content = ctx.alloc().compositeBuffer(MAX_COMPOSITE_BUFFER_COMPONENTS);
        }
        content.addComponent(true, partialContent);
    }

    void release() {
        if (content != null) {
            // Assuming that content may have been released, so just try to release it instead calling release()
            // which may produce an exception if refCnt() == 0.
            tryRelease(content);
        }
    }

    private static class Empty implements Aggregation {

        @Override
        public ByteBuf body() {
            return Unpooled.EMPTY_BUFFER;
        }

        @Override
        public HttpHeaders trailers() {
            return EmptyHttpHeaders.INSTANCE;
        }
    }
}
