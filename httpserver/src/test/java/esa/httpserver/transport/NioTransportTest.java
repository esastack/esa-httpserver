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
package esa.httpserver.transport;

import esa.httpserver.NetOptions;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NioTransportTest {

    @Test
    void testServerChannelFactoryCreation() {
        final ChannelFactory<ServerChannel> factory = NioTransport.INSTANCE
                .serverChannelFactory(new InetSocketAddress(8080));

        assertTrue(factory.newChannel() instanceof NioServerSocketChannel);
        assertThrows(IllegalArgumentException.class, () -> NioTransport.INSTANCE
                .serverChannelFactory(new DomainSocketAddress("/tmp.sock")));
    }

    @Test
    void testEventLoopGroupCreation() {
        final EventLoopGroup loop = NioTransport.INSTANCE
                .loop(1, new DefaultThreadFactory("foo"));

        assertTrue(loop instanceof NioEventLoopGroup);
    }

    @Test
    void testApplyOptions() throws InterruptedException {
        final NetOptions options = new NetOptions();
        options.setSoBacklog(10);
        options.setReuseAddress(false);
        options.setSoKeepalive(true);
        options.setTcpNoDelay(true);
        options.setSoRcvbuf(100);
        options.setSoSendbuf(100);
        options.setSoLinger(1);
        options.setOptions(Collections.singletonMap(ChannelOption.AUTO_READ, false));
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Channel> ch = new AtomicReference<>();
        final EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            ServerBootstrap sb = new ServerBootstrap();
            NioTransport.INSTANCE.applyOptions(sb, options, new LocalAddress("foo"));
            sb.channel(NioServerSocketChannel.class)
                    .group(group)
                    .childHandler(new ChannelInboundHandlerAdapter())
                    .handler(new ChannelHandlerAdapter() {
                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) {
                            ch.set(ctx.channel());
                            latch.countDown();
                        }
                    });
            sb.register().syncUninterruptibly();
            latch.await();
            final Channel channel = ch.get();
            assertNotNull(channel);

            assertEquals(10, channel.config().getOption(ChannelOption.SO_BACKLOG));
            assertFalse(channel.config().getOption(ChannelOption.SO_REUSEADDR));
            assertFalse(channel.config().getOption(ChannelOption.AUTO_READ));
        } finally {
            group.shutdownGracefully();
        }
    }

}
