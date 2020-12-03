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

import esa.httpserver.utils.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.ProtocolDetectionState;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;

import java.util.List;

import static esa.httpserver.impl.Utils.handleException;

final class HAProxyDetector extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        ProtocolDetectionResult<HAProxyProtocolVersion> ha = HAProxyMessageDecoder.detectProtocol(in);

        if (ha.state() == ProtocolDetectionState.NEEDS_MORE_DATA) {
            return;
        }

        if (ha.state() == ProtocolDetectionState.DETECTED) {
            if (Loggers.logger().isDebugEnabled()) {
                Loggers.logger().debug("Detected HAProxy protocol version of {} in connection {}",
                        ha.detectedProtocol(), ctx.channel());
            }

            ctx.pipeline()
                    .addAfter(ctx.name(),
                            "HAProxyMessageHandler",
                            new HAProxyMessageHandler());
            ctx.pipeline().replace(this, "HAProxyDecoder", new HAProxyMessageDecoder());
        } else {
            ctx.pipeline().remove(this);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        handleException(ctx, cause);
    }
}
