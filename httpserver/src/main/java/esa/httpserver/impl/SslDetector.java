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

import esa.commons.function.Consumer3;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import java.util.List;

import static esa.httpserver.impl.Utils.handleException;

final class SslDetector extends ByteToMessageDecoder {

    /**
     * the length of the ssl record header (in bytes)
     */
    private static final int SSL_RECORD_HEADER_LENGTH = 5;
    private final SslHelper sslHelper;
    private final Consumer3<Boolean, Channel, Throwable> callback;

    SslDetector(SslHelper sslHelper, Consumer3<Boolean, Channel, Throwable> callback) {
        this.sslHelper = sslHelper;
        this.callback = callback;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < SSL_RECORD_HEADER_LENGTH) {
            return;
        }

        // TODO: sni support
        if (isSsl(in)) {
            SSLEngine engine = sslHelper.getSslContext().newEngine(ctx.alloc());

            if (sslHelper.options().getEnabledProtocols() != null
                    && sslHelper.options().getEnabledProtocols().length > 0) {
                engine.setEnabledProtocols(sslHelper.options().getEnabledProtocols());
            }
            final SslHandler sslHandler = new SslHandler(engine);
            if (sslHelper.options().getHandshakeTimeoutMillis() > 0L) {
                sslHandler.setHandshakeTimeoutMillis(sslHelper.options().getHandshakeTimeoutMillis());
            }
            ctx.pipeline().addAfter(ctx.name(), "SslCompletionHandler", new SslCompletionHandler(callback));
            ctx.pipeline().replace(this, "SslHandler", sslHandler);
        } else {
            callback.accept(false, ctx.channel(), null);
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        handleException(ctx, cause);
    }

    private static boolean isSsl(ByteBuf in) {
        return SslHandler.isEncrypted(in);
    }
}
