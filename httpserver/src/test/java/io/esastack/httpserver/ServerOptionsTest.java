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
package io.esastack.httpserver;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ServerOptionsTest {

    @Test
    void testConfigure() {
        final ServerOptions options = ServerOptionsConfigure.newOpts()
                .daemon(true)
                .bossThreads(1)
                .ioThreads(2)
                .compress(true)
                .decompress(true)
                .compressionLevel(3)
                .keepAliveEnable(false)
                .maxContentLength(4L)
                .maxInitialLineLength(5)
                .maxHeaderSize(5)
                .maxChunkSize(6)
                .metricsEnabled(true)
                .haProxy(HAProxyMode.AUTO)
                .ssl(new SslOptions())
                .h2(null)
                .multipart(null)
                .preferNativeTransport(false)
                .soKeepalive(false)
                .tcpNoDelay(false)
                .reuseAddress(false)
                .reusePort(false)
                .tcpFastOpen(true)
                .tcpCork(true)
                .tcpQuickAck(true)
                .soRcvbuf(7)
                .soSendbuf(8)
                .soBacklog(9)
                .soLinger(1)
                .writeBufferLowWaterMark(10)
                .writeBufferHighWaterMark(11)
                .idleTimeoutSeconds(12)
                .options(Collections.singletonMap(ChannelOption.AUTO_READ, true))
                .option(ChannelOption.AUTO_CLOSE, true)
                .childOptions(Collections.singletonMap(ChannelOption.AUTO_READ, true))
                .childOption(ChannelOption.AUTO_CLOSE, true)
                .logging(LogLevel.DEBUG)
                .channelHandlers(Collections.singleton(new ChannelInboundHandlerAdapter()))
                .configured();

        assertTrue(options.isDaemon());
        assertEquals(1L, options.getBossThreads());
        assertEquals(2L, options.getIoThreads());
        assertTrue(options.isCompress());
        assertTrue(options.isDecompress());
        assertEquals(3, options.getCompressionLevel());
        assertFalse(options.isKeepAliveEnable());
        assertEquals(4L, options.getMaxContentLength());
        assertEquals(5, options.getMaxInitialLineLength());
        assertEquals(5, options.getMaxHeaderSize());
        assertEquals(6, options.getMaxChunkSize());
        assertTrue(options.isMetricsEnabled());
        assertEquals(HAProxyMode.AUTO, options.getHaProxy());
        assertNotNull(options.getSsl());
        assertNull(options.getH2());
        assertNull(options.getMultipart());
        assertFalse(options.isPreferNativeTransport());
        assertFalse(options.isSoKeepalive());
        assertFalse(options.isTcpNoDelay());
        assertFalse(options.isReuseAddress());
        assertFalse(options.isReusePort());
        assertTrue(options.isTcpFastOpen());
        assertTrue(options.isTcpCork());
        assertTrue(options.isTcpQuickAck());
        assertEquals(7, options.getSoRcvbuf());
        assertEquals(8, options.getSoSendbuf());
        assertEquals(9, options.getSoBacklog());
        assertEquals(1, options.getSoLinger());
        assertEquals(10, options.getWriteBufferLowWaterMark());
        assertEquals(11, options.getWriteBufferHighWaterMark());
        assertEquals(12, options.getIdleTimeoutSeconds());

        assertEquals(Boolean.TRUE, options.getOptions().get(ChannelOption.AUTO_READ));
        assertEquals(Boolean.TRUE, options.getOptions().get(ChannelOption.AUTO_CLOSE));
        assertEquals(Boolean.TRUE, options.getChildOptions().get(ChannelOption.AUTO_READ));
        assertEquals(Boolean.TRUE, options.getChildOptions().get(ChannelOption.AUTO_CLOSE));
        assertEquals(LogLevel.DEBUG, options.getLogging());
        assertEquals(1, options.getChannelHandlers().size());
    }

    @Test
    void testCopyFromAnother() {

        final ServerOptions another = ServerOptionsConfigure.newOpts()
                .daemon(true)
                .bossThreads(1)
                .ioThreads(2)
                .compress(true)
                .decompress(true)
                .compressionLevel(3)
                .keepAliveEnable(false)
                .maxContentLength(4L)
                .maxInitialLineLength(5)
                .maxHeaderSize(5)
                .maxChunkSize(6)
                .metricsEnabled(true)
                .haProxy(HAProxyMode.AUTO)
                .ssl(new SslOptions())
                .h2(null)
                .multipart(null)
                .preferNativeTransport(false)
                .soKeepalive(false)
                .tcpNoDelay(false)
                .reuseAddress(false)
                .reusePort(false)
                .tcpFastOpen(true)
                .tcpCork(true)
                .tcpQuickAck(true)
                .soRcvbuf(7)
                .soSendbuf(8)
                .soBacklog(9)
                .soLinger(1)
                .writeBufferLowWaterMark(10)
                .writeBufferHighWaterMark(11)
                .idleTimeoutSeconds(12)
                .options(Collections.singletonMap(ChannelOption.AUTO_READ, true))
                .option(ChannelOption.AUTO_CLOSE, true)
                .childOptions(Collections.singletonMap(ChannelOption.AUTO_READ, true))
                .childOption(ChannelOption.AUTO_CLOSE, true)
                .logging(LogLevel.DEBUG)
                .channelHandlers(Collections.singleton(new ChannelInboundHandlerAdapter()))
                .configured();

        final ServerOptions options = new ServerOptions(another);
        assertTrue(options.isDaemon());
        assertEquals(1L, options.getBossThreads());
        assertEquals(2L, options.getIoThreads());
        assertTrue(options.isCompress());
        assertTrue(options.isDecompress());
        assertEquals(3, options.getCompressionLevel());
        assertFalse(options.isKeepAliveEnable());
        assertEquals(4L, options.getMaxContentLength());
        assertEquals(5, options.getMaxInitialLineLength());
        assertEquals(5, options.getMaxHeaderSize());
        assertEquals(6, options.getMaxChunkSize());
        assertTrue(options.isMetricsEnabled());
        assertEquals(HAProxyMode.AUTO, options.getHaProxy());
        assertNotNull(options.getSsl());
        assertNull(options.getH2());
        assertNull(options.getMultipart());
        assertFalse(options.isPreferNativeTransport());
        assertFalse(options.isSoKeepalive());
        assertFalse(options.isTcpNoDelay());
        assertFalse(options.isReuseAddress());
        assertFalse(options.isReusePort());
        assertTrue(options.isTcpFastOpen());
        assertTrue(options.isTcpCork());
        assertTrue(options.isTcpQuickAck());
        assertEquals(7, options.getSoRcvbuf());
        assertEquals(8, options.getSoSendbuf());
        assertEquals(9, options.getSoBacklog());
        assertEquals(1, options.getSoLinger());
        assertEquals(10, options.getWriteBufferLowWaterMark());
        assertEquals(11, options.getWriteBufferHighWaterMark());
        assertEquals(12, options.getIdleTimeoutSeconds());

        assertEquals(Boolean.TRUE, options.getOptions().get(ChannelOption.AUTO_READ));
        assertEquals(Boolean.TRUE, options.getOptions().get(ChannelOption.AUTO_CLOSE));
        assertEquals(Boolean.TRUE, options.getChildOptions().get(ChannelOption.AUTO_READ));
        assertEquals(Boolean.TRUE, options.getChildOptions().get(ChannelOption.AUTO_CLOSE));
        assertEquals(LogLevel.DEBUG, options.getLogging());
        assertEquals(1, options.getChannelHandlers().size());
    }
}
