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
package io.esastack.httpserver.transport;

import esa.commons.Checks;
import io.esastack.httpserver.NetOptions;
import io.esastack.httpserver.utils.Loggers;
import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.internal.SystemPropertyUtil;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

/**
 * Standard JDK NIO transport.
 */
public class NioTransport implements Transport {

    static NioTransport INSTANCE = new NioTransport();

    private static final boolean USE_UNPOOLED_ALLOCATOR;

    static {
        USE_UNPOOLED_ALLOCATOR =
                SystemPropertyUtil.getBoolean("io.esastack.httpserver.useUnpooledAllocator", false);
        if (Loggers.logger().isDebugEnabled()) {
            Loggers.logger().debug("-Dio.esastack.httpserver.useUnpooledAllocator: {}", USE_UNPOOLED_ALLOCATOR);
        }
    }

    @Override
    public ChannelFactory<ServerChannel> serverChannelFactory(SocketAddress local) {
        Checks.checkArg(!(local instanceof DomainSocketAddress),
                "Domain socket is UNSUPPORTED in NIO transport");
        return NioServerSocketChannel::new;
    }

    @Override
    public EventLoopGroup loop(int nThreads, ThreadFactory threadFactory) {
        return new NioEventLoopGroup(nThreads, threadFactory);
    }

    @Override
    public void applyOptions(ServerBootstrap bootstrap,
                             NetOptions options,
                             SocketAddress local) {
        final boolean isDomainSocket = local instanceof DomainSocketAddress;
        if (!isDomainSocket) {
            bootstrap.childOption(ChannelOption.SO_KEEPALIVE, options.isSoKeepalive());
            bootstrap.childOption(ChannelOption.TCP_NODELAY, options.isTcpNoDelay());
        }

        if (options.getSoBacklog() > 0) {
            bootstrap.option(ChannelOption.SO_BACKLOG, options.getSoBacklog());
        }

        bootstrap.option(ChannelOption.SO_REUSEADDR, options.isReuseAddress());

        if (USE_UNPOOLED_ALLOCATOR) {
            bootstrap.childOption(ChannelOption.ALLOCATOR, UnpooledByteBufAllocator.DEFAULT);
        }

        if (options.getSoRcvbuf() > 0) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, options.getSoRcvbuf());
        }

        if (options.getSoSendbuf() > 0) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, options.getSoSendbuf());
        }

        if (options.getSoLinger() != -1) {
            bootstrap.childOption(ChannelOption.SO_LINGER, options.getSoLinger());
        }

        if (options.getOptions() != null) {
            addOption(bootstrap, options.getOptions());
        }

        if (options.getChildOptions() != null) {
            addChildOption(bootstrap, options.getChildOptions());
        }
    }

    private static void addOption(AbstractBootstrap bootstrap, Map<ChannelOption<?>, Object> options) {
        if (!options.isEmpty()) {
            for (Map.Entry<ChannelOption<?>, Object> entry : options.entrySet()) {
                bootstrap.option(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addChildOption(ServerBootstrap bootstrap, Map<ChannelOption<?>, Object> options) {
        if (!options.isEmpty()) {
            for (Map.Entry<ChannelOption<?>, Object> entry : options.entrySet()) {
                bootstrap.childOption((ChannelOption<Object>) entry.getKey(), entry.getValue());
            }
        }
    }
}
