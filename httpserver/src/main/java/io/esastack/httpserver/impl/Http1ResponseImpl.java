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

import esa.commons.ExceptionUtils;
import io.esastack.commons.net.http.HttpMethod;
import io.esastack.commons.net.netty.http.Http1HeadersImpl;
import io.esastack.httpserver.core.Response;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static io.esastack.httpserver.impl.Utils.toByteBuf;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;

final class Http1ResponseImpl extends BaseResponse<Http1RequestHandleImpl> implements Response {

    private final Http1HeadersImpl headers = new Http1HeadersImpl();
    private final boolean isKeepAlive;
    private Http1HeadersImpl trailers;

    Http1ResponseImpl(Http1RequestHandleImpl req,
                      boolean isKeepAlive) {
        super(req);
        this.isKeepAlive = isKeepAlive;
    }

    @Override
    public Http1HeadersImpl headers() {
        return headers;
    }

    @Override
    public boolean isKeepAlive() {
        return isKeepAlive && request.runtime.isRunning();
    }

    @Override
    public Http1HeadersImpl trailers() {
        if (trailers == null) {
            trailers = new Http1HeadersImpl();
        }
        return trailers;
    }

    @Override
    Future<Void> doWrite(byte[] data, int offset, int length, boolean writeHead) {
        if (writeHead) {
            if (inEventLoop()) {
                ChannelPromise promise = ctx().newPromise();
                ctx().write(buildResponseHead(-1L), promise);
                if (length == 0) {
                    ctx().flush();
                } else {
                    promise = ctx().newPromise();
                    ctx().writeAndFlush(new DefaultHttpContent(toByteBuf(alloc(), data, offset, length)), promise);
                }
                return promise;
            } else if (length == 0) {
                return ctx().write(buildResponseHead(-1L));
            } else {
                final ChannelPromise promise = ctx().newPromise();
                final DefaultHttpResponse head = buildResponseHead(-1L);
                safeRunInChannel(() -> {
                    ctx().write(head, ctx().newPromise());
                    ctx().writeAndFlush(new DefaultHttpContent(toByteBuf(alloc(), data, offset, length)), promise);
                }, promise, null);
                return promise;
            }
        } else if (length == 0) {
            return ctx().newSucceededFuture();
        } else if (inEventLoop()) {
            return ctx().writeAndFlush(new DefaultHttpContent(toByteBuf(alloc(), data, offset, length)));
        } else {
            final ChannelPromise promise = ctx().newPromise();
            safeRunInChannel(() ->
                            ctx().writeAndFlush(new DefaultHttpContent(
                                    toByteBuf(alloc(), data, offset, length)), promise),
                    promise, null);
            return promise;
        }
    }

    @Override
    Future<Void> doWrite(ByteBuf data, boolean writeHead) {
        if (writeHead) {
            if (inEventLoop()) {
                ChannelPromise promise = ctx().newPromise();
                ctx().write(buildResponseHead(-1L), promise);
                if (data.isReadable()) {
                    promise = ctx().newPromise();
                    ctx().writeAndFlush(new DefaultHttpContent(data), promise);
                } else {
                    data.release();
                    ctx().flush();
                }
                return promise;
            } else if (data.isReadable()) {
                final ChannelPromise promise = ctx().newPromise();
                final DefaultHttpResponse head = buildResponseHead(-1L);
                safeRunInChannel(() -> {
                    ctx().write(head, ctx().newPromise());
                    ctx().writeAndFlush(new DefaultHttpContent(data), promise);
                }, promise, data);
                return promise;
            } else {
                data.release();
                return ctx().write(buildResponseHead(-1L));
            }
        } else if (data.isReadable()) {
            // whatever it is in event loop, just write it
            return ctx().writeAndFlush(new DefaultHttpContent(data));
        } else {
            data.release();
            return ctx().newSucceededFuture();
        }
    }

    @Override
    void doEnd(ByteBuf data, boolean writeHead, boolean forceClose) {
        if (writeHead) {
            // header hasn't been written
            standardHeaders(data.readableBytes(), forceClose);
            ctx().writeAndFlush(buildFullResponse(data), endPromise);
        } else {
            final LastHttpContent last = getLastHttpContent(data);
            if (last == LastHttpContent.EMPTY_LAST_CONTENT) {
                data.release();
            }
            ctx().writeAndFlush(last, endPromise);
        }
    }

