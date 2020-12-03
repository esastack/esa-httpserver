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
import esa.httpserver.utils.Loggers;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.function.Consumer;

final class OnChannelActiveHandler extends ChannelInboundHandlerAdapter {

    private final Consumer<ChannelHandlerContext> onConnect;

    OnChannelActiveHandler(Consumer<ChannelHandlerContext> onConnect) {
        Checks.checkNotNull(onConnect, "onConnect");
        this.onConnect = onConnect;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        try {
            onConnect.accept(ctx);
        } catch (Throwable t) {
            Loggers.logger().error("Error while processing onConnect handler.", t);
        }
        ctx.pipeline().remove(this);
        super.channelActive(ctx);
    }

}
