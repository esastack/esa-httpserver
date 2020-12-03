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

import esa.commons.netty.http.Http1HeadersImpl;
import esa.httpserver.utils.Constants;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDecoderTest {

    @Test
    void testTttfbAndHeaderType() {
        final EmbeddedChannel channel =
                new EmbeddedChannel(new RequestDecoder(1024,
                        1024,
                        1024));
        final String reqStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 8\r\n" +
                "\r\n" +
                "12345678";
        final long start = System.currentTimeMillis();
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(reqStr.getBytes(StandardCharsets.US_ASCII))));
        final HttpRequest req = channel.readInbound();
        assertNotNull(req);
        assertTrue(req.headers().contains(Constants.TTFB));
        assertTrue(start <= Long.parseLong(req.headers().get(Constants.TTFB)));
        assertTrue(req.headers() instanceof Http1HeadersImpl);
        assertTrue(channel.finish());
    }

}
