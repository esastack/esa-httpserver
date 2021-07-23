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
package io.esastack.httpserver.it;

import esa.commons.NetworkUtils;
import io.esastack.httpserver.HttpServer;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.esastack.httpserver.SslOptionsConfigure;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpsTest {

    @Test
    void testSsl() throws Exception {
        final SelfSignedCertificate cert = new SelfSignedCertificate();
        final int port = NetworkUtils.selectRandomPort();
        final CompositeByteBuf buf = Unpooled.compositeBuffer();
        final CompletableFuture<Boolean> ended = new CompletableFuture<>();
        final HttpServer server = HttpServer.create(ServerOptionsConfigure.newOpts()
                .logging(LogLevel.INFO)
                .ioThreads(1)
                .ssl(SslOptionsConfigure.newOpts()
                        .certificate(cert.certificate())
                        .privateKey(cert.privateKey())
                        .configured())
                .configured())
                .handle(req -> {
                    req.onData(data -> buf.writeBytes(data.retain()))
                            .onEnd(p -> {
                                ended.complete(true);
                                return p.setSuccess(null);
                            });
                    req.response().write("12".getBytes());
                    req.response().write("34".getBytes());
                    req.response().write("56".getBytes());
                    req.response().end("78".getBytes());
                });

        server.listen(port);

        EventLoopGroup group = new NioEventLoopGroup(2);
        try {
            // Configure the client.
            Bootstrap b = new Bootstrap();
            final SslContext sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            final CompletableFuture<String> ret = new CompletableFuture<>();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(sslCtx.newHandler(ch.alloc()));
                            pipeline.addLast(new HttpClientCodec());
                            pipeline.addLast(new HttpObjectAggregator(1024));
                            pipeline.addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                                    ret.complete(msg.content().toString(StandardCharsets.UTF_8));
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                                        throws Exception {
                                    ret.completeExceptionally(cause);
                                    super.exceptionCaught(ctx, cause);
                                }
                            });
                        }
                    });

            final String msg = "That is what it is.";

            final DefaultFullHttpRequest request =
                    new DefaultFullHttpRequest(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                            HttpMethod.POST,
                            "/foo",
                            Unpooled.copiedBuffer(msg.getBytes(StandardCharsets.UTF_8)));
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH,
                    request.content().readableBytes());
            b.connect(new InetSocketAddress(port))
                    .sync()
                    .channel()
                    .writeAndFlush(request)
                    .channel()
                    .closeFuture()
                    .sync();
            assertEquals("12345678", ret.join());

        } finally {
            group.shutdownGracefully();
            server.close();
            buf.release();
        }
    }

}
