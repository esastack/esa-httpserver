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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.stream.ChunkedWriteHandler;

import static io.esastack.httpserver.impl.Utils.handleException;
import static io.esastack.httpserver.impl.Utils.handleIdle;
import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;

final class Http2ConnectionChunkHandler extends Http2ConnectionHandler {

    Http2ConnectionChunkHandler(Http2ConnectionDecoder decoder,
                                Http2ConnectionEncoder encoder,
                                Http2Settings initialSettings,
                                boolean decoupleCloseAndGoAway) {
        super(decoder, encoder, initialSettings, decoupleCloseAndGoAway);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        // add a ChunkedWriteHandler after Http2ConnectionChunkHandler
        // usually we use the Http2ConnectionChunkHandler#encoder() to write http2 response, and this
        // ChunkedWriteHandler we added here is used to handle the messages type of Http2ChunkedInput(eg. large file)
        // TODO: this should be removed in the future and try another way to write chunked input by
        //  Http2RemoteFlowController#isWritable() and Http2RemoteFlowController$Listener#writabilityChanged(stream))
        //  see: https://github.com/netty/netty/issues/10801
        ctx.pipeline().addAfter(ctx.name(), "h2ChunkedWriter", new ChunkedWriteHandler());
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Http2ChunkedInput should be closed if we failed to write(eg. stream has been removed before writing),
        // which is implemented in ChunkedWriteHandler#handleFuture(xxx)
        if (msg instanceof Http2ChunkedInput.Content) {

            Http2ChunkedInput.Content c = (Http2ChunkedInput.Content) msg;
            boolean hasBody = c.content().readableBytes() > 0;

            if (hasBody) {
                if (msg instanceof Http2ChunkedInput.LastContent) {
                    Http2ChunkedInput.LastContent lastContent = (Http2ChunkedInput.LastContent) msg;
                    boolean hasTrailer = lastContent.trailers != null && !lastContent.trailers.isEmpty();

                    encoder().writeData(ctx,
                            c.streamId,
                            c.content(),
                            0,
                            !hasTrailer,
                            hasTrailer ? ctx.newPromise() : promise);

                    if (hasTrailer) {
                        encoder().writeHeaders(ctx,
                                c.streamId,
                                lastContent.trailers,
                                lastContent.streamDependency,
                                lastContent.weight,
                                lastContent.exclusive,
                                0,
                                true,
                                promise);
                    }
                } else {
                    encoder().writeData(ctx,
                            c.streamId,
                            c.content(),
                            0,
                            false,
                            promise);
                }
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!handleIdle(ctx, evt)) {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (getEmbeddedHttp2Exception(cause) != null) {
            super.exceptionCaught(ctx, cause);
        } else {
            handleException(ctx, cause);
        }
    }
}
