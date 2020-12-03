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

import esa.commons.Checks;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.function.BiConsumer;

import static esa.httpserver.impl.Utils.handleException;
import static esa.httpserver.impl.Utils.handleIdle;
import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;

final class H2cDetector extends ByteToMessageDecoder {

    private static final ByteBuf CONNECTION_PREFACE = unreleasableBuffer(connectionPrefaceBuf());
    private final BiConsumer<ChannelHandlerContext, Boolean> callback;

    H2cDetector(BiConsumer<ChannelHandlerContext, Boolean> callback) {
        Checks.checkNotNull(callback, "callback");
        this.callback = callback;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int prefaceLength = CONNECTION_PREFACE.readableBytes();
        int bytesRead = Math.min(in.readableBytes(), prefaceLength);
        if (!ByteBufUtil.equals(CONNECTION_PREFACE, CONNECTION_PREFACE.readerIndex(),
                in, in.readerIndex(), bytesRead)) {
            callback.accept(ctx, false);
            ctx.pipeline().remove(this);
        } else if (bytesRead == prefaceLength) {
            // Full h2 preface match, removed source codec, using http2 codec to handle following network traffic
            callback.accept(ctx, true);
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        handleException(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!handleIdle(ctx, evt)) {
            super.userEventTriggered(ctx, evt);
        }
    }
}
