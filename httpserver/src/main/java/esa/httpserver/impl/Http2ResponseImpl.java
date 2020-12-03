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

import esa.commons.ExceptionUtils;
import esa.commons.http.HttpMethod;
import esa.httpserver.core.Response;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import static esa.httpserver.impl.Utils.toByteBuf;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;

final class Http2ResponseImpl extends BaseResponse<Http2RequestHandleImpl> implements Response {

    private static final AttributeKey<Object> CLOSE_LOCK = AttributeKey.valueOf("$closeOnShutdown");
    /**
     * A {@link ChannelFutureListener} that closes the {@link Channel} which is associated with the specified {@link
     * ChannelFuture}.
     */
    private static final ChannelFutureListener CLOSE = future -> {
        final Channel ch = future.channel();
        if (ch.isActive() && ch.attr(CLOSE_LOCK).compareAndSet(null, Boolean.TRUE)) {
            future.channel().close();
        }
    };

    private final Http2HeadersImpl headers = new Http2HeadersImpl();
    private final Http2ConnectionEncoder encoder;
    private final int streamDependency;
    private final short weight;
    private final boolean exclusive;
    private Http2HeadersImpl trailers;

    Http2ResponseImpl(Http2RequestHandleImpl req,
                      Http2ConnectionEncoder encoder,
                      int streamDependency,
                      short weight,
                      boolean exclusive) {
        super(req);
        this.encoder = encoder;
        this.streamDependency = streamDependency;
        this.weight = weight;
        this.exclusive = exclusive;
    }

    @Override
    public Http2HeadersImpl headers() {
        return headers;
    }

    @Override
    public Http2HeadersImpl trailers() {
        if (trailers == null) {
            trailers = new Http2HeadersImpl();
        }
        return trailers;
    }

    @Override
    public boolean isWritable() {
        if (inEventLoop()) {
            return encoder.flowController().isWritable(request.stream);
        } else {
            return ctx().channel().isWritable();
        }
    }

    @Override
    Future<Void> doWrite(byte[] data, int offset, int length, boolean writeHead) {
        if (writeHead) {
            buildHeaders(-1L);
            if (inEventLoop()) {
                ChannelPromise promise = ctx().newPromise();
                doWriteHeaders(headers.unwrap(), false, promise);
                if (length > 0) {
                    promise = ctx().newPromise();
                    doWriteData(toByteBuf(alloc(), data, offset, length), false, promise);
                }
                flush();
                return promise;
            } else if (length == 0) {
                final ChannelPromise promise = ctx().newPromise();
                safeRunInChannel(() -> {
                    doWriteHeaders(headers.unwrap(), false, promise);
                    flush();
                }, promise, null);
                return promise;
            } else {
                final ChannelPromise promise = ctx().newPromise();
                safeRunInChannel(() -> {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    doWriteData(toByteBuf(alloc(), data, offset, length), false, promise);
                    flush();
                }, promise, data);
                return promise;
            }
        } else if (length == 0) {
            return ctx().newSucceededFuture();
        } else if (inEventLoop()) {
            ChannelFuture f =
                    doWriteData(toByteBuf(alloc(), data, offset, length), false, ctx().newPromise());
            flush();
            return f;
        } else {
            final ChannelPromise promise = ctx().newPromise();
            safeRunInChannel(() -> {
                doWriteData(toByteBuf(alloc(), data, offset, length), false, promise);
                flush();
            }, promise, data);
            return promise;
        }
    }

