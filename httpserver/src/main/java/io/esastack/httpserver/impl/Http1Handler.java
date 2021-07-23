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

import esa.commons.http.HttpHeaders;
import esa.commons.netty.http.EmptyHttpHeaders;
import esa.commons.netty.http.Http1HeadersAdaptor;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.utils.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.*;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static io.esastack.httpserver.impl.Utils.handleException;
import static io.esastack.httpserver.impl.Utils.handleIdle;
import static io.esastack.httpserver.impl.Utils.toErrorMsg;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPECT;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

final class Http1Handler extends SimpleChannelInboundHandler<HttpObject> {

    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(
            HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse CONTINUE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE_CLOSE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);

    private final Consumer<RequestHandle> handler;
    private final ServerRuntime runtime;
    private Http1RequestHandleImpl current;
    private long chunkSize = -1L;

    static {
        EXPECTATION_FAILED.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE_CLOSE.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE_CLOSE.headers().set(CONNECTION, CLOSE);
    }

    Http1Handler(ServerRuntime runtime,
                 Consumer<RequestHandle> handler) {
        this.runtime = runtime;
        this.handler = handler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg.decoderResult().isFailure()) {
            decodeError(ctx, msg);
            return;
        }

        if (msg instanceof HttpRequest) {
            final HttpRequest start = (HttpRequest) msg;
            final HttpVersion protocol = start.protocolVersion();
            boolean requiredContentLengthValid = true;
            if (protocol.compareTo(HttpVersion.HTTP_1_1) >= 0) {
                final String expectValue = start.headers().get(EXPECT);
                if (expectValue != null) {
                    if (HttpHeaderValues.CONTINUE.toString().equalsIgnoreCase(expectValue)) {
                        // has 'expect: 100-continue' header'
                        if (isContentLengthInvalid(start)) {
                            write413(ctx, true, TOO_LARGE.retainedDuplicate());
                            // ignore content data
                            return;
                        } else {
                            requiredContentLengthValid = false;
                            write100(ctx);
                            start.headers().remove(EXPECT);
                        }
                    } else {
                        // has 'expect' header but value is not '100-continue'
                        // ignore content data
                        write417(ctx, isKeepAlive(start));
                        return;
                    }
                }
            }

            if (requiredContentLengthValid && isContentLengthInvalid(start)) {
                // content length oversize
                final boolean keepalive = isKeepAlive(start);
                final FullHttpResponse tooLarge = keepalive ? TOO_LARGE : TOO_LARGE_CLOSE;
                // ignore content data
                write413(ctx, keepalive, tooLarge.retainedDuplicate());
                resetNow();
                return;
            }

            try {
                handler.accept(current = new Http1RequestHandleImpl(runtime, ctx, start, isKeepAlive(start)));
                runtime.metrics().reportRequest(current);
            } catch (Throwable t) {
                error(t, null, req -> {
                    if (!req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            () -> toErrorMsg(req.response(), t), false)) {
                        Loggers.logger()
                                .warn("Error while accepting {}", req, t);
                    }
                });
            }
        } else if (msg instanceof HttpContent) {
            if (current == null) {
                //discard data until the begging of the next req
                return;
            }

            if (msg == LastHttpContent.EMPTY_LAST_CONTENT) {
                end();
                return;
            }

            ByteBuf buf = ((HttpContent) msg).content();
            // check maxContentSize limit
            if (chunkSize >= 0) {
                if (buf.readableBytes() > chunkSize) {
                    buf = buf.readSlice((int) chunkSize);
                    handleData(buf);
                    final TooLongFrameException t = new TooLongFrameException("content length exceeded "
                            + runtime.options().getMaxContentLength() + " bytes.");
                    error(t, r -> {
                        if (!r.response().tryEnd(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, () -> {
                            r.response().headers().clear();
                            return Unpooled.EMPTY_BUFFER;
                        }, false)) {
                            Loggers.logger().error("Request entity too large.", t);
                        }
                    }, null);
                    return;
                } else {
                    chunkSize -= buf.readableBytes();
                }
            }

            if (!handleData(((HttpContent) msg).content())) {
                // failed to handle data
                return;
            }

            if (msg instanceof LastHttpContent) {
                if (((LastHttpContent) msg).trailingHeaders().isEmpty()) {
                    if (handleTrailer(EmptyHttpHeaders.INSTANCE)) {
                        end();
                    }
                } else {
                    if (handleTrailer(new Http1HeadersAdaptor(((LastHttpContent) msg).trailingHeaders()))) {
                        end();
                    }
                }
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        // We might need keep reading the channel until the message is aggregated.
        //
        // See https://github.com/netty/netty/issues/6583
        if (current != null && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            // trigger handler.error() if it is not null as it may be a left-over
            super.channelInactive(ctx);
        } finally {
            handleChannelInactive(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        try {
            // trigger handler.error() if it is not null as it may be a left-over
            super.handlerRemoved(ctx);
        } finally {
            handleChannelInactive(ctx);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!handleIdle(ctx, evt)) {
            // propagate to next
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // trigger onError firstly, because we will close this channel in handleException(ctx, cause) which will fire
        // a channel inactive event.
        error(cause, r -> {
            if (!r.response().tryEndWithCrash(cause)) {
                Loggers.logger()
                        .warn("Unexpected channel exception caught, but {} has been committed",
                                r.response(), ctx.channel());
            }
        }, null);
        handleException(ctx, cause);
    }

    private void decodeError(ChannelHandlerContext ctx, HttpObject msg) {
        Loggers.logger().error("{} decoding error",
                ctx.channel(), msg.decoderResult().cause());

        if (current == null) {
            write400(ctx, msg.decoderResult());
        } else {
            error(msg.decoderResult().cause(), r -> {
                if (!r.response().tryEnd(HttpResponseStatus.BAD_REQUEST, () -> {
                    final ByteBuf err =
                            Unpooled.copiedBuffer(msg.decoderResult().toString()
                                    .getBytes(StandardCharsets.UTF_8));
                    // clear headers before
                    r.response().headers().clear();
                    r.response().headers().set(CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
                    return err;
                }, true)) {
                    Loggers.logger()
                            .warn("Decoding error occurred, but {} has been committed",
                                    r.response(), msg.decoderResult().cause());
                }
            }, null);
        }
    }

    private boolean isKeepAlive(HttpRequest start) {
        return runtime.options().isKeepAliveEnable()
                && !runtime.shutdownStatus().get()
                && HttpUtil.isKeepAlive(start);
    }

    private boolean handleData(ByteBuf data) {
        try {
            current.handleContent(data);
            return true;
        } catch (Throwable t) {
            error(t, null, req -> {
                if (!req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        () -> toErrorMsg(req.response(), t), false)) {
                    Loggers.logger()
                            .warn("Error while handing content: {}", req, t);
                }
            });
        }
        return false;
    }

    private boolean handleTrailer(HttpHeaders trailer) {
        if (trailer != null && !trailer.isEmpty()) {
            try {
                current.handleTrailer(trailer);
                return true;
            } catch (Throwable t) {
                error(t, null, req -> {
                    if (!req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            () -> toErrorMsg(req.response(), t), false)) {
                        Loggers.logger()
                                .warn("Error while handing trailers: {}", req, t);
                    }
                });
                return false;
            }
        }
        return true;
    }

    private void end() {
        assert current != null;
        final Http1RequestHandleImpl req = current;
        req.isEnded = true;
        try {
            req.handleEnd();
            resetNow();
        } catch (Throwable t) {
            error(t, null, r -> {
                if (req.response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        () -> toErrorMsg(req.response(), t), false)) {
                    Loggers.logger()
                            .warn("Error while handing {}", req, t);
                }
            });
        }
    }

    private void error(Throwable err,
                       Consumer<Http1RequestHandleImpl> before,
                       Consumer<Http1RequestHandleImpl> after) {
        final Http1RequestHandleImpl req = current;
        if (req != null) {
            // reset first and then calling handleError()
            // avoid triggering error() twice in the handleError() calling
            resetNow();

            if (before != null) {
                before.accept(req);
            }

            if (!req.isEnded) {
                req.isEnded = true;
            }

            Throwable causeToCloseChannel = null;
            try {
                req.handleError(err);
            } catch (Throwable t) {
                Loggers.logger().error("Error while handing {}," +
                        " but another error was thrown by the onError() handler.", req, t);
                causeToCloseChannel = t;
            }

            if (after != null) {
                after.accept(req);
            }

            if (causeToCloseChannel != null && req.ctx.channel().isActive()) {
                req.ctx.channel().close();
            }
        }
    }

    private boolean isContentLengthInvalid(HttpMessage start) {
        if (runtime.options().getMaxContentLength() > 0L) {
            long contentLength;
            try {
                String value = start.headers().get(CONTENT_LENGTH);
                if (value != null) {
                    contentLength = Long.parseLong(value);
                } else {
                    contentLength = -1L;
                }
            } catch (final NumberFormatException e) {
                contentLength = -1L;
            }

            if (contentLength >= 0L) {
                // content-length present in header
                return contentLength > runtime.options().getMaxContentLength();
            } else {
                // content-length absent in header, so we need to check the bytes read every time HttpContent is coming
                chunkSize = runtime.options().getMaxContentLength();
                return false;
            }
        }
        return false;
    }

    private void write100(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(CONTINUE.retainedDuplicate()).addListener(f -> {
            if (!f.isSuccess()) {
                Loggers.logger().warn("Failed to send a 100 Continue.",
                        f.cause());
                ctx.channel().close();
            }
        });
    }

    private void write413(ChannelHandlerContext ctx, boolean keepalive, FullHttpResponse tooLarge) {
        ctx.writeAndFlush(tooLarge).addListener(f -> {
            if (!f.isSuccess()) {
                Loggers.logger().warn("Failed to send a 413 Request Entity Too Large.",
                        f.cause());
                ctx.channel().close();
            } else if (!keepalive) {
                ctx.channel().close();
            }
        });
    }

    private void write417(ChannelHandlerContext ctx, boolean keepalive) {
        ctx.writeAndFlush(EXPECTATION_FAILED.retainedDuplicate()).addListener(f -> {
            if (!f.isSuccess()) {
                Loggers.logger().warn("Failed to send a 417 Expectation Failed.",
                        f.cause());
                ctx.channel().close();
            } else if (!keepalive) {
                ctx.channel().close();
            }
        });
    }

    private void write400(ChannelHandlerContext ctx, DecoderResult msg) {
        final ByteBuf err =
                Unpooled.copiedBuffer(msg.toString()
                        .getBytes(StandardCharsets.UTF_8));
        FullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.BAD_REQUEST,
                        err);
        response.headers().set(CONTENT_LENGTH, err.readableBytes());
        response.headers().set(CONNECTION, CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleChannelInactive(ChannelHandlerContext ctx) {
        if (current != null) {
            final ChannelException t =
                    new ChannelException("Channel " + ctx.channel() + " INACTIVE");
            error(t, req -> {
                if (!req.response().tryEndWithCrash(t)) {
                    Loggers.logger()
                            .warn("Channel {} inactive, but response has been committed", ctx.channel());
                }
            }, null);
        }
    }

    private void resetNow() {
        current = null;
        chunkSize = -1L;
    }
}
