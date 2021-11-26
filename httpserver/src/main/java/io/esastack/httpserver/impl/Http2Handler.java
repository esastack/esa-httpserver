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

import io.esastack.commons.net.http.HttpHeaders;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.utils.Constants;
import io.esastack.httpserver.utils.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;

import java.util.function.Consumer;

import static io.esastack.httpserver.impl.Utils.standardHttp2Headers;
import static io.esastack.httpserver.impl.Utils.toErrorMsg;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPECT;

final class Http2Handler extends Http2EventAdapter {

    private static final Http2Headers EXPECTATION_FAILED = new DefaultHttp2Headers()
            .status(HttpResponseStatus.EXPECTATION_FAILED.codeAsText())
            .setLong(CONTENT_LENGTH, 0L);
    private static final Http2Headers TOO_LARGE = new DefaultHttp2Headers()
            .status(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.codeAsText())
            .setLong(CONTENT_LENGTH, 0L);
    private static final Http2Headers CONTINUE = new DefaultHttp2Headers()
            .status(HttpResponseStatus.CONTINUE.codeAsText());

    private final ServerRuntime runtime;
    private final Http2ConnectionEncoder encoder;
    private final Http2Connection.PropertyKey messageKey;
    private final Consumer<RequestHandle> handler;

    Http2Handler(ServerRuntime runtime,
                 Http2ConnectionEncoder encoder,
                 Consumer<RequestHandle> handler) {
        this.runtime = runtime;
        this.encoder = encoder;
        this.encoder.connection().addListener(this);
        this.messageKey = encoder.connection().newKey();
        this.handler = handler;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx,
                              int streamId,
                              Http2Headers headers,
                              int padding,
                              boolean endStream) {
        onHeadersRead(ctx,
                streamId,
                headers,
                0,
                Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT,
                false,
                padding,
                endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx,
                              int streamId,
                              Http2Headers headers,
                              int streamDependency,
                              short weight,
                              boolean exclusive,
                              int padding,
                              boolean endOfStream) {
        Http2Stream stream = encoder.connection().stream(streamId);
        Http2RequestHandleImpl request = getCurrentRequest(stream);

        if (request == null) {
            final CharSequence expectValue = headers.get(EXPECT);
            boolean requiredContentLengthValid = true;
            if (expectValue != null) {
                if (HttpHeaderValues.CONTINUE.contentEqualsIgnoreCase(expectValue)) {
                    // has 'expect: 100-continue' header'
                    if (isContentLengthInvalid(headers)) {
                        write413(ctx, stream, streamDependency, weight, exclusive);
                        // ignore content data
                        return;
                    } else {
                        requiredContentLengthValid = false;
                        write100(ctx, stream, streamDependency, weight, exclusive);
                        headers.remove(EXPECT);
                    }
                } else {
                    // has 'expect' header but value is not '100-continue'
                    // ignore content data
                    write417(ctx, stream, streamDependency, weight, exclusive);
                    return;
                }
            }

            if (requiredContentLengthValid && isContentLengthInvalid(headers)) {
                write413(ctx, stream, streamDependency, weight, exclusive);
                return;
            }

            // Time to First Byte
            headers.addLong(Constants.TTFB, System.currentTimeMillis());

            try {
                handler.accept(request = Http2RequestHandleImpl.from(runtime,
                        ctx,
                        encoder,
                        headers,
                        stream,
                        streamDependency,
                        weight,
                        exclusive));
                runtime.metrics().reportRequest(request);
            } catch (Throwable t) {
                if (request != null) {
                    error(null, request, t, r -> {
                        if (!r.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                () -> toErrorMsg(r.response(), t), false)) {
                            Loggers.logger()
                                    .warn("Error while accepting {}", r, t);
                        }
                    });
                    return;
                } else {
                    Loggers.logger().error("Error while accepting http2 request", t);
                    stream.close();
                    return;
                }
            }
        } else {
            standardHttp2Headers(headers);
            if (!handleTrailer(stream, request, new Http2HeadersImpl(headers))) {
                return;
            }
        }

        if (endOfStream) {
            end(stream, request);
        } else {
            setHandler(stream, request);
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx,
                          int streamId,
                          ByteBuf data,
                          int padding,
                          boolean endOfStream) {
        Http2Stream stream = encoder.connection().stream(streamId);
        Http2RequestHandleImpl request = getCurrentRequest(stream);
        if (request == null) {
            // ignored content
            return data.readableBytes() + padding;
        }

        boolean dataHandled = true;
        final int readableBytes = data.readableBytes();

        // The req.bytes is logically divided to positive one, negative one and zero
        // 1. The positive one presenting the chunk size left, which means we can only read num of req.bytes data
        //    limited by content length, and the oversize bytes will be discarded.
        // 2. The negative one presenting the num of bytes that we have read, which would be limited by the
        //    ServerOptions#maxContentLength,  and the exceeded bytes will be discarded.
        // 3. Zero presenting that all the coming data should be discarded
        if (request.bytes > 0L) {
            // content length present in header, and maxContentLimit has been handled
            if (readableBytes > request.bytes) {
                // oversize
                dataHandled = handleData(stream, request, data.readSlice((int) request.bytes));
                request.bytes = 0L;
            } else {
                dataHandled = handleData(stream, request, data);
                request.bytes -= readableBytes;
            }
        } else if (request.bytes < 0L) {
            if (runtime.options().getMaxContentLength() > 0L) {
                // content length absent in header, so handle the maxContentLimit
                final long limit = request.bytes + runtime.options().getMaxContentLength() + 1L;
                if (readableBytes > limit) {
                    // exceeded
                    if ((dataHandled = handleData(stream, request, data.readSlice((int) limit)))) {
                        TooLongFrameException t = new TooLongFrameException("content length exceeded "
                                + runtime.options().getMaxContentLength() + " bytes.");
                        final Http2ResponseImpl response = request.response();
                        if (!response.tryEnd(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, () -> {
                            response.headers().clear();
                            response.headers().setLong(CONTENT_LENGTH, 0L);
                            return Unpooled.EMPTY_BUFFER;
                        }, false)) {
                            Loggers.logger().error("Request entity too large.", t);
                        }
                        error(stream, request, t, null);
                    }
                } else {
                    dataHandled = handleData(stream, request, data);
                    request.bytes -= readableBytes;
                }
            } else {
                // req.bytes is -1L, no limit
                dataHandled = handleData(stream, request, data);
            }

        }
        // else
        // req.bytes is 0L, just discard it
        if (endOfStream && dataHandled) {
            end(stream, request);
        }
        return readableBytes + padding;
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        final Http2Stream stream = encoder.connection().stream(streamId);
        final Http2RequestHandleImpl req = removeRequest(stream);
        if (req != null) {
            final Http2Exception t =
                    Http2Exception.streamError(streamId, Http2Error.valueOf(errorCode), "Stream reset.");
            if (!req.response().tryEndWithCrash(t)) {
                Loggers.logger()
                        .warn("Stream({}) reset, but {} has been committed", stream.id(), req.response());
            }
            error(null, req, t, null);
        }
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {
        final Http2RequestHandleImpl req = removeRequest(stream);
        if (req != null) {
            final Http2Exception t =
                    Http2Exception.streamError(stream.id(), Http2Error.STREAM_CLOSED, "Stream removed.");
            if (!req.response().tryEndWithCrash(t)) {
                Loggers.logger()
                        .warn("Stream({}) removed, but {} has been committed", stream.id(), req.response());
            }
            error(null, req, t, null);
        }
    }

    private boolean handleData(Http2Stream stream, Http2RequestHandleImpl req, ByteBuf data) {
        try {
            req.handleContent(data);
            return true;
        } catch (Throwable t) {
            error(stream, req, t, r -> {
                if (!req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        () -> toErrorMsg(r.response(), t), false)) {
                    Loggers.logger()
                            .warn("Error while handing content: {}", t, req);
                }
            });
            return false;
        }
    }

    private boolean handleTrailer(Http2Stream stream, Http2RequestHandleImpl req, HttpHeaders trailer) {
        if (trailer != null && !trailer.isEmpty()) {
            try {
                req.handleTrailer(trailer);
            } catch (Throwable t) {
                error(stream, req, t, r -> {
                    if (!req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            () -> toErrorMsg(r.response(), t), false)) {
                        Loggers.logger()
                                .warn("Error while handing trailers: {}", t, req);
                    }
                });
                return false;
            }
        }
        return true;
    }

