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

import esa.commons.function.Consumer3;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

final class SslCompletionHandler extends ChannelInboundHandlerAdapter {

    private final Consumer3<Boolean, Channel, Throwable> callback;

    SslCompletionHandler(Consumer3<Boolean, Channel, Throwable> callback) {
        this.callback = callback;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
            if (event.isSuccess()) {
                ctx.pipeline().remove(this);
                callback.accept(true, ctx.channel(), null);
            } else {
                ctx.pipeline().remove(this);
                callback.accept(false, ctx.channel(), event.cause());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Ignore these exception as they will be reported to the handler
        // the handshake will ultimately fail or succeed
    }
}
