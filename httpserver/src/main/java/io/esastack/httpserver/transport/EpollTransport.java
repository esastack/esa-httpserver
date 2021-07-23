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

import io.esastack.httpserver.NetOptions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;

import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * @see io.netty.channel.epoll.Epoll
 */
public final class EpollTransport extends NioTransport implements Transport {

    @Override
    public ChannelFactory<ServerChannel> serverChannelFactory(SocketAddress local) {
        if (local instanceof DomainSocketAddress) {
            return EpollServerDomainSocketChannel::new;
        }
        return EpollServerSocketChannel::new;
    }

    @Override
    public EventLoopGroup loop(int nThreads, ThreadFactory threadFactory) {
        return new EpollEventLoopGroup(nThreads, threadFactory);
    }

    @Override
    public void applyOptions(ServerBootstrap bootstrap,
                             NetOptions options,
                             SocketAddress local) {
        final boolean isDomainSocket = local instanceof DomainSocketAddress;
        if (!isDomainSocket) {
            bootstrap.option(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
        }
        if (options.isTcpFastOpen()) {
            bootstrap.option(EpollChannelOption.TCP_FASTOPEN_CONNECT, true);
        }
        bootstrap.childOption(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
        bootstrap.childOption(EpollChannelOption.TCP_CORK, options.isTcpCork());
        super.applyOptions(bootstrap, options, local);
    }
}
