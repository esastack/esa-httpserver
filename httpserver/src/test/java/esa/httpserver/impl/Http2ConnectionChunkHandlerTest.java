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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class Http2ConnectionChunkHandlerTest {

    private static final int STREAM_ID = 3;

    private EmbeddedChannel channel;

    @Mock
    private Http2Connection connection;

    @Mock
    private Http2Stream stream;

    @Mock
    private Http2ConnectionDecoder decoder;

    @Mock
    private Http2ConnectionEncoder encoder;

    @Mock
    private Http2FrameWriter frameWriter;

    @Mock
    private Http2RemoteFlowController remoteFlow;

    @Mock
    private Http2LocalFlowController localFlow;

    @Mock
    private Http2Connection.Endpoint<Http2RemoteFlowController> remote;

    @Mock
    private Http2RemoteFlowController remoteFlowController;

    @Mock
    private Http2Connection.Endpoint<Http2LocalFlowController> local;

    @Mock
    private Http2LocalFlowController localFlowController;


    @BeforeEach
    void setUp() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        when(stream.open(anyBoolean())).thenReturn(stream);
        when(remote.flowController()).thenReturn(remoteFlowController);
        when(local.flowController()).thenReturn(localFlowController);

        when(connection.remote()).thenReturn(remote);
        when(connection.local()).thenReturn(local);
        when(connection.forEachActiveStream(any(Http2StreamVisitor.class)))
                .thenAnswer((Answer<Http2Stream>) in -> {
                    Http2StreamVisitor visitor = in.getArgument(0);
                    if (!visitor.visit(stream)) {
                        return stream;
                    }
                    return null;
                });
        when(connection.numActiveStreams()).thenReturn(1);
        when(connection.stream(STREAM_ID)).thenReturn(stream);
        when(connection.goAwaySent(anyInt(), anyLong(), any(ByteBuf.class))).thenReturn(true);

        when(encoder.connection()).thenReturn(connection);
        when(encoder.frameWriter()).thenReturn(frameWriter);
        when(encoder.flowController()).thenReturn(remoteFlow);
        when(encoder.writeSettings(any(ChannelHandlerContext.class),
                any(Http2Settings.class), any(ChannelPromise.class)))
                .thenAnswer(invocation -> {
                    ChannelPromise p = invocation.getArgument(2);
                    return p.setSuccess();
                });
        when(decoder.connection()).thenReturn(connection);
        when(decoder.flowController()).thenReturn(localFlow);

        when(frameWriter.writeGoAway(
                any(ChannelHandlerContext.class),
                anyInt(),
                anyLong(),
                any(ByteBuf.class),
                any(ChannelPromise.class)))
                .thenAnswer((Answer<ChannelFuture>) invocation -> {
                    ByteBuf buf = invocation.getArgument(3);
                    buf.release();
                    ChannelPromise p = invocation.getArgument(4);
                    return p.setSuccess();
                });

        channel = new EmbeddedChannel(new Http2ConnectionChunkHandler(decoder,
                encoder,
                Http2Settings.defaultSettings(),
                false));

        Helper.mockHeaderAndDataFrameWrite(encoder);
    }

    @Test
    void testAddChunkWriteHandlerAfterHandlerAdded() {
        assertNotNull(channel.pipeline().get(ChunkedWriteHandler.class));
        assertSame(channel.pipeline().last(), channel.pipeline().get(ChunkedWriteHandler.class));
    }

    @Test
    void testTriggerIdleStateEvent() {
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
        assertFalse(channel.isActive());
    }

    @Test
    void testTriggerNonIdleStateEvent() {
        final List<Object> events = new CopyOnWriteArrayList<>();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                events.add(evt);
            }
        });
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);
        assertEquals(1, events.size());
        assertSame(IdleStateEvent.READER_IDLE_STATE_EVENT, events.get(0));
        assertTrue(channel.isActive());
    }

    @Test
    void testNonHtt2ExceptionCaught() {
        final List<Throwable> errs = new CopyOnWriteArrayList<>();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                errs.add(cause);
            }
        });

        final Exception ex = new IllegalStateException("foo");
        channel.pipeline().fireExceptionCaught(ex);
        assertTrue(errs.isEmpty());
        assertFalse(channel.isActive());
    }

    @Test
    void testHttp2ExceptionCaught() {
        final List<Throwable> errs = new CopyOnWriteArrayList<>();
        channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                errs.add(cause);
            }
        });

        final Exception ex = new Http2Exception(Http2Error.STREAM_CLOSED);
        channel.pipeline().fireExceptionCaught(ex);
        assertTrue(errs.isEmpty());
        assertFalse(channel.isActive());
    }

    @Test
    void testWriteHttp2ChunkedInputWithoutTrailer() throws IOException {

        final File file = File.createTempFile("httpserver-", ".tmp");
        try {
            file.deleteOnExit();

            final FileOutputStream out = new FileOutputStream(file);
            final byte[] data = new byte[1024 * 1024];
            ThreadLocalRandom.current().nextBytes(data);
            out.write(data);
            out.close();
            final Http2ChunkedInput chunkedInput =
                    new Http2ChunkedInput(new ChunkedFile(file, 1024),
                            null,
                            STREAM_ID,
                            0,
                            Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                            false);
            channel.writeOutbound(chunkedInput);
            assertTrue(channel.finish());
            // ignore h2 preface
            channel.readOutbound();
            final CompositeByteBuf body = Unpooled.compositeBuffer();
            Object frame;
            boolean endStream = false;
            while ((frame = channel.readOutbound()) != null) {
                if (frame instanceof Helper.DataFrame) {
                    assertFalse(endStream);
                    Helper.DataFrame chunk = (Helper.DataFrame) frame;
                    assertEquals(STREAM_ID, chunk.streamId);
                    assertEquals(0, chunk.padding);
                    assertEquals(1024, chunk.data.readableBytes());
                    body.addComponent(true, chunk.data);
                    endStream = chunk.endStream;
                } else {
                    fail();
                }
            }
            assertTrue(endStream);
            assertArrayEquals(data, ByteBufUtil.getBytes(body));
        } finally {
            file.delete();
        }
    }

    @Test
    void testWriteHttp2ChunkedInputWithTrailer() throws IOException {

        final File file = File.createTempFile("httpserver-", ".tmp");
        try {
            file.deleteOnExit();

            final FileOutputStream out = new FileOutputStream(file);
            final byte[] data = new byte[1024 * 1024];
            ThreadLocalRandom.current().nextBytes(data);
            out.write(data);
            out.close();
            final Http2Headers trailers = new DefaultHttp2Headers();
            trailers.set("a", "1");
            final Http2ChunkedInput chunkedInput =
                    new Http2ChunkedInput(new ChunkedFile(file, 1024),
                            trailers,
                            STREAM_ID,
                            0,
                            Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                            false);
            channel.writeOutbound(chunkedInput);
            assertTrue(channel.finish());
            // ignore h2 preface
            channel.readOutbound();
            final CompositeByteBuf body = Unpooled.compositeBuffer();
            Object frame;
            while ((frame = channel.readOutbound()) != null) {
                if (frame instanceof Helper.DataFrame) {
                    Helper.DataFrame chunk = (Helper.DataFrame) frame;
                    assertEquals(STREAM_ID, chunk.streamId);
                    assertEquals(0, chunk.padding);
                    assertEquals(1024, chunk.data.readableBytes());
                    body.addComponent(true, chunk.data);
                    assertFalse(chunk.endStream);
                } else if (frame instanceof Helper.HeaderFrame) {
                    Helper.HeaderFrame trailer = (Helper.HeaderFrame) frame;
                    assertEquals(STREAM_ID, trailer.streamId);
                    assertSame(trailers, trailer.headers);
                    assertEquals(0, trailer.streamDependency);
                    assertEquals(Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, trailer.weight);
                    assertFalse(trailer.exclusive);
                    assertEquals(0, trailer.padding);
                    assertTrue(trailer.endStream);
                } else {
                    fail();
                }
            }
            assertArrayEquals(data, ByteBufUtil.getBytes(body));
        } finally {
            file.delete();
        }
    }

    @Test
    void testPassThroughNonHttp2ChunkedInput() {
        final Object obj = new Object();
        channel.writeOutbound(obj);
        // ignore h2 preface
        channel.readOutbound();
        assertSame(obj, channel.readOutbound());
    }
}
