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

import java.net.SocketAddress;
import java.util.concurrent.ThreadFactory;

/**
 * An interface defines the traits of transport, such as NIO, Epoll.
 */
public interface Transport {

    /**
     * Creates a server {@link ChannelFactory}.
     *
     * @param local address to bind
     *
     * @return channel factory
     */
    ChannelFactory<ServerChannel> serverChannelFactory(SocketAddress local);

    /**
     * Creates a {@link EventLoopGroup}
     *
     * @param nThreads      threads count
     * @param threadFactory thread factory to use
     *
     * @return instance of {@link EventLoopGroup}
     */
    EventLoopGroup loop(int nThreads, ThreadFactory threadFactory);

    /**
     * Applies the given {@code options} to the {@code bootstrap}.
     *
     * @param bootstrap bootstrap
     * @param options options
     * @param local address to bind
     */
    void applyOptions(ServerBootstrap bootstrap, NetOptions options, SocketAddress local);

}
