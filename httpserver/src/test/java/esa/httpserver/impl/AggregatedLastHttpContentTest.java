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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AggregatedLastHttpContentTest {

    @Test
    void testAll() {
        final ByteBuf c = Unpooled.copiedBuffer("foo".getBytes());
        final HttpHeaders headers = new DefaultHttpHeaders();
        headers.set("a", "1");
        final AggregatedLastHttpContent last = new AggregatedLastHttpContent(c, headers);
        assertSame(c, last.content());
        assertSame(headers, last.trailingHeaders());
        assertSame(DecoderResult.SUCCESS, last.decoderResult());
        assertSame(DecoderResult.SUCCESS, last.getDecoderResult());
        assertThrows(UnsupportedOperationException.class, () -> last.setDecoderResult(DecoderResult.UNFINISHED));
        assertEquals(c.refCnt(), last.refCnt());

        AggregatedLastHttpContent another = last.copy();
        assertNotSame(last, another);
        assertTrue(ByteBufUtil.equals(c, another.content()));
        assertNotSame(headers, another.trailingHeaders());
        assertTrue(another.trailingHeaders().contains("a", "1", true));

        another = last.duplicate();
        assertNotSame(last, another);
        assertTrue(ByteBufUtil.equals(c, another.content()));
        assertNotSame(headers, another.trailingHeaders());
        assertTrue(another.trailingHeaders().contains("a", "1", true));

        another = last.retainedDuplicate();
        assertNotSame(last, another);
        assertTrue(ByteBufUtil.equals(c, another.content()));
        assertNotSame(headers, another.trailingHeaders());
        assertTrue(another.trailingHeaders().contains("a", "1", true));
        assertEquals(2, another.refCnt());
        another.release();

        final ByteBuf c1 = Unpooled.copiedBuffer("bar".getBytes());
        another = last.replace(c1);
        assertSame(c1, another.content());
        assertNotSame(headers, another.trailingHeaders());
        assertTrue(another.trailingHeaders().contains("a", "1", true));

        assertEquals(2, last.retain().refCnt());
        assertSame(last, last.touch());
        assertSame(last, last.touch("baz"));
        assertTrue(last.release(2));

        assertNotNull(last.toString());
    }

}
