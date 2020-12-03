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

import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class Http2ConnectionChunkHandlerBuilderTest {

    @Test
    void testBuild() {
        final Http2ConnectionChunkHandlerBuilder builder = new Http2ConnectionChunkHandlerBuilder();
        final Http2Settings settings = Http2Settings.defaultSettings();
        builder.validateHeaders(true)
                .initialSettings(settings);
        assertSame(settings, builder.initialSettings());

        assertThrows(NullPointerException.class, () -> builder.frameListener(null));
        assertThrows(IllegalArgumentException.class, () -> builder.gracefulShutdownTimeoutMillis(-2L));
        builder.server(true);

        final Http2FrameListener listener = mock(Http2FrameListener.class);
        builder.frameListener(listener).maxReservedStreams(1)
                .encoderEnforceMaxConcurrentStreams(false)
                .encoderIgnoreMaxHeaderListSize(false)
                .headerSensitivityDetector(Http2HeadersEncoder.ALWAYS_SENSITIVE)
                .decoupleCloseAndGoAway(false)
                .frameLogger(new Http2FrameLogger(LogLevel.INFO));

        final Http2ConnectionChunkHandler handler = builder.build();

        assertTrue(handler.encoder().frameWriter() instanceof Http2OutboundFrameLogger);
        assertTrue(handler.connection().isServer());
    }

    @Test
    void testBuildWithCodec() {
        final Http2Connection connection = new DefaultHttp2Connection(false);
        final Http2FrameWriter writer = new DefaultHttp2FrameWriter();
        final Http2FrameReader reader = new DefaultHttp2FrameReader();
        final Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(connection, writer);
        final Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(connection, encoder, reader);

        final Http2ConnectionChunkHandler handler = new Http2ConnectionChunkHandlerBuilder()
                .codec(decoder, encoder)
                .frameListener(mock(Http2FrameListener.class))
                .build();

        assertSame(encoder, handler.encoder());
    }

    @Test
    void testBuildWithConnection() {
        final Http2Connection connection = new DefaultHttp2Connection(true);
        final Http2ConnectionChunkHandler handler = new Http2ConnectionChunkHandlerBuilder()
                .connection(connection)
                .frameListener(mock(Http2FrameListener.class))
                .build();

        assertSame(connection, handler.connection());
    }

}
