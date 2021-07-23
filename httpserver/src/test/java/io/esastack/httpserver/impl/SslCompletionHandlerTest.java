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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SslCompletionHandlerTest {

    @Test
    void testHandShakeSuccess() {
        final AtomicInteger ret = new AtomicInteger(0);
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new SslCompletionHandler((r, ch, t) -> {
            ret.set(r ? 1 : -1);
            err.set(t);
        }));

        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);

        assertEquals(1, ret.get());
        assertNull(err.get());
        assertNull(channel.pipeline().get(SslCompletionHandler.class));
    }

    @Test
    void testHandShakeFailed() {
        final AtomicInteger ret = new AtomicInteger(0);
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new SslCompletionHandler((r, ch, t) -> {
            ret.set(r ? 1 : -1);
            err.set(t);
        }));

        final Exception ex = new IllegalStateException();
        channel.pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(ex));

        assertEquals(-1, ret.get());
        assertSame(ex, err.get());
        assertNull(channel.pipeline().get(SslCompletionHandler.class));
    }

    @Test
    void testEmptyHandingInExceptionCaught() {
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new SslCompletionHandler((r, ch, t) -> {
        }), new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                err.set(cause);
            }
        });

        channel.pipeline().fireExceptionCaught(new IllegalStateException());
        assertNull(err.get());
    }

}