    @Override
    Future<Void> doWrite(ByteBuf data, boolean writeHead) {
        if (writeHead) {
            buildHeaders(-1L);
            if (inEventLoop()) {
                ChannelPromise promise = ctx().newPromise();
                doWriteHeaders(headers.unwrap(), false, promise);
                if (data.isReadable()) {
                    doWriteData(data, false, promise = ctx().newPromise());
                } else {
                    data.release();
                }
                flush();
                return promise;
            } else if (data.isReadable()) {
                final ChannelPromise promise = ctx().newPromise();
                safeRunInChannel(() -> {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    doWriteData(data, false, promise);
                    flush();
                }, promise, data);
                return promise;
            } else {
                final ChannelPromise promise = ctx().newPromise();
                data.release();
                safeRunInChannel(() -> {
                    doWriteHeaders(headers.unwrap(), false, promise);
                    flush();
                }, promise, null);
                return promise;
            }
        } else if (data.isReadable()) {
            if (inEventLoop()) {
                ChannelFuture f = doWriteData(data, false, ctx().newPromise());
                flush();
                return f;
            } else {
                final ChannelPromise promise = ctx().newPromise();
                safeRunInChannel(() -> {
                    doWriteData(data, false, promise);
                    flush();
                }, promise, data);
                return promise;
            }
        } else {
            data.release();
            return ctx().newSucceededFuture();
        }
    }

    @Override
    void doEnd(ByteBuf data, boolean writeHead, boolean forceClose) {
        final boolean isTrailerAbsent = isTrailerAbsent();

        if (writeHead) {
            final int bytesToWrite = data.readableBytes();
            buildHeaders(bytesToWrite);
            if (inEventLoop()) {
                if (bytesToWrite == 0 && isTrailerAbsent) {
                    doWriteHeaders(headers.unwrap(), true, endPromise);
                    data.release();
                } else if (isTrailerAbsent) {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    doWriteData(data, true, endPromise);
                } else {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    doWriteData(data, false, ctx().newPromise());
                    doWriteHeaders(trailers().unwrap(), true, endPromise);
                }
                flush();
            } else {
                if (bytesToWrite == 0 && isTrailerAbsent) {
                    data.release();
                    safeRunInChannel(() -> {
                        doWriteHeaders(headers.unwrap(), true, this.endPromise);
                        flush();
                    }, endPromise, null);
                } else if (isTrailerAbsent) {
                    safeRunInChannel(() -> {
                        doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                        doWriteData(data, true, this.endPromise);
                        flush();
                    }, endPromise, data);
                } else {
                    safeRunInChannel(() -> {
                        doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                        doWriteData(data, false, ctx().newPromise());
                        doWriteHeaders(trailers().unwrap(), true, this.endPromise);
                        flush();
                    }, endPromise, data);
                }
            }
        } else if (inEventLoop()) {
            if (isTrailerAbsent) {
                doWriteData(data, true, endPromise);
            } else {
                doWriteData(data, false, ctx().newPromise());
                doWriteHeaders(trailers().unwrap(), true, endPromise);
            }
            flush();
        } else {
            if (isTrailerAbsent) {
                safeRunInChannel(() -> {
                    doWriteData(data, true, this.endPromise);
                    flush();
                }, endPromise, data);
            } else {
                safeRunInChannel(() -> {
                    doWriteData(data, false, ctx().newPromise());
                    doWriteHeaders(trailers().unwrap(), true, this.endPromise);
                    flush();
                }, endPromise, data);
            }
        }
    }

