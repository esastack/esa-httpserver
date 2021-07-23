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

import io.esastack.httpserver.ServerOptions;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameSizePolicy;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersEncoder;
import io.netty.handler.codec.http2.Http2Settings;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Helper {

    static ServerRuntime serverRuntime() {
        return serverRuntime(ServerOptionsConfigure.newOpts().configured());
    }

    static ServerRuntime serverRuntime(ServerOptions options) {
        final ServerRuntime runtime = new ServerRuntime("foo", options, new HttpServerImpl.CloseFuture());
        runtime.setStarted(null, null, null);
        return runtime;
    }

    static final Object SETTINGS_ACK = new Object();

    static Http2FrameWriter mockHttp2FrameWriter() {
        final Http2HeadersEncoder.Configuration conf = mock(Http2HeadersEncoder.Configuration.class);
        when(conf.maxHeaderTableSize()).thenReturn(0L);
        when(conf.maxHeaderListSize()).thenReturn(0L);

        final Http2FrameSizePolicy policy = mock(Http2FrameSizePolicy.class);
        when(policy.maxFrameSize()).thenReturn(0);

        final Http2FrameWriter.Configuration configuration = mock(Http2FrameWriter.Configuration.class);
        when(configuration.headersConfiguration()).thenReturn(conf);
        when(configuration.frameSizePolicy()).thenReturn(policy);

        final Http2FrameWriter frameWriter = mock(Http2FrameWriter.class);

        when(frameWriter.configuration()).thenReturn(configuration);
        when(frameWriter.writeSettings(any(ChannelHandlerContext.class),
                any(Http2Settings.class),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(invocation.getArgument(1), invocation.getArgument(2));
                });

        when(frameWriter.writeSettingsAck(any(ChannelHandlerContext.class),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(SETTINGS_ACK, invocation.getArgument(1));
                });

        when(frameWriter.writeGoAway(any(ChannelHandlerContext.class),
                anyInt(),
                anyLong(),
                any(ByteBuf.class),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(new GoawayFrame(invocation.getArgument(1),
                                    invocation.getArgument(2),
                                    invocation.getArgument(3)),
                            invocation.getArgument(4));
                });
        when(frameWriter.writeHeaders(any(ChannelHandlerContext.class),
                anyInt(),
                any(Http2Headers.class),
                anyInt(),
                anyBoolean(),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation ->
                        ((ChannelPromise) invocation.getArgument(5)).setSuccess());

        when(frameWriter.writeHeaders(any(ChannelHandlerContext.class),
                anyInt(),
                any(Http2Headers.class),
                anyInt(),
                anyShort(),
                anyBoolean(),
                anyInt(),
                anyBoolean(),
                any(ChannelPromise.class)))
                .thenAnswer(invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(new Helper.HeaderFrame(invocation.getArgument(1),
                                    invocation.getArgument(2),
                                    invocation.getArgument(3),
                                    invocation.getArgument(4),
                                    invocation.getArgument(5),
                                    invocation.getArgument(6),
                                    invocation.getArgument(7)),
                            invocation.getArgument(8));
                });

        when(frameWriter.writeData(any(ChannelHandlerContext.class),
                anyInt(),
                any(ByteBuf.class),
                anyInt(),
                anyBoolean(),
                any(ChannelPromise.class)))
                .thenAnswer(invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(new Helper.DataFrame(invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3),
                            invocation.getArgument(4)), invocation.getArgument(5));
                });

        when(frameWriter.writeRstStream(any(ChannelHandlerContext.class),
                anyInt(),
                anyLong(),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation ->
                        ((ChannelPromise) invocation.getArgument(3)).setSuccess());

        when(frameWriter.writeWindowUpdate(any(ChannelHandlerContext.class),
                anyInt(),
                anyInt(),
                any(ChannelPromise.class)))
                .then((Answer<ChannelFuture>) invocation ->
                        ((ChannelPromise) invocation.getArgument(3)).setSuccess());

        when(frameWriter.writePushPromise(any(ChannelHandlerContext.class),
                anyInt(),
                anyInt(),
                any(Http2Headers.class),
                anyInt(),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation ->
                        ((ChannelPromise) invocation.getArgument(5)).setSuccess());

        when(frameWriter.writeFrame(any(ChannelHandlerContext.class),
                anyByte(),
                anyInt(),
                any(Http2Flags.class),
                any(ByteBuf.class),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation -> {

                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(new Frame(invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3),
                            invocation.getArgument(4)), invocation.getArgument(5));
                });
        return frameWriter;
    }

    static void mockHeaderAndDataFrameWrite(Http2FrameWriter encoder) {
        when(encoder.writeHeaders(any(ChannelHandlerContext.class),
                anyInt(),
                any(Http2Headers.class),
                anyInt(),
                anyShort(),
                anyBoolean(),
                anyInt(),
                anyBoolean(),
                any(ChannelPromise.class)))
                .thenAnswer(invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(new Helper.HeaderFrame(invocation.getArgument(1),
                                    invocation.getArgument(2),
                                    invocation.getArgument(3),
                                    invocation.getArgument(4),
                                    invocation.getArgument(5),
                                    invocation.getArgument(6),
                                    invocation.getArgument(7)),
                            invocation.getArgument(8));
                });

        when(encoder.writeData(any(ChannelHandlerContext.class),
                anyInt(),
                any(ByteBuf.class),
                anyInt(),
                anyBoolean(),
                any(ChannelPromise.class)))
                .thenAnswer(invocation -> {
                    ChannelHandlerContext ctx = invocation.getArgument(0);
                    return ctx.write(new Helper.DataFrame(invocation.getArgument(1),
                            invocation.getArgument(2),
                            invocation.getArgument(3),
                            invocation.getArgument(4)), invocation.getArgument(5));
                });
    }

    static class HeaderFrame {
        final int streamId;
        final Http2Headers headers;
        final int streamDependency;
        final short weight;
        final boolean exclusive;
        final int padding;
        final boolean endStream;

        HeaderFrame(int streamId,
                    Http2Headers headers,
                    int streamDependency,
                    short weight,
                    boolean exclusive,
                    int padding,
                    boolean endStream) {
            this.streamId = streamId;
            this.headers = headers;
            this.streamDependency = streamDependency;
            this.weight = weight;
            this.exclusive = exclusive;
            this.padding = padding;
            this.endStream = endStream;
        }
    }

    static class DataFrame {
        final int streamId;
        final ByteBuf data;
        final int padding;
        final boolean endStream;

        DataFrame(int streamId, ByteBuf data, int padding, boolean endStream) {
            this.streamId = streamId;
            this.data = data;
            this.padding = padding;
            this.endStream = endStream;
        }
    }

    static class GoawayFrame {
        final int lastStreamId;
        final long errorCode;
        final ByteBuf debugData;

        GoawayFrame(int lastStreamId, long errorCode, ByteBuf debugData) {
            this.lastStreamId = lastStreamId;
            this.errorCode = errorCode;
            this.debugData = debugData;
        }
    }

    static class Frame {
        final byte frameType;
        final int streamId;
        final Http2Flags flags;
        final ByteBuf payload;

        Frame(byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
            this.frameType = frameType;
            this.streamId = streamId;
            this.flags = flags;
            this.payload = payload;
        }
    }

}
