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

import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2HeadersEncoder;
import io.netty.handler.codec.http2.Http2Settings;

final class Http2ConnectionChunkHandlerBuilder
        extends AbstractHttp2ConnectionHandlerBuilder<Http2ConnectionChunkHandler, Http2ConnectionChunkHandlerBuilder> {

    @Override
    public Http2ConnectionChunkHandlerBuilder validateHeaders(boolean validateHeaders) {
        return super.validateHeaders(validateHeaders);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder initialSettings(Http2Settings settings) {
        return super.initialSettings(settings);
    }

    @Override
    public Http2Settings initialSettings() {
        return super.initialSettings();
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder frameListener(Http2FrameListener frameListener) {
        return super.frameListener(frameListener);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        return super.gracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder server(boolean isServer) {
        return super.server(isServer);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder connection(Http2Connection connection) {
        return super.connection(connection);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder maxReservedStreams(int maxReservedStreams) {
        return super.maxReservedStreams(maxReservedStreams);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder codec(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder) {
        return super.codec(decoder, encoder);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder frameLogger(Http2FrameLogger frameLogger) {
        return super.frameLogger(frameLogger);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder encoderEnforceMaxConcurrentStreams(
            boolean encoderEnforceMaxConcurrentStreams) {
        return super.encoderEnforceMaxConcurrentStreams(encoderEnforceMaxConcurrentStreams);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder encoderIgnoreMaxHeaderListSize(boolean encoderIgnoreMaxHeaderListSize) {
        return super.encoderIgnoreMaxHeaderListSize(encoderIgnoreMaxHeaderListSize);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder headerSensitivityDetector(Http2HeadersEncoder.SensitivityDetector arg0) {
        return super.headerSensitivityDetector(arg0);
    }

    @Override
    @Deprecated
    public Http2ConnectionChunkHandlerBuilder initialHuffmanDecodeCapacity(int initialHuffmanDecodeCapacity) {
        return super.initialHuffmanDecodeCapacity(initialHuffmanDecodeCapacity);
    }

    @Override
    public Http2ConnectionChunkHandlerBuilder decoupleCloseAndGoAway(boolean decoupleCloseAndGoAway) {
        return super.decoupleCloseAndGoAway(decoupleCloseAndGoAway);
    }

    @Override
    public Http2ConnectionChunkHandler build() {
        return super.build();
    }

    @Override
    protected Http2ConnectionChunkHandler build(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                                Http2Settings initialSettings) {
        return new Http2ConnectionChunkHandler(decoder, encoder, initialSettings, decoupleCloseAndGoAway());
    }
}