    @Override
    void doEnd(byte[] data, int offset, int length, boolean writeHead) {
        if (writeHead) {
            // header hasn't been written
            standardHeaders(length, false);
            if (inEventLoop()) {
                ctx().writeAndFlush(buildFullResponse(toByteBuf(alloc(), data, offset, length)), endPromise);
            } else {
                safeRunInChannel(() ->
                                ctx().writeAndFlush(
                                        buildFullResponse(toByteBuf(alloc(), data, offset, length)), this.endPromise),
                        endPromise, null);
            }
        } else if (inEventLoop()) {
            ctx().writeAndFlush(getLastHttpContent(toByteBuf(alloc(), data, offset, length)), endPromise);
        } else {
            safeRunInChannel(() ->
                            ctx().writeAndFlush(
                                    getLastHttpContent(toByteBuf(alloc(), data, offset, length)), this.endPromise),
                    endPromise, null);
        }
    }

    private DefaultFullHttpResponse buildFullResponse(ByteBuf data) {
        return new DefaultFullHttpResponse(request.req.protocolVersion(),
                HttpResponseStatus.valueOf(status),
                data,
                headers,
                trailers == null ? EmptyHttpHeaders.INSTANCE : trailers);
    }

    @Override
    void doSendFile(File file, long position, long len) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            // Write the initial line and the header.
            ctx().write(buildResponseHead(len));
            if (supportFileRegion()) {
                // use zero-copy
                // TODO: write file segment by segment
                ctx().write(new DefaultFileRegion(raf.getChannel(), position, len));
                ctx().writeAndFlush(getLastHttpContent(), endPromise);

            } else {
                // HttpChunkedInput already has the the end marker
                ctx().writeAndFlush(new HttpChunkedInput(
                        new ChunkedFile(raf, position, len, 8192), getLastHttpContent()), endPromise);
            }
        } catch (Throwable e) {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }

            // should not happen
            try {
                ctx().channel().close();
            } catch (Exception ignored) {
            } finally {
                ExceptionUtils.throwException(e);
            }
        }
    }

    @Override
    ChannelFutureListener closure() {
        return ChannelFutureListener.CLOSE;
    }

    private DefaultHttpResponse buildResponseHead(long contentLength) {
        standardHeaders(contentLength, false);
        return new DefaultHttpResponse(request.req.protocolVersion(),
                HttpResponseStatus.valueOf(status),
                headers);
    }

    private void standardHeaders(long contentLength, boolean forceClose) {
        if (isKeepAlive && !forceClose) {
            headers.remove(CONNECTION);
        } else {
            headers.set(CONNECTION, HttpHeaderValues.CLOSE);
        }

        if (request.method().equals(HttpMethod.HEAD) || status == HttpResponseStatus.NOT_MODIFIED.code()) {
            headers.remove(TRANSFER_ENCODING);
        } else if (contentLength < 0L) {
            if (!headers.contains(TRANSFER_ENCODING) && !headers.contains(CONTENT_LENGTH)) {
                headers.set(TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
        } else {
            headers.setLong(CONTENT_LENGTH, contentLength);
        }
    }

    private boolean supportFileRegion() {
        return ctx().pipeline().get(SslHandler.class) == null
                && ctx().pipeline().get(HttpContentCompressor.class) == null;
    }

    private LastHttpContent getLastHttpContent() {
        if (isTrailerAbsent()) {
            return LastHttpContent.EMPTY_LAST_CONTENT;
        } else {
            return new AggregatedLastHttpContent(Unpooled.EMPTY_BUFFER, trailers);
        }
    }

    private LastHttpContent getLastHttpContent(ByteBuf data) {
        // header has been written
        final boolean trailerAbsent = isTrailerAbsent();
        if (trailerAbsent && data.readableBytes() == 0) {
            return LastHttpContent.EMPTY_LAST_CONTENT;
        } else {
            return new AggregatedLastHttpContent(data,
                    trailerAbsent ? EmptyHttpHeaders.INSTANCE : trailers);
        }
    }

    private boolean isTrailerAbsent() {
        return trailers == null || trailers.isEmpty();
    }

    private boolean inEventLoop() {
        return ctx().channel().eventLoop().inEventLoop();
    }

    private void safeRunInChannel(Runnable r, ChannelPromise promise, Object data) {
        Utils.safeRunInChannel(ctx(), r, promise, data);
    }

}