    private void end(Http2Stream stream, Http2RequestHandleImpl req) {
        assert req != null;
        req.isEnded = true;
        try {
            req.handleEnd();
            removeRequest(stream);
        } catch (Throwable t) {
            error(stream, req, t, r -> {
                if (req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        () -> toErrorMsg(req.response(), t), false)) {
                    Loggers.logger()
                            .warn("Error while handing {}", t, req);
                }
            });
        }
    }

    private void error(Http2Stream stream,
                       Http2RequestHandleImpl req,
                       Throwable err,
                       Consumer<Http2RequestHandleImpl> after) {

        if (stream != null) {
            removeRequest(stream);
        }

        if (!req.isEnded) {
            req.isEnded = true;
        }

        Throwable causeToCloseStream = null;

        try {
            req.handleError(err);
        } catch (Throwable t) {
            Loggers.logger().error("Error while handing {}," +
                    " but another error was thrown by the onError() handler.", req, t);
            causeToCloseStream = t;
        }

        if (after != null) {
            after.accept(req);
        }

        if (causeToCloseStream != null && !req.response().isCommitted()) {
            req.stream.close();
        }
    }

    private Http2RequestHandleImpl getCurrentRequest(Http2Stream stream) {
        return stream.getProperty(messageKey);
    }

    private Http2RequestHandleImpl removeRequest(Http2Stream stream) {
        return stream.removeProperty(messageKey);
    }

    private void setHandler(Http2Stream stream, Http2RequestHandleImpl request) {
        Http2RequestHandleImpl p = stream.setProperty(messageKey, request);
        if (p != null) {
            p.handleError(new IllegalStateException("Unexpected error"));
        }
    }

    private boolean isShutdown() {
        return runtime.shutdownStatus().get();
    }

    private boolean isContentLengthInvalid(Http2Headers start) {
        long contentLength;
        return runtime.options().getMaxContentLength() > 0L
                && (contentLength = start.getLong(CONTENT_LENGTH, -1L)) >= 0L
                && contentLength > runtime.options().getMaxContentLength();
    }

    private void write100(ChannelHandlerContext ctx,
                          Http2Stream stream,
                          int streamDependency,
                          short weight,
                          boolean exclusive) {
        encoder.writeHeaders(ctx,
                stream.id(),
                CONTINUE,
                streamDependency,
                weight,
                exclusive,
                0,
                false,
                ctx.newPromise())
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        Loggers.logger().warn("Failed to send a 100 Continue.",
                                f.cause());
                        stream.close();
                    } else if (isShutdown() && ctx.channel().isActive()) {
                        ctx.channel().close();
                    }
                });
    }

    private void write413(ChannelHandlerContext ctx,
                          Http2Stream stream,
                          int streamDependency,
                          short weight,
                          boolean exclusive) {
        encoder.writeHeaders(ctx,
                stream.id(),
                TOO_LARGE,
                streamDependency,
                weight,
                exclusive,
                0,
                true,
                ctx.newPromise())
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        Loggers.logger().warn("Failed to send a 413 Request Entity Too Large.",
                                f.cause());
                        stream.close();
                    } else if (isShutdown() && ctx.channel().isActive()) {
                        ctx.channel().close();
                    }
                });
        // release all resources corresponding current stream
        stream.close();
        removeRequest(stream);
    }

    private void write417(ChannelHandlerContext ctx,
                          Http2Stream stream,
                          int streamDependency,
                          short weight,
                          boolean exclusive) {
        encoder.writeHeaders(ctx,
                stream.id(),
                EXPECTATION_FAILED,
                streamDependency,
                weight,
                exclusive,
                0,
                true,
                ctx.newPromise())
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        Loggers.logger().warn("Failed to send a 417 Continue.",
                                f.cause());
                        stream.close();
                    } else if (isShutdown() && ctx.channel().isActive()) {
                        ctx.channel().close();
                    }
                });
        // release all resources corresponding current stream
        stream.close();
        removeRequest(stream);
    }
}
