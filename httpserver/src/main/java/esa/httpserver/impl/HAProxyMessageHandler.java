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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyProxiedProtocol;

import java.net.InetSocketAddress;

import static esa.httpserver.impl.Utils.SOURCE_ADDRESS;

@ChannelHandler.Sharable
final class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object o) throws Exception {
        if (o instanceof HAProxyMessage) {
            HAProxyMessage msg = (HAProxyMessage) o;
            if (!HAProxyProxiedProtocol.UNKNOWN.equals(msg.proxiedProtocol())
                    && msg.sourceAddress() != null) {
                ctx.channel()
                        .attr(SOURCE_ADDRESS)
                        .set(InetSocketAddress.createUnresolved(msg.sourceAddress(), msg.sourcePort()));
            }
            msg.release();
            ctx.pipeline().remove(this);
        } else {
            super.channelRead(ctx, o);
        }
    }
}
