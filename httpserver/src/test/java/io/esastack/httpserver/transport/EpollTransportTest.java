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

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EpollTransportTest {

    @Test
    void testServerChannelFactoryCreation() {
        final EpollTransport transport = new EpollTransport();
        final ChannelFactory<ServerChannel> factory1 = transport
                .serverChannelFactory(new InetSocketAddress(8080));
        final ChannelFactory<ServerChannel> factory2 = transport
                .serverChannelFactory(new DomainSocketAddress("tmp.sock"));

        assumeTrue(Epoll.isAvailable());

        assertTrue(factory1.newChannel() instanceof EpollServerSocketChannel);
        assertTrue(factory2.newChannel() instanceof EpollServerDomainSocketChannel);
    }

    @Test
    void testEventLoopGroupCreation() {
        assumeTrue(Epoll.isAvailable());
        final EpollTransport transport = new EpollTransport();
        final EventLoopGroup loop = transport
                .loop(1, new DefaultThreadFactory("foo"));

        assertTrue(loop instanceof EpollEventLoopGroup);
    }

}