    @Override
    void doEnd(byte[] data, int offset, int length, boolean writeHead) {
        final boolean isTrailerAbsent = isTrailerAbsent();

        if (writeHead) {
            buildHeaders(length);
            if (inEventLoop()) {
                if (length == 0 && isTrailerAbsent) {
                    doWriteHeaders(headers.unwrap(), true, endPromise);
                } else if (isTrailerAbsent) {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    doWriteData(toByteBuf(alloc(), data, offset, length), true, endPromise);
                } else {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    doWriteData(toByteBuf(alloc(), data, offset, length), false, ctx().newPromise());
                    doWriteHeaders(trailers().unwrap(), true, endPromise);
                }
                flush();
            } else {
                if (length == 0 && isTrailerAbsent) {
                    safeRunInChannel(() -> {
                        doWriteHeaders(headers.unwrap(), true, endPromise);
                        flush();
                    }, endPromise, null);
                } else if (isTrailerAbsent) {
                    safeRunInChannel(() -> {
                        doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                        doWriteData(toByteBuf(alloc(), data, offset, length), true, endPromise);
                        flush();
                    }, endPromise, data);
                } else {
                    safeRunInChannel(() -> {
                        doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                        doWriteData(toByteBuf(alloc(), data, offset, length), false, ctx().newPromise());
                        doWriteHeaders(trailers().unwrap(), true, endPromise);
                        flush();
                    }, endPromise, data);
                }
            }
        } else if (inEventLoop()) {
            if (isTrailerAbsent) {
                doWriteData(toByteBuf(alloc(), data, offset, length), true, endPromise);
            } else {
                doWriteData(toByteBuf(alloc(), data, offset, length), false, ctx().newPromise());
                doWriteHeaders(trailers().unwrap(), true, endPromise);
            }
            flush();
        } else {
            if (isTrailerAbsent) {
                safeRunInChannel(() -> {
                    doWriteData(toByteBuf(alloc(), data, offset, length), true, endPromise);
                    flush();
                }, endPromise, data);
            } else {
                safeRunInChannel(() -> {
                    doWriteData(toByteBuf(alloc(), data, offset, length), false, ctx().newPromise());
                    doWriteHeaders(trailers().unwrap(), true, endPromise);
                    flush();
                }, endPromise, data);
            }
        }
    }

    @Override
    void doSendFile(File file, long position, long len) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            buildHeaders(len);
            final Http2ChunkedInput chunkedInput =
                    new Http2ChunkedInput(new ChunkedFile(raf, position, len, 8192),
                            isTrailerAbsent() ? null : trailers.unwrap(),
                            request.stream.id(),
                            streamDependency,
                            weight,
                            exclusive);

            if (inEventLoop()) {
                doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                // use the last handler to write large file
                ctx().channel().writeAndFlush(chunkedInput, endPromise);
            } else {
                safeRunInChannel(() -> {
                    doWriteHeaders(headers.unwrap(), false, ctx().newPromise());
                    // use the last handler to write large file
                    ctx().channel().writeAndFlush(chunkedInput, endPromise);
                }, endPromise, chunkedInput);
            }
        } catch (Throwable e) {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
            if (inEventLoop()) {
                request.stream.close();
            } else {
                ctx().channel().eventLoop().execute(request.stream::close);
            }
            ExceptionUtils.throwException(e);
        }
    }

    @Override
    ChannelFutureListener closure() {
        return CLOSE;
    }

    private ChannelFuture doWriteData(ByteBuf data, boolean endStream, ChannelPromise promise) {
        return encoder.writeData(ctx(),
                request.stream.id(), data, 0, endStream, promise);
    }

    private void doWriteHeaders(Http2Headers headers, boolean endStream, ChannelPromise promise) {
        // write underlying
        encoder.writeHeaders(ctx(),
                request.stream.id(),
                headers,
                streamDependency,
                weight,
                exclusive,
                0,
                endStream,
                promise);
    }

    private void buildHeaders(long contentLength) {
        headers.unwrap().status(Integer.toString(status));

        if (contentLength > 0L) {
            headers.setLong(CONTENT_LENGTH, contentLength);
        } else {
            if (request.method().equals(HttpMethod.HEAD)
                    || status == HttpResponseStatus.NOT_MODIFIED.code()
                    || HttpStatusClass.INFORMATIONAL.equals(HttpStatusClass.valueOf(status))) {
                headers.remove(TRANSFER_ENCODING);
            } else if (status == HttpResponseStatus.RESET_CONTENT.code()) {
                headers.set(CONTENT_LENGTH, "0");
            }
        }
    }

    private boolean isTrailerAbsent() {
        return trailers == null || trailers.isEmpty();
    }

    private void flush() {
        ctx().channel().flush();
    }

    private boolean inEventLoop() {
        return ctx().channel().eventLoop().inEventLoop();
    }

    private void safeRunInChannel(Runnable r, ChannelPromise promise, Object data) {
        Utils.safeRunInChannel(ctx(), r, promise, data);
    }
}
