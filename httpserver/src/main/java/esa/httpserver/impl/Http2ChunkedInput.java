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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.stream.ChunkedInput;

final class Http2ChunkedInput implements ChunkedInput<Http2ChunkedInput.Content> {

    private final ChunkedInput<ByteBuf> input;
    private final int streamId;
    private final int streamDependency;
    private final short weight;
    private final boolean exclusive;
    private final Http2Headers trailers;

    Http2ChunkedInput(ChunkedInput<ByteBuf> input,
                      Http2Headers trailers,
                      int streamId,
                      int streamDependency,
                      short weight,
                      boolean exclusive) {
        this.input = input;
        this.trailers = trailers;
        this.streamId = streamId;
        this.streamDependency = streamDependency;
        this.weight = weight;
        this.exclusive = exclusive;
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return input.isEndOfInput();
    }

    @Override
    public void close() throws Exception {
        input.close();
    }

    @Deprecated
    @Override
    public Content readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    @Override
    public Content readChunk(ByteBufAllocator allocator) throws Exception {
        ByteBuf buf = input.readChunk(allocator);
        if (buf == null) {
            return null;
        }

        if (input.isEndOfInput()) {
            return new LastContent(buf, trailers, streamId, streamDependency, weight, exclusive);
        } else {
            return new Content(buf, streamId);
        }
    }

    @Override
    public long length() {
        return input.length();
    }

    @Override
    public long progress() {
        return input.progress();
    }

    static class Content extends DefaultHttpContent {

        final int streamId;

        Content(ByteBuf content,
                int streamId) {
            super(content);
            this.streamId = streamId;
        }
    }

    static final class LastContent extends Content {

        final Http2Headers trailers;
        final int streamDependency;
        final short weight;
        final boolean exclusive;

        LastContent(ByteBuf content,
                    Http2Headers trailers,
                    int streamId,
                    int streamDependency,
                    short weight,
                    boolean exclusive) {
            super(content, streamId);
            this.trailers = trailers;
            this.streamDependency = streamDependency;
            this.weight = weight;
            this.exclusive = exclusive;
        }
    }
}
