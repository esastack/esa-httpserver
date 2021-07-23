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

import esa.commons.StringUtils;
import io.esastack.httpserver.utils.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ThrowableUtil;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;

final class Utils {

    static final byte[] EMPTY_BYTES = new byte[0];

    static final AttributeKey<SocketAddress> SOURCE_ADDRESS = AttributeKey.valueOf("$source.address");

    static boolean handleIdle(ChannelHandlerContext ctx, Object event) {
        if (event instanceof IdleStateEvent) {
            IdleStateEvent evt = (IdleStateEvent) event;
            if (evt.state() == IdleState.ALL_IDLE) {
                if (Loggers.logger().isDebugEnabled()) {
                    Loggers.logger().debug("Close timeout connection: {}", ctx.channel());
                }
                // use ctx.channel().close() to fire channelInactive event from the tail of pipeline instead of
                // ctx.close()
                ctx.channel().close();
                return true;
            }
        }
        return false;
    }

    static void handleException(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            if (Loggers.logger().isDebugEnabled()) {
                Loggers.logger().debug(
                        "Exception occurred in connection: {}, maybe client has closed connection.",
                        ctx.channel(), cause);
            }
        } else if (ctx.channel().isActive() && ctx.channel().isWritable()) {
            Loggers.logger().warn("Error occurred in connection: {}",
                    ctx.channel(), cause);
        }
        ctx.channel().close();
    }

    static void standardHttp2Headers(Http2Headers headers) {

        for (Http2Headers.PseudoHeaderName p : Http2Headers.PseudoHeaderName.values()) {
            headers.remove(p.value());
        }

        List<CharSequence> cookies = headers.getAllAndRemove(COOKIE);
        if (cookies != null && !cookies.isEmpty()) {
            headers.set(COOKIE, String.join("; ", cookies));
        }
    }

    static void tryRelease(ReferenceCounted target) {
        int refCnt = target.refCnt();
        if (refCnt > 0) {
            target.release();
        }
    }

    /**
     * Try to mark the {@link Promise} as success and log.
     */
    static <V> void trySuccess(Promise<? super V> p, V result) {
        if (!p.trySuccess(result)) {
            Throwable err = p.cause();
            if (err == null) {
                Loggers.logger()
                        .warn("Failed to mark a promise as success because it has succeeded already: {}", p);
            } else {
                Loggers.logger()
                        .warn("Failed to mark a promise as success because it has failed already: {}," +
                                " unnotified cause:", p, err);
            }
        }
    }

    /**
     * Try to mark the {@link Promise} as failure and log.
     */
    static void tryFailure(Promise<?> p, Throwable cause) {
        if (!p.tryFailure(cause)) {
            Throwable err = p.cause();
            if (err == null) {
                Loggers.logger().warn(
                        "Failed to mark a promise as failure because it has succeeded already: {}", p, cause);
            } else if (Loggers.logger().isWarnEnabled()) {
                Loggers.logger().warn(
                        "Failed to mark a promise as failure because it has failed already: {}, unnotified cause: {}",
                        p, ThrowableUtil.stackTraceToString(err), cause);
            }
        }
    }

    static ByteBuf toErrorMsg(BaseResponse r, Throwable t) {
        r.headers().clear();
        r.headers().set(CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        return toErrorMsg(t);
    }

    static ByteBuf toErrorMsg(Throwable t) {
        ByteBuf err;
        if (StringUtils.isEmpty(t.getMessage())) {
            err = Unpooled.EMPTY_BUFFER;
        } else {
            err = Unpooled.copiedBuffer(t.getMessage().getBytes(StandardCharsets.UTF_8));
        }
        return err;
    }

    static void checkIndex(byte[] bytes, int off, int len) {
        if (MathUtil.isOutOfBounds(off, len, bytes.length)) {
            throw new IndexOutOfBoundsException();
        }
    }

    static ByteBuf toByteBuf(ByteBufAllocator alloc, byte[] arr, int off, int length) {
        if (arr.length == 0 || length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        ByteBuf buf = alloc.buffer(length);
        return buf.writeBytes(arr, off, length);
    }

    static void safeRunInChannel(ChannelHandlerContext ctx, Runnable r, ChannelPromise promise, Object data) {
        try {
            ctx.channel().eventLoop().execute(r);
        } catch (Throwable cause) {
            if (promise != null) {
                tryFailure(promise, cause);
            }
            ReferenceCountUtil.release(data);
            Loggers.logger().error("Failed to submit task to event loop.", cause);
        }
    }

    private Utils() {
    }
}
