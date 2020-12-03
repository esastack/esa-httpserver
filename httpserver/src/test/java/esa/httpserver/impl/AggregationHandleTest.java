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

import esa.commons.http.HttpHeaders;
import esa.commons.netty.http.EmptyHttpHeaders;
import esa.commons.netty.http.Http1HeadersImpl;
import esa.httpserver.core.Aggregation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AggregationHandleTest {

    @Test
    void testEmpty() {
        final Aggregation empty = AggregationHandle.EMPTY;
        assertSame(Unpooled.EMPTY_BUFFER, empty.body());
        assertSame(EmptyHttpHeaders.INSTANCE, empty.trailers());
    }

    @Test
    void testAggregateAndRelease() {
        final ChannelHandlerContext ctx =
                new EmbeddedChannel(new ChannelInboundHandlerAdapter()).pipeline().firstContext();
        final AggregationHandle handle = new AggregationHandle(ctx);

        assertSame(Unpooled.EMPTY_BUFFER, handle.body());
        assertSame(EmptyHttpHeaders.INSTANCE, handle.trailers());

        final ByteBuf c1 = Unpooled.copiedBuffer("123".getBytes(StandardCharsets.UTF_8));
        final ByteBuf c2 = Unpooled.copiedBuffer("456".getBytes(StandardCharsets.UTF_8));

        try {
            handle.appendPartialContent(c1);
            assertEquals("123", handle.body().toString(StandardCharsets.UTF_8));

            handle.appendPartialContent(c2);
            assertEquals("123456", handle.body().toString(StandardCharsets.UTF_8));

            final HttpHeaders trailers = new Http1HeadersImpl();
            handle.setTrailers(trailers);
            assertSame(trailers, handle.trailers());
        } finally {
            handle.release();
            assertEquals(0, handle.body().refCnt());
            assertEquals(0, c1.refCnt());
            assertEquals(0, c2.refCnt());
        }
    }

}
