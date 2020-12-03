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

import esa.commons.NetworkUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class HAProxyTest {

    @Test
    void testHAProxyDetected() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HAProxyDetector());
        channel.writeInbound(Unpooled.copiedBuffer("PROXY ".getBytes(StandardCharsets.US_ASCII)));
        // needs more bytes
        assertNull(channel.readInbound());
        channel.writeInbound(Unpooled.copiedBuffer("TCP4 192.168.0.1 "
                .getBytes(StandardCharsets.US_ASCII)));
        // fall in HAProxyMessageDecoder
        assertNull(channel.readInbound());

        assertNotNull(channel.pipeline().get(HAProxyMessageDecoder.class));
        assertNotNull(channel.pipeline().get(HAProxyMessageHandler.class));
        assertEquals(channel.pipeline().first().getClass(), HAProxyMessageDecoder.class);
        assertEquals(channel.pipeline().last().getClass(), HAProxyMessageHandler.class);

        channel.writeInbound(Unpooled.copiedBuffer("192.168.0.11 56324 443\r\n"
                .getBytes(StandardCharsets.US_ASCII)));

        assertNull(channel.pipeline().get(HAProxyMessageDecoder.class));
        assertNull(channel.pipeline().get(HAProxyMessageHandler.class));

        final SocketAddress sourceAddress = channel.attr(Utils.SOURCE_ADDRESS).get();
        assertNotNull(sourceAddress);

        assertEquals("192.168.0.1:56324", NetworkUtils.parseAddress(sourceAddress));
    }

    @Test
    void testHAProxyDetectFailed() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HAProxyDetector());
        final String header = "UNKNOWN UNKNOWN 192.168.0.1 192.168.0.11 56324 443\r\n";
        final ByteBuf buf = Unpooled.copiedBuffer(header.getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);
        assertSame(buf, channel.readInbound());
        assertNull(channel.pipeline().get(HAProxyDetector.class));
        assertNull(channel.pipeline().get(HAProxyMessageDecoder.class));
        assertNull(channel.pipeline().get(HAProxyMessageHandler.class));
    }

    @Test
    void testHAProxyDetectedAsUnknownProtocol() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HAProxyDetector());
        final String header = "PROXY UNKNOWN 192.168.0.1 192.168.0.11 56324 443\r\n";
        final ByteBuf buf = Unpooled.copiedBuffer(header.getBytes(StandardCharsets.US_ASCII));
        channel.writeInbound(buf);
        assertNull(channel.pipeline().get(HAProxyDetector.class));
        assertNull(channel.pipeline().get(HAProxyMessageDecoder.class));
        assertNull(channel.pipeline().get(HAProxyMessageHandler.class));
        assertNull(channel.attr(Utils.SOURCE_ADDRESS).get());
    }

    @Test
    void testNoneHAPRoxyMessageReadInHAProxyMessageHandler() {
        final EmbeddedChannel channel = new EmbeddedChannel(new HAProxyMessageHandler());
        final Object obj = new Object();
        channel.writeInbound(obj);
        assertSame(obj, channel.readInbound());
    }

}
