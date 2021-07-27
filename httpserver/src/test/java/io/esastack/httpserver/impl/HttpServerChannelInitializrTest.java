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

import esa.commons.ExceptionUtils;
import esa.commons.StringUtils;
import io.esastack.httpserver.H2OptionsConfigure;
import io.esastack.httpserver.HAProxyMode;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.esastack.httpserver.SslOptions;
import io.esastack.httpserver.utils.Constants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CompressorHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerChannelInitializrTest {

    @Test
    void testCloseChannelIfServerIsShutdown() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(new ServerRuntime("foo", ServerOptionsConfigure.newOpts()
                        .writeBufferHighWaterMark(WriteBufferWaterMark.DEFAULT.high() + 1)
                        .configured(), new HttpServerImpl.CloseFuture()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertFalse(channel.isActive());
    }

    @Test
    void testApplyWriteBufferHighWaterMark() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                        .writeBufferHighWaterMark(WriteBufferWaterMark.DEFAULT.high() + 1)
                        .configured()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertEquals(WriteBufferWaterMark.DEFAULT.high() + 1,
                channel.config().getWriteBufferHighWaterMark());
    }

    @Test
    void testApplyWriteBufferLowWaterMark() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                        .writeBufferLowWaterMark(WriteBufferWaterMark.DEFAULT.low() - 1)
                        .configured()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertEquals(WriteBufferWaterMark.DEFAULT.low() - 1,
                channel.config().getWriteBufferLowWaterMark());
    }

    @Test
    void testApplyWriteBufferWaterMark() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                        .writeBufferLowWaterMark(WriteBufferWaterMark.DEFAULT.low() - 1)
                        .writeBufferHighWaterMark(WriteBufferWaterMark.DEFAULT.high() + 1)
                        .configured()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertEquals(WriteBufferWaterMark.DEFAULT.low() - 1,
                channel.config().getWriteBufferLowWaterMark());
        assertEquals(WriteBufferWaterMark.DEFAULT.high() + 1,
                channel.config().getWriteBufferHighWaterMark());
    }

    @Test
    void testHAProxyDetectorInitialization() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                        .haProxy(HAProxyMode.AUTO)
                        .configured()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertNotNull(channel.pipeline().get(HAProxyDetector.class));
        assertEquals("HAProxyDetector", channel.pipeline().firstContext().name());
    }

    @Test
    void testHAProxyDecoderInitialization() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                        .haProxy(HAProxyMode.ON)
                        .configured()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertNotNull(channel.pipeline().get(HAProxyMessageDecoder.class));
        assertNotNull(channel.pipeline().get(HAProxyMessageHandler.class));
        assertEquals("HAProxyDecoder", channel.pipeline().firstContext().name());
        final List<String> names = channel.pipeline().names();
        assertTrue(names.indexOf("HAProxyDecoder") < names.indexOf("HAProxyMessageHandler"));
    }

    @Test
    void testHAProxyOffInitialization() {
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                        .haProxy(HAProxyMode.OFF)
                        .configured()),
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);
        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        assertNull(channel.pipeline().get(HAProxyMessageDecoder.class));
        assertNull(channel.pipeline().get(HAProxyMessageHandler.class));
    }

    @Test
    void testChannelInitAndActiveAndInactiveListener() {
        final AtomicBoolean init = new AtomicBoolean();
        final AtomicBoolean active = new AtomicBoolean();
        final AtomicBoolean inActive = new AtomicBoolean();
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .configured());
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        c -> init.set(true),
                        c -> active.set(true),
                        c -> inActive.set(true));

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);

        assertTrue(init.get());
        assertTrue(active.get());
        assertFalse(inActive.get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());

        channel.close();
        assertTrue(inActive.get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().activeConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttp1Connections());
    }

    @Test
    void testErrorOccurredInConnectionHandlerShouldBeIgnored() {
        final AtomicBoolean init = new AtomicBoolean();
        final AtomicBoolean active = new AtomicBoolean();
        final AtomicBoolean inActive = new AtomicBoolean();
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .configured());
        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        c -> {
                            init.set(true);
                            ExceptionUtils.throwException(new IllegalStateException());
                        },
                        c -> {
                            active.set(true);
                            ExceptionUtils.throwException(new IllegalStateException());
                        },
                        c -> {
                            inActive.set(true);
                            ExceptionUtils.throwException(new IllegalStateException());
                        });

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);

        assertTrue(init.get());
        assertTrue(active.get());
        assertFalse(inActive.get());
        channel.close();
        assertTrue(inActive.get());
    }

    @Test
    void testHttp1HandlerInitialization() {
        testHttp1HandlerInitialization(false, false, false, false);

        testHttp1HandlerInitialization(true, false, false, false);
        testHttp1HandlerInitialization(false, true, false, false);
        testHttp1HandlerInitialization(false, false, true, false);
        testHttp1HandlerInitialization(false, false, false, true);

        testHttp1HandlerInitialization(true, true, false, false);
        testHttp1HandlerInitialization(true, false, true, false);
        testHttp1HandlerInitialization(true, false, false, true);
        testHttp1HandlerInitialization(false, true, true, false);
        testHttp1HandlerInitialization(false, true, false, true);
        testHttp1HandlerInitialization(false, false, true, true);

        testHttp1HandlerInitialization(true, true, true, false);
        testHttp1HandlerInitialization(false, true, true, true);

        testHttp1HandlerInitialization(true, true, true, true);

    }

    @Test
    void testH1HandlerInitializationAfterH2cDetector() {
        testH1HandlerInitializationAfterH2cDetector(false, false, false, false);

        testH1HandlerInitializationAfterH2cDetector(true, false, false, false);
        testH1HandlerInitializationAfterH2cDetector(false, true, false, false);
        testH1HandlerInitializationAfterH2cDetector(false, false, true, false);
        testH1HandlerInitializationAfterH2cDetector(false, false, false, true);

        testH1HandlerInitializationAfterH2cDetector(true, true, false, false);
        testH1HandlerInitializationAfterH2cDetector(true, false, true, false);
        testH1HandlerInitializationAfterH2cDetector(true, false, false, true);
        testH1HandlerInitializationAfterH2cDetector(false, true, true, false);
        testH1HandlerInitializationAfterH2cDetector(false, true, false, true);
        testH1HandlerInitializationAfterH2cDetector(false, false, true, true);

        testH1HandlerInitializationAfterH2cDetector(true, true, true, false);
        testH1HandlerInitializationAfterH2cDetector(false, true, true, true);

        testH1HandlerInitializationAfterH2cDetector(true, true, true, true);
    }

    @Test
    void testH2cHandlerInitializationAfterH2cDetector() {
        testH2cHandlerInitializationAfterH2cDetector(false, false, false, false);
        testH2cHandlerInitializationAfterH2cDetector(false, false, false, false);

        testH2cHandlerInitializationAfterH2cDetector(true, false, false, false);
        testH2cHandlerInitializationAfterH2cDetector(false, true, false, false);
        testH2cHandlerInitializationAfterH2cDetector(false, false, true, false);
        testH2cHandlerInitializationAfterH2cDetector(false, false, false, true);

        testH2cHandlerInitializationAfterH2cDetector(true, true, false, false);
        testH2cHandlerInitializationAfterH2cDetector(true, false, true, false);
        testH2cHandlerInitializationAfterH2cDetector(true, false, false, true);
        testH2cHandlerInitializationAfterH2cDetector(false, true, true, false);
        testH2cHandlerInitializationAfterH2cDetector(false, true, false, true);
        testH2cHandlerInitializationAfterH2cDetector(false, false, true, true);

        testH2cHandlerInitializationAfterH2cDetector(true, true, true, false);
        testH2cHandlerInitializationAfterH2cDetector(false, true, true, true);

        testH2cHandlerInitializationAfterH2cDetector(true, true, true, true);
    }

    @Test
    void testUpgrade() {
        testUpgrade(false, false, false, false);

        testUpgrade(true, false, false, false);
        testUpgrade(false, true, false, false);
        testUpgrade(false, false, true, false);
        testUpgrade(false, false, false, true);

        testUpgrade(true, true, false, false);
        testUpgrade(true, false, true, false);
        testUpgrade(true, false, false, true);
        testUpgrade(false, true, true, false);
        testUpgrade(false, true, false, true);
        testUpgrade(false, false, true, true);

        testUpgrade(true, true, true, false);
        testUpgrade(false, true, true, true);

        testUpgrade(true, true, true, true);
    }

    @Test
    void testUpgradeFailed() {
        testUnsupportedUpgradeProtocol(false, false, false, false);

        testUnsupportedUpgradeProtocol(true, false, false, false);
        testUnsupportedUpgradeProtocol(false, true, false, false);
        testUnsupportedUpgradeProtocol(false, false, true, false);
        testUnsupportedUpgradeProtocol(false, false, false, true);

        testUnsupportedUpgradeProtocol(true, true, false, false);
        testUnsupportedUpgradeProtocol(true, false, true, false);
        testUnsupportedUpgradeProtocol(true, false, false, true);
        testUnsupportedUpgradeProtocol(false, true, true, false);
        testUnsupportedUpgradeProtocol(false, true, false, true);
        testUnsupportedUpgradeProtocol(false, false, true, true);

        testUnsupportedUpgradeProtocol(true, true, true, false);
        testUnsupportedUpgradeProtocol(false, true, true, true);

        testUnsupportedUpgradeProtocol(true, true, true, true);


        testUpgradeError(false, false, false, false, null);

        testUpgradeError(true, false, false, false, "");
        testUpgradeError(false, true, false, false, "123");
        testUpgradeError(false, false, true, false, "foo");
        testUpgradeError(false, false, false, true, null);

        testUpgradeError(true, true, false, false, "abcd");
        testUpgradeError(true, false, true, false, "abcd");
        testUpgradeError(true, false, false, true, "abcd");
        testUpgradeError(false, true, true, false, "abcd");
        testUpgradeError(false, true, false, true, "abcd");
        testUpgradeError(false, false, true, true, "abcd");

        testUpgradeError(true, true, true, false, "abcd");
        testUpgradeError(false, true, true, true, "abcd");

        testUpgradeError(true, true, true, true, "abcd");
    }

    @Test
    void testSslDetect() throws CertificateException {
        testSslDetectFailedWithHttp2Disabled(false, false, false, false);

        testSslDetectFailedWithHttp2Disabled(true, false, false, false);
        testSslDetectFailedWithHttp2Disabled(false, true, false, false);
        testSslDetectFailedWithHttp2Disabled(false, false, true, false);
        testSslDetectFailedWithHttp2Disabled(false, false, false, true);

        testSslDetectFailedWithHttp2Disabled(true, true, false, false);
        testSslDetectFailedWithHttp2Disabled(true, false, true, false);
        testSslDetectFailedWithHttp2Disabled(true, false, false, true);
        testSslDetectFailedWithHttp2Disabled(false, true, true, false);
        testSslDetectFailedWithHttp2Disabled(false, true, false, true);
        testSslDetectFailedWithHttp2Disabled(false, false, true, true);

        testSslDetectFailedWithHttp2Disabled(true, true, true, false);
        testSslDetectFailedWithHttp2Disabled(false, true, true, true);

        testSslDetectFailedWithHttp2Disabled(true, true, true, true);

        // h2 enabled
        testSslDetectFailedWithHttp2Enabled(false, false, false, false);

        testSslDetectFailedWithHttp2Enabled(true, false, false, false);
        testSslDetectFailedWithHttp2Enabled(false, true, false, false);
        testSslDetectFailedWithHttp2Enabled(false, false, true, false);
        testSslDetectFailedWithHttp2Enabled(false, false, false, true);

        testSslDetectFailedWithHttp2Enabled(true, true, false, false);
        testSslDetectFailedWithHttp2Enabled(true, false, true, false);
        testSslDetectFailedWithHttp2Enabled(true, false, false, true);
        testSslDetectFailedWithHttp2Enabled(false, true, true, false);
        testSslDetectFailedWithHttp2Enabled(false, true, false, true);
        testSslDetectFailedWithHttp2Enabled(false, false, true, true);

        testSslDetectFailedWithHttp2Enabled(true, true, true, false);
        testSslDetectFailedWithHttp2Enabled(false, true, true, true);

        testSslDetectFailedWithHttp2Enabled(true, true, true, true);
    }

    private static void testUnsupportedUpgradeProtocol(boolean logging,
                                                       boolean idle,
                                                       boolean compress,
                                                       boolean decompress) {
        final CustomHandler customHandler = new CustomHandler();

        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .h2(H2OptionsConfigure.newOpts()
                        .enabled(true)
                        .configured())
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(H2cDetector.class));
        assertTrue(channel.pipeline().names().indexOf("H2cDetector") > loggingIdx);

        final String upgradeString = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: unknown\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(upgradeString.getBytes()));

        assertNotNull(channel.pipeline().get(HttpServerUpgradeHandler.class));
        assertNotNull(channel.pipeline().get("Splitter"));
        testH1Handlers(logging, idle, compress, decompress, runtime, channel);

        channel.flushOutbound();

        final ByteBuf rejectMessage = channel.readOutbound();
        final String expectedHttpResponse = "HTTP/1.1 200 OK\r\n" +
                "content-length: 0\r\n\r\n";
        assertEquals(expectedHttpResponse, rejectMessage.toString(CharsetUtil.US_ASCII));

        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());
        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(0L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testUpgradeError(boolean logging,
                                         boolean idle,
                                         boolean compress,
                                         boolean decompress,
                                         String content) {
        final CustomHandler customHandler = new CustomHandler();

        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .h2(H2OptionsConfigure.newOpts()
                        .enabled(true)
                        .configured())
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> {
                            r.aggregate(true);
                            r.onEnd(p -> {
                                r.response().end(r.aggregated().body().retain());
                                return p.setSuccess(null);
                            });
                        },
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(H2cDetector.class));
        assertTrue(channel.pipeline().names().indexOf("H2cDetector") > loggingIdx);

        final String upgradeString = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                "Content-Length: " + StringUtils.emptyIfNull(content).length() + "\r\n\r\n" +
                StringUtils.emptyIfNull(content);

        channel.writeInbound(Unpooled.copiedBuffer(upgradeString.getBytes()));

        assertNotNull(channel.pipeline().get(HttpServerUpgradeHandler.class));
        assertNotNull(channel.pipeline().get("Splitter"));
        testH1Handlers(logging, idle, compress, decompress, runtime, channel);

        channel.flushOutbound();

        final ByteBuf rejectMessage = channel.readOutbound();
        final String expectedHttpResponse = "HTTP/1.1 200 OK\r\n" +
                "content-length: " + StringUtils.emptyIfNull(content).length() + "\r\n\r\n" +
                StringUtils.emptyIfNull(content);
        assertEquals(expectedHttpResponse, rejectMessage.toString(CharsetUtil.US_ASCII));

        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());
        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(0L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testUpgrade(boolean logging,
                                    boolean idle,
                                    boolean compress,
                                    boolean decompress) {
        final CustomHandler customHandler = new CustomHandler();

        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .h2(H2OptionsConfigure.newOpts()
                        .enabled(true)
                        .configured())
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(H2cDetector.class));
        assertTrue(channel.pipeline().names().indexOf("H2cDetector") > loggingIdx);

        final String upgradeString = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: Upgrade, HTTP2-Settings\r\n" +
                "Upgrade: h2c\r\n" +
                "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(upgradeString.getBytes()));
        testH2cHandler(logging, idle, compress, decompress, runtime, channel);
        assertNull(channel.pipeline().get("Splitter"));

        channel.flushOutbound();
        final ByteBuf upgradeMessage = channel.readOutbound();
        final String expectedHttpResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
                "connection: upgrade\r\n" +
                "upgrade: h2c\r\n\r\n";
        assertEquals(expectedHttpResponse, upgradeMessage.toString(CharsetUtil.US_ASCII));
        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testHttp1HandlerInitialization(boolean logging,
                                                       boolean idle,
                                                       boolean compress,
                                                       boolean decompress) {
        final CustomHandler customHandler = new CustomHandler();

        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        testH1Handlers(logging, idle, compress, decompress, runtime, channel);

        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(0L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testSslDetectFailedWithHttp2Disabled(boolean logging,
                                                             boolean idle,
                                                             boolean compress,
                                                             boolean decompress) throws CertificateException {
        final CustomHandler customHandler = new CustomHandler();
        final SslOptions ssl = new SslOptions();
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        final SelfSignedCertificate certificate = new SelfSignedCertificate();
        ssl.setCertificate(certificate.certificate());
        ssl.setPrivateKey(certificate.privateKey());
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .ssl(ssl)
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(runtime.options().getSsl(), false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);

        assertNull(channel.pipeline().get(LoggingHandler.class));
        assertNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(SslDetector.class));

        final String plainRequestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 8\r\n" +
                "\r\n" +
                "12345678";
        channel.writeInbound(Unpooled.copiedBuffer(plainRequestStr.getBytes()));
        testH1Handlers(logging, idle, compress, decompress, runtime, channel);
        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(0L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testSslDetectFailedWithHttp2Enabled(boolean logging,
                                                            boolean idle,
                                                            boolean compress,
                                                            boolean decompress) throws CertificateException {
        final CustomHandler customHandler = new CustomHandler();
        final SslOptions ssl = new SslOptions();
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        final SelfSignedCertificate certificate = new SelfSignedCertificate();
        ssl.setCertificate(certificate.certificate());
        ssl.setPrivateKey(certificate.privateKey());
        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .ssl(ssl)
                .h2(H2OptionsConfigure.newOpts()
                        .enabled(true)
                        .configured())
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(runtime.options().getSsl(), false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);

        assertNull(channel.pipeline().get(LoggingHandler.class));
        assertNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(SslDetector.class));

        final String plainRequestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 8\r\n" +
                "\r\n" +
                "12345678";
        channel.writeInbound(Unpooled.copiedBuffer(plainRequestStr.getBytes()));

        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
        }
        assertNotNull(channel.pipeline().get(CustomHandler.class));
        assertNull(channel.pipeline().get(H2cDetector.class));
        assertNotNull(channel.pipeline().get(HttpServerUpgradeHandler.class));
        assertNotNull(channel.pipeline().get("Splitter"));
        testH1Handlers(logging, idle, compress, decompress, runtime, channel);


        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());

        channel.flushOutbound();

        final ByteBuf rejectMessage = channel.readOutbound();
        final String expectedHttpResponse = "HTTP/1.1 200 OK\r\n" +
                "content-length: 0\r\n\r\n";
        assertEquals(expectedHttpResponse, rejectMessage.toString(CharsetUtil.US_ASCII));

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(0L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testH1HandlerInitializationAfterH2cDetector(boolean logging,
                                                                    boolean idle,
                                                                    boolean compress,
                                                                    boolean decompress) {
        final CustomHandler customHandler = new CustomHandler();

        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .h2(H2OptionsConfigure.newOpts()
                        .enabled(true)
                        .configured())
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(H2cDetector.class));
        assertTrue(channel.pipeline().names().indexOf("H2cDetector") > loggingIdx);

        final String reqStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 8\r\n" +
                "\r\n" +
                "12345678";
        channel.writeInbound(Unpooled.copiedBuffer(reqStr.getBytes()));

        assertNull(channel.pipeline().get(H2cDetector.class));
        assertNotNull(channel.pipeline().get(HttpServerUpgradeHandler.class));
        assertNotNull(channel.pipeline().get("Splitter"));
        testH1Handlers(logging, idle, compress, decompress, runtime, channel);

        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(1L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(0L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(1L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(0L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testH2cHandlerInitializationAfterH2cDetector(boolean logging,
                                                                     boolean idle,
                                                                     boolean compress,
                                                                     boolean decompress) {
        final CustomHandler customHandler = new CustomHandler();

        final ServerRuntime runtime = Helper.serverRuntime(ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .logging(logging ? LogLevel.DEBUG : null)
                .idleTimeoutSeconds(idle ? 10 : -1)
                .compress(compress)
                .decompress(decompress)
                .channelHandlers(Collections.singleton(customHandler))
                .h2(H2OptionsConfigure.newOpts()
                        .enabled(true)
                        .maxFrameSize(MAX_FRAME_SIZE_LOWER_BOUND)
                        .maxReservedStreams(logging ? 1 : 0)
                        .configured())
                .configured());

        final HttpServerChannelInitializr initializr =
                new HttpServerChannelInitializr(runtime,
                        new SslHelper(null, false),
                        r -> r.response().end(),
                        null,
                        null,
                        null);

        final EmbeddedChannel channel = new EmbeddedChannel(initializr);
        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));
        assertNotNull(channel.pipeline().get(H2cDetector.class));
        assertTrue(channel.pipeline().names().indexOf("H2cDetector") > loggingIdx);

        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());
        assertNull(channel.pipeline().get(H2cDetector.class));
        testH2cHandler(logging, idle, compress, decompress, runtime, channel);

        assertEquals(Constants.SCHEMA_HTTP, channel.attr(Constants.SCHEME).get());

        assertEquals(1L, runtime.metrics().connection().connectionCount());
        assertEquals(0L, runtime.metrics().connection().http1ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().http2ConnectionCount());
        assertEquals(1L, runtime.metrics().connection().httpConnectionCount());
        assertEquals(0L, runtime.metrics().connection().httpsConnectionCount());
        assertEquals(1L, runtime.metrics().connection().activeConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttp1Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttp2Connections());
        assertEquals(1L, runtime.metrics().connection().activeHttpConnections());
        assertEquals(0L, runtime.metrics().connection().activeHttpsConnections());
    }

    private static void testH1Handlers(boolean logging,
                                       boolean idle,
                                       boolean compress,
                                       boolean decompress,
                                       ServerRuntime runtime,
                                       EmbeddedChannel channel) {
        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));

        assertNotNull(channel.pipeline().get(RequestDecoder.class));
        assertNotNull(channel.pipeline().get(HttpResponseEncoder.class));
        final int requestDecoderIdx = channel.pipeline().names().indexOf("RequestDecoder");
        final int responseEncoderIdx = channel.pipeline().names().indexOf("ResponseEncoder");
        assertTrue(requestDecoderIdx > loggingIdx);
        assertTrue(responseEncoderIdx > requestDecoderIdx);

        int decompressorIdx = -1;
        if (decompress) {
            assertNotNull(channel.pipeline().get(HttpContentDecompressor.class));
            decompressorIdx = channel.pipeline().names().indexOf("Decompressor");
            assertTrue(decompressorIdx > responseEncoderIdx);
        }

        int chunkedWriterIdx = -1;
        if (compress) {
            assertNotNull(channel.pipeline().get(ChunkedWriteHandler.class));
            assertNotNull(channel.pipeline().get(HttpContentCompressor.class));
            chunkedWriterIdx = channel.pipeline().names().indexOf("ChunkedWriter");
            assertTrue(chunkedWriterIdx > decompressorIdx);
            assertTrue(chunkedWriterIdx > responseEncoderIdx);
            assertTrue(channel.pipeline().names().indexOf("Compressor") < chunkedWriterIdx);
        }
        int idleIdx = -1;
        if (idle) {
            final IdleStateHandler h = channel.pipeline().get(IdleStateHandler.class);
            assertNotNull(h);
            assertEquals(runtime.options().getIdleTimeoutSeconds() * 1000L, h.getAllIdleTimeInMillis());
            assertEquals(0, h.getReaderIdleTimeInMillis());
            assertEquals(0, h.getWriterIdleTimeInMillis());

            idleIdx = channel.pipeline().names().indexOf("IdleHandler");
            assertTrue(idleIdx > decompressorIdx);
            assertTrue(idleIdx > chunkedWriterIdx);
            assertTrue(idleIdx > responseEncoderIdx);
        }

        assertNotNull(channel.pipeline().get(Http1Handler.class));
        assertTrue(channel.pipeline().names().indexOf("H1Handler") > idleIdx);
        assertTrue(channel.pipeline().names().indexOf("H1Handler") > chunkedWriterIdx);
        assertTrue(channel.pipeline().names().indexOf("H1Handler") > responseEncoderIdx);
    }

    private static void testH2cHandler(boolean logging,
                                       boolean idle,
                                       boolean compress,
                                       boolean decompress,
                                       ServerRuntime runtime,
                                       EmbeddedChannel channel) {

        int loggingIdx = -1;
        if (logging) {
            assertNotNull(channel.pipeline().get(LoggingHandler.class));
            assertEquals("Logging", channel.pipeline().firstContext().name());
            loggingIdx = channel.pipeline().names().indexOf("Logging");
        }

        assertNotNull(channel.pipeline().get(CustomHandler.class));

        int idleIdx = -1;
        if (idle) {
            final IdleStateHandler h = channel.pipeline().get(IdleStateHandler.class);
            assertNotNull(h);
            assertEquals(runtime.options().getIdleTimeoutSeconds() * 1000L, h.getAllIdleTimeInMillis());
            assertEquals(0, h.getReaderIdleTimeInMillis());
            assertEquals(0, h.getWriterIdleTimeInMillis());

            idleIdx = channel.pipeline().names().indexOf("IdleHandler");
            assertTrue(idleIdx > loggingIdx);
        }


        final Http2ConnectionChunkHandler handler = channel.pipeline().get(Http2ConnectionChunkHandler.class);
        assertNotNull(handler);
        assertTrue(channel.pipeline().names().indexOf("H2Handler") > idleIdx);
        assertTrue(channel.pipeline().names().indexOf("H2Handler") > loggingIdx);

        if (compress) {
            assertTrue(handler.encoder() instanceof CompressorHttp2ConnectionEncoder);
        }
        if (decompress) {
            assertTrue(handler.decoder().frameListener() instanceof DelegatingDecompressorFrameListener);
        }
        if (logging) {
            assertTrue(handler.encoder().frameWriter() instanceof Http2OutboundFrameLogger);
        }
        assertEquals(runtime.options().getH2().getGracefulShutdownTimeoutMillis(),
                handler.gracefulShutdownTimeoutMillis());
    }

    private static class CustomHandler extends ChannelInboundHandlerAdapter {
    }


}
