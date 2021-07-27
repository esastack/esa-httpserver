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

import esa.commons.http.HttpVersion;
import io.esastack.httpserver.H2Options;
import io.esastack.httpserver.HAProxyMode;
import io.esastack.httpserver.ServerOptions;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.utils.Constants;
import io.esastack.httpserver.utils.Loggers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AsciiString;

import java.util.List;
import java.util.function.Consumer;

import static io.netty.handler.ssl.ApplicationProtocolNames.HTTP_2;

final class HttpServerChannelInitializr extends ChannelInitializer<Channel> {

    private final ServerRuntime runtime;
    private final SslHelper sslHelper;
    private final Consumer<RequestHandle> handler;
    private final Consumer<Channel> onConnectionInit;
    private final Consumer<Channel> onConnected;
    private final Consumer<Channel> onDisconnected;

    HttpServerChannelInitializr(ServerRuntime runtime,
                                SslHelper sslHelper,
                                Consumer<RequestHandle> handler,
                                Consumer<Channel> onConnectionInit,
                                Consumer<Channel> onConnected,
                                Consumer<Channel> onDisconnected) {
        this.runtime = runtime;
        this.sslHelper = sslHelper;
        this.handler = handler;
        this.onConnectionInit = onConnectionInit;
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
    }

    @Override
    protected void initChannel(Channel ch) {

        if (!runtime.isRunning()) {
            ch.close();
            return;
        }

        if (onConnectionInit != null) {
            try {
                onConnectionInit.accept(ch);
            } catch (Throwable t) {
                Loggers.logger().warn("Error while processing onConnectionInit()", t);
            }
        }

        final ChannelPipeline pipeline = ch.pipeline();
        // options for each accepted channel
        applyChannelOptions(ch);

        // Channel Active event hook
        // we could not use channelActive() event in current class, because the timing of
        // ChannelInitializer#initChannel(xx) which is relying on channelRegistered() event proceeds the
        // channelActive() event.
        if (Loggers.logger().isDebugEnabled() || onConnected != null || runtime.metrics().enabled()) {
            pipeline.addLast(new OnChannelActiveHandler(ctx -> {
                if (Loggers.logger().isDebugEnabled()) {
                    Loggers.logger().debug("Received connection {}", ctx.channel());
                }
                if (onConnected != null) {
                    try {
                        onConnected.accept(ch);
                    } catch (Throwable t) {
                        Loggers.logger().warn("Error while processing onConnected()", t);
                    }
                }
                ctx.channel().closeFuture().addListener(f -> {
                    if (Loggers.logger().isDebugEnabled()) {
                        Loggers.logger().debug("Connection Disconnected {}", ctx.channel());
                    }
                    if (onDisconnected != null) {
                        try {
                            onDisconnected.accept(ctx.channel());
                        } catch (Throwable t) {
                            Loggers.logger().warn("Error while processing onDisconnected()", t);
                        }
                    }
                    runtime.metrics().reportDisconnect(ch);
                });
            }));
        }

        // HAProxy protocol
        final HAProxyMode haProxyMode = options().getHaProxy();
        if (haProxyMode != null && !HAProxyMode.OFF.equals(haProxyMode)) {
            if (HAProxyMode.ON.equals(haProxyMode)) {
                pipeline.addFirst("HAProxyDecoder", new HAProxyMessageDecoder());
                pipeline.addAfter("HAProxyDecoder",
                        "HAProxyMessageHandler", new HAProxyMessageHandler());
            } else {
                // AUTO
                pipeline.addFirst("HAProxyDetector", new HAProxyDetector());
            }
        }

        if (sslHelper.isSsl()) {
            pipeline.addLast("SslDetector",
                    new SslDetector(sslHelper, (isSsl, channel, err) -> {
                        if (err != null) {
                            // TODO: better way?
                            Loggers.logger().error("{} TLS handshake failed", channel, err);
                            channel.close();
                            return;
                        }
                        if (isSsl) {
                            channel.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTPS);
                        } else {
                            channel.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTP);
                        }

                        // add custom channel handlers before adding http codec handlers
                        addCustomHandlers(channel.pipeline());

                        // add http codec handlers
                        SslHandler sslHandler;
                        if (sslHelper.isSsl()
                                && ((sslHandler = channel.pipeline().get(SslHandler.class)) != null)
                                && HTTP_2.equals(sslHandler.applicationProtocol())) {
                            addHttp2Handlers(channel);
                        } else if (options().getH2() != null && options().getH2().isEnabled()) {
                            addUpgradeHandler(channel);
                        } else {
                            addHttp1Handlers(pipeline);
                            addHttp1Handler(ch);
                        }
                    }));
        } else {
            ch.attr(Constants.SCHEME).set(Constants.SCHEMA_HTTP);
            addCustomHandlers(pipeline);
            if (options().getH2() != null && options().getH2().isEnabled()) {
                addH1OrH2cHandler(pipeline);
            } else {
                addHttp1Handlers(pipeline);
                addHttp1Handler(ch);
            }
        }
    }

    private void applyChannelOptions(Channel ch) {
        final int high = options().getWriteBufferHighWaterMark();
        final int low = options().getWriteBufferLowWaterMark();
        if (high > 0) {
            if (low > 0) {
                ch.config().setWriteBufferWaterMark(new WriteBufferWaterMark(low, high));
            } else {
                ch.config().setWriteBufferHighWaterMark(high);
            }
        } else if (low > 0) {
            ch.config().setWriteBufferLowWaterMark(low);
        }
    }

    private void addCustomHandlers(ChannelPipeline pipeline) {
        if (options().getLogging() != null) {
            pipeline.addFirst("Logging", new LoggingHandler(options().getLogging()));
        }
        // custom channel handlers
        List<ChannelHandler> handlers = options().getChannelHandlers();
        if (handlers != null && !handlers.isEmpty()) {
            pipeline.addLast(handlers.toArray(new ChannelHandler[0]));
        }
    }

    private void addHttp1Handlers(ChannelPipeline pipeline) {
        pipeline.addLast("RequestDecoder", new RequestDecoder(options().getMaxInitialLineLength(),
                options().getMaxHeaderSize(), options().getMaxChunkSize()));
        pipeline.addLast("ResponseEncoder", new HttpResponseEncoder());
        if (options().isDecompress()) {
            pipeline.addLast("Decompressor", new HttpContentDecompressor());
        }
        if (options().isCompress()) {
            pipeline.addLast("Compressor", new HttpContentCompressor(options().getCompressionLevel()));
        }

        if (sslHelper.isSsl() || options().isCompress()) {
            // for large file.
            pipeline.addLast("ChunkedWriter", new ChunkedWriteHandler());
        }
        if (options().getIdleTimeoutSeconds() > 0) {
            // idle connection handler
            pipeline.addLast("IdleHandler",
                    new IdleStateHandler(0, 0,
                            options().getIdleTimeoutSeconds()));
        }
    }

    private void addHttp1Handler(Channel ch) {
        ch.pipeline().addLast("H1Handler", new Http1Handler(runtime, handler));
        runtime.metrics().reportConnect(ch, HttpVersion.HTTP_1_1);
    }

    private void addHttp2Handlers(Channel ch) {
        if (options().getIdleTimeoutSeconds() > 0) {
            // idle connection handler
            ch.pipeline().addLast("IdleHandler",
                    new IdleStateHandler(0, 0,
                            options().getIdleTimeoutSeconds()));
        }
        ch.pipeline().addLast("H2Handler", buildHttp2ConnectionHandler());
        runtime.metrics().reportConnect(ch, HttpVersion.HTTP_2);
    }

    private void addH1OrH2cHandler(ChannelPipeline pipeline) {
        pipeline.addLast("H2cDetector", new H2cDetector((ctx, h2c) -> {
            if (h2c) {
                addHttp2Handlers(ctx.channel());
            } else {
                addUpgradeHandler(ctx.channel());
            }
        }));
    }

    private void addUpgradeHandler(Channel channel) {
        addHttp1Handlers(channel.pipeline());
        channel.pipeline().addLast("UpgradeHandler", new HttpServerUpgradeHandler(fromCtx -> {
            // remove http1 handlers
            fromCtx.pipeline().remove("RequestDecoder");
            fromCtx.pipeline().remove("ResponseEncoder");
            if (options().isDecompress()) {
                fromCtx.pipeline().remove("Decompressor");
            }
            if (options().isCompress()) {
                fromCtx.pipeline().remove("Compressor");
            }
            if (sslHelper.isSsl() || options().isCompress()) {
                fromCtx.pipeline().remove("ChunkedWriter");
            }
            fromCtx.pipeline().remove("H1Handler");
            fromCtx.pipeline().remove("Splitter");
            runtime.metrics().reportUpgrade(fromCtx.channel());
        }, protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                return new Http2ServerUpgradeCodec("H2Handler", buildHttp2ConnectionHandler());
            } else {
                if (Loggers.logger().isDebugEnabled()) {
                    Loggers.logger().debug("Unsupported upgrade protocol: {}", protocol);
                }
                return null;
            }
        }, maxContentLength()));

        channel.pipeline().addLast("Splitter",
                new MessageToMessageDecoder<FullHttpRequest>() {

                    @Override
                    protected void decode(ChannelHandlerContext ctx,
                                          FullHttpRequest msg,
                                          List<Object> out) {
                        out.add(msg.retain());
                        if (msg.content().isReadable()) {
                            out.add(new AggregatedLastHttpContent(msg.content().retain(),
                                    msg.trailingHeaders()));
                        } else {
                            out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                        }
                    }
                });

        addHttp1Handler(channel);
    }

    private int maxContentLength() {
        final long configured = runtime.options().getMaxContentLength();
        return (configured > 0L && configured < Integer.MAX_VALUE) ? (int) configured : Integer.MAX_VALUE;
    }

    private Http2ConnectionChunkHandler buildHttp2ConnectionHandler() {
        final H2Options h2Options = options().getH2();
        assert h2Options != null;
        final Http2Connection connection;
        if (h2Options.getMaxReservedStreams() > 0) {
            connection = new DefaultHttp2Connection(true, h2Options.getMaxReservedStreams());
        } else {
            connection = new DefaultHttp2Connection(true);
        }

        Http2FrameWriter writer = new DefaultHttp2FrameWriter();
        Http2FrameReader reader = new DefaultHttp2FrameReader();
        if (Http2CodecUtil.isMaxFrameSizeValid(h2Options.getMaxFrameSize())) {
            try {
                ((DefaultHttp2FrameWriter) writer).maxFrameSize(h2Options.getMaxFrameSize());
                ((DefaultHttp2FrameReader) reader).maxFrameSize(h2Options.getMaxFrameSize());
            } catch (Http2Exception ignored) {
            }
        }

        if (options().getLogging() != null) {
            reader = new Http2InboundFrameLogger(reader, new Http2FrameLogger(options().getLogging()));
            writer = new Http2OutboundFrameLogger(writer, new Http2FrameLogger(options().getLogging()));
        }

        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        if (options().isCompress()) {
            encoder = new CompressorHttp2ConnectionEncoder(encoder, options().getCompressionLevel(),
                    CompressorHttp2ConnectionEncoder.DEFAULT_WINDOW_BITS,
                    CompressorHttp2ConnectionEncoder.DEFAULT_MEM_LEVEL);
        }

        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);
        final Http2Handler frameListener = new Http2Handler(runtime, encoder, handler);
        final Http2ConnectionChunkHandlerBuilder builder = new Http2ConnectionChunkHandlerBuilder()
                .codec(decoder, encoder)
                .frameListener(options().isDecompress()
                        ? new DelegatingDecompressorFrameListener(connection, frameListener)
                        : frameListener);

        if (h2Options.getGracefulShutdownTimeoutMillis() > 0L) {
            builder.gracefulShutdownTimeoutMillis(h2Options.getGracefulShutdownTimeoutMillis());
        }
        return builder.build();
    }

    private ServerOptions options() {
        return runtime.options();
    }
}
