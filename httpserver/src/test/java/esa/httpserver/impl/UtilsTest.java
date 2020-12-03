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
import esa.commons.netty.http.Http1HeadersImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static esa.httpserver.impl.Utils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UtilsTest {

    @Test
    void testHandleIdle() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        assertFalse(handleIdle(channel.pipeline().firstContext(), IdleStateEvent.READER_IDLE_STATE_EVENT));
        assertTrue(channel.isActive());
        assertTrue(handleIdle(channel.pipeline().firstContext(), IdleStateEvent.ALL_IDLE_STATE_EVENT));
        assertFalse(channel.isActive());
    }

    @Test
    void testHandleException() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        handleException(channel.pipeline().firstContext(), new IOException());
        assertFalse(channel.isActive());

        final EmbeddedChannel channel1 = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        assertTrue(handleIdle(channel1.pipeline().firstContext(), IdleStateEvent.ALL_IDLE_STATE_EVENT));
        assertFalse(channel1.isActive());
    }

    @Test
    void testStandardHttp2Headers() {
        final Http2Headers headers = new DefaultHttp2Headers()
                .method(HttpMethod.GET.name())
                .status("200")
                .authority("auth")
                .scheme("schema")
                .path("p")
                .set(HttpHeaderNames.COOKIE, "a=1")
                .add(HttpHeaderNames.COOKIE, "b=2");

        standardHttp2Headers(headers);

        assertNull(headers.status());
        assertNull(headers.method());
        assertNull(headers.authority());
        assertNull(headers.scheme());
        assertNull(headers.path());

        assertEquals("a=1; b=2", headers.get(HttpHeaderNames.COOKIE));
        assertEquals(1, headers.getAll(HttpHeaderNames.COOKIE).size());
    }

    @Test
    void testTryRelease() {
        ByteBuf buf = Unpooled.copiedBuffer("foo".getBytes());
        assertEquals(1, buf.refCnt());
        tryRelease(buf);
        assertEquals(0, buf.refCnt());

        buf = Unpooled.copiedBuffer("foo".getBytes());
        buf.retain();

        tryRelease(buf);
        assertEquals(1, buf.refCnt());
        assertTrue(buf.release());
    }

    @Test
    void testTrySuccess() {
        final Promise<Void> p = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
        trySuccess(p, null);
        assertTrue(p.isSuccess());
        assertDoesNotThrow(() -> trySuccess(p, null));
    }

    @Test
    void testTryFailure() {
        final Promise<Void> p = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
        tryFailure(p, new IllegalStateException());
        assertTrue(p.isDone());
        assertFalse(p.isSuccess());
        assertDoesNotThrow(() -> tryFailure(p, new IllegalStateException()));
    }


    @Test
    void testToErrorMsgWithMessageInException() {
        final BaseResponse response = mock(BaseResponse.class);
        final HttpHeaders headers = new Http1HeadersImpl();
        headers.set("a", "1");
        when(response.headers()).thenReturn(headers);

        assertEquals("foo",
                toErrorMsg(response, new IllegalStateException("foo")).toString(StandardCharsets.UTF_8));

        assertFalse(headers.contains("a", "1"));
        assertTrue(headers.contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN));
    }

    @Test
    void testToErrorMsgWithoutMessageInException() {
        final BaseResponse response = mock(BaseResponse.class);
        final HttpHeaders headers = new Http1HeadersImpl();
        headers.set("a", "1");
        when(response.headers()).thenReturn(headers);

        assertEquals("",
                toErrorMsg(response, new IllegalStateException()).toString(StandardCharsets.UTF_8));

        assertFalse(headers.contains("a", "1"));
        assertTrue(headers.contains(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN));
    }

    @Test
    void testToErrorMsg() {
        assertEquals("foo",
                toErrorMsg(new IllegalStateException("foo")).toString(StandardCharsets.UTF_8));
        assertEquals("",
                toErrorMsg(new IllegalStateException()).toString(StandardCharsets.UTF_8));
    }

    @Test
    void testCheckIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> checkIndex(EMPTY_BYTES, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> checkIndex(EMPTY_BYTES, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkIndex(new byte[2], 2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> checkIndex(new byte[2], 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> checkIndex(new byte[2], 1, 2));
    }

}
