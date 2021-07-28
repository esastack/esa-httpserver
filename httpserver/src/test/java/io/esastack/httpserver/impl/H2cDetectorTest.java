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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2cDetectorTest {

    @Test
    void testH2c() {
        final AtomicInteger isH2c = new AtomicInteger();
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
            if (h2c) {
                isH2c.set(1);
            } else {
                isH2c.set(-1);
            }
        }));

        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());
        assertEquals(1, isH2c.get());
        final ByteBuf read = channel.readInbound();
        assertTrue(ByteBufUtil.equals(Http2CodecUtil.connectionPrefaceBuf(), read));
        assertNull(channel.pipeline().get(H2cDetector.class));
    }

    @Test
    void testH2c1() {
        final AtomicInteger isH2c = new AtomicInteger();
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
            if (h2c) {
                isH2c.set(1);
            } else {
                isH2c.set(-1);
            }
        }));

        final ByteBuf preface = Http2CodecUtil.connectionPrefaceBuf();

        channel.writeInbound(preface.readSlice(2));
        channel.writeInbound(preface.readSlice(2));
        channel.writeInbound(preface.readSlice(2));
        channel.writeInbound(preface);
        assertEquals(1, isH2c.get());
        final ByteBuf read = channel.readInbound();
        assertTrue(ByteBufUtil.equals(Http2CodecUtil.connectionPrefaceBuf(), read));
        assertNull(channel.pipeline().get(H2cDetector.class));
    }

    @Test
    void testH2c2() {
        final AtomicInteger isH2c = new AtomicInteger();
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
            if (h2c) {
                isH2c.set(1);
            } else {
                isH2c.set(-1);
            }
        }));

        final ByteBuf preface = Http2CodecUtil.connectionPrefaceBuf();
        final ByteBuf oversize = Unpooled.buffer(preface.readableBytes() + 4);
        oversize.writeBytes(preface);
        oversize.writeByte(1);
        oversize.writeByte(2);
        oversize.writeByte(3);
        oversize.writeByte(4);

        channel.writeInbound(oversize);
        assertEquals(1, isH2c.get());
        final ByteBuf read = channel.readInbound();
        assertTrue(ByteBufUtil.equals(oversize, read));
        assertNull(channel.pipeline().get(H2cDetector.class));
    }

    @Test
    void testH2cFailed() {
        final AtomicInteger isH2c = new AtomicInteger();
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
            if (h2c) {
                isH2c.set(1);
            } else {
                isH2c.set(-1);
            }
        }));

        final ByteBuf preface = Unpooled.copiedBuffer(Http2CodecUtil.connectionPrefaceBuf());
        preface.setByte(preface.writerIndex() - 1,
                preface.getByte(preface.writerIndex() - 1) + (byte) 1);

        channel.writeInbound(preface);
        assertEquals(-1, isH2c.get());
        final ByteBuf read = channel.readInbound();
        assertTrue(ByteBufUtil.equals(preface, read));
        assertNull(channel.pipeline().get(H2cDetector.class));
    }

    @Test
    void testExceptionCaught() {
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
        }));

        channel.pipeline().fireExceptionCaught(new IllegalStateException());
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());
    }

    @Test
    void testIdle() {
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
        }));

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());
    }

    @Test
    void testNoneIdleEvent() {
        final List<Object> events = new CopyOnWriteArrayList<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new H2cDetector((ctx, h2c) -> {
        }), new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                events.add(evt);
            }
        });

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertEquals(1, events.size());
        assertSame(IdleStateEvent.READER_IDLE_STATE_EVENT, events.get(0));
    }

}
