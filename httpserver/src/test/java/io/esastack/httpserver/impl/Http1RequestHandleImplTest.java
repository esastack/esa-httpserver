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

import io.esastack.commons.net.netty.http.Http1HeadersImpl;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http1RequestHandleImplTest {

    @Test
    void testImplementation() {
        final HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo", new Http1HeadersImpl());
        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        final EmbeddedChannel channel = new EmbeddedChannel();
        when(ctx.channel()).thenReturn(channel);
        final Http1RequestHandleImpl handle =
                new Http1RequestHandleImpl(Helper.serverRuntime(),
                        ctx,
                        request,
                        true);

        assertEquals(io.esastack.commons.net.http.HttpVersion.HTTP_1_1, handle.version());

        assertSame(io.esastack.commons.net.http.HttpMethod.POST, handle.method());
        assertSame(request.headers(), handle.headers());

        assertNotNull(handle.response());
        assertSame(request, handle.toHttpRequest());
    }

}
