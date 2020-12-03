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

import esa.commons.StringUtils;
import esa.commons.http.Cookie;
import esa.commons.http.HttpHeaders;
import esa.commons.http.HttpMethod;
import esa.commons.netty.http.CookieImpl;
import esa.httpserver.core.Aggregation;
import esa.httpserver.core.MultiPart;
import esa.httpserver.core.RequestHandle;
import esa.httpserver.utils.Constants;
import esa.httpserver.utils.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static esa.httpserver.impl.Utils.SOURCE_ADDRESS;
import static esa.httpserver.impl.Utils.toErrorMsg;

abstract class BaseRequestHandle implements RequestHandle {

    final ServerRuntime runtime;
    final ChannelHandlerContext ctx;
    final HttpMethod method;
    final QueryStringDecoder uriDecoded;
    Map<String, Cookie> cookies;
    SocketAddress remoteAddress;
    private Consumer<ByteBuf> onData;
    private Consumer<HttpHeaders> onTrailer;
    private Function<Promise<Void>, Future<Void>> onEnd;
    private Consumer<Throwable> onError;
    private AggregationHandle aggregation;
    private MultipartHandle multipart;
    volatile boolean isEnded;

    BaseRequestHandle(ServerRuntime runtime,
                      ChannelHandlerContext ctx,
                      HttpMethod method,
                      String uri) {
        this.runtime = runtime;
        this.ctx = ctx;
        this.method = method;
        this.uriDecoded = new QueryStringDecoder(uri);
    }

    @Override
    public String scheme() {
        return ctx.channel().attr(Constants.SCHEME).get();
    }

    @Override
    public HttpMethod method() {
        return method;
    }

    @Override
    public String uri() {
        return uriDecoded.uri();
    }

    @Override
    public String path() {
        return uriDecoded.path();
    }

    @Override
    public String query() {
        return uriDecoded.rawQuery();
    }

    @Override
    public Map<String, List<String>> paramMap() {
        return uriDecoded.parameters();
    }

    @Override
    public Map<String, Cookie> cookies() {
        if (cookies == null) {
            String value = headers().get(HttpHeaderNames.COOKIE);
            if (StringUtils.isEmpty(value)) {
                cookies = Collections.emptyMap();
            } else {
                Set<io.netty.handler.codec.http.cookie.Cookie> decoded =
                        ServerCookieDecoder.STRICT.decode(value);
                Map<String, Cookie> map = new HashMap<>(decoded.size());

                for (io.netty.handler.codec.http.cookie.Cookie c : decoded) {
                    map.put(c.name(), new CookieImpl(c));
                }
                cookies = map;
            }
        }
        return cookies;
    }

    @Override
    public SocketAddress remoteAddress() {
        if (remoteAddress == null) {
            SocketAddress msg = ctx.channel().attr(SOURCE_ADDRESS).get();
            if (msg == null) {
                remoteAddress = tcpSourceAddress();
            } else {
                remoteAddress = msg;
            }
        }
        return remoteAddress;
    }

    @Override
    public SocketAddress tcpSourceAddress() {
        return ctx.channel().remoteAddress();
    }

    @Override
    public SocketAddress localAddress() {
        return ctx.channel().localAddress();
    }

    @Override
    public ByteBufAllocator alloc() {
        return ctx.alloc();
    }

    @Override
    public RequestHandle onData(Consumer<ByteBuf> h) {
        onData = h;
        return this;
    }

    @Override
    public RequestHandle onTrailer(Consumer<HttpHeaders> h) {
        onTrailer = h;
        return this;
    }

    @Override
    public RequestHandle onEnd(Function<Promise<Void>, Future<Void>> h) {
        onEnd = h;
        return this;
    }

    @Override
    public RequestHandle onError(Consumer<Throwable> h) {
        onError = h;
        return this;
    }

    @Override
    public MultiPart multipart() {
        return multipart == null ? MultipartHandle.EMPTY : multipart;
    }

    @Override
    public Aggregation aggregated() {
        return aggregation == null ? AggregationHandle.EMPTY : aggregation;
    }

    @Override
    public RequestHandle aggregate(boolean aggregate) {
        checkEnded();
        if (aggregate) {
            if (aggregation == null) {
                aggregation = new AggregationHandle(ctx);
            }
        } else if (aggregation != null) {
            aggregation.release();
            aggregation = null;
        }
        return this;
    }

    @Override
    public RequestHandle multipart(boolean expect) {
        checkEnded();

        if (expect) {
            if (multipart == null) {
                final HttpRequest request = toHttpRequest();
                if (HttpPostRequestDecoder.isMultipart(request)) {
                    multipart = new MultipartHandle(
                            new HttpPostRequestDecoder(runtime.multipartDataFactory(), request));
                }
            }
        } else if (multipart != null) {
            multipart.release();
            multipart = null;
        }
        return this;
    }

    @Override
    public boolean isEnded() {
        return isEnded;
    }

    @Override
    public abstract BaseResponse<? extends BaseRequestHandle> response();

    void handleContent(ByteBuf buf) {
        if (multipart != null) {
            multipart.onData(buf.duplicate());
        }

        if (aggregation != null) {
            // use retainedDuplicate() if onData handler is present for maintaining separate indexes and marks
            aggregation.appendPartialContent(onData == null ? buf.retain() : buf.retainedDuplicate());
        }

        if (onData != null) {
            onData.accept(buf);
        }
    }

    void handleTrailer(HttpHeaders trailers) {
        if (aggregation != null) {
            aggregation.setTrailers(trailers);
        }

        if (onTrailer != null) {
            onTrailer.accept(trailers);
        }
    }

    void handleEnd() {
        if (multipart != null) {
            // may be error
            multipart.end();
        }

        if (onEnd != null) {
            Future<Void> f = onEnd.apply(ctx.newPromise());
            if (f.isDone()) {
                windUp(f);
            } else {
                f.addListener(this::windUp);
            }
        } else {
            windUp(ctx.newSucceededFuture());
        }
    }

    private void windUp(Future<? super Void> f) {
        try {
            if (f.isSuccess()) {
                if (response().isCommitted()) {
                    if (!response().isEnded()) {
                        Loggers.logger().warn("Response of {} hasn't been ended after processing.", toString());
                    }
                } else if (!response().tryEnd(HttpResponseStatus.valueOf(response().status()),
                        () -> Unpooled.EMPTY_BUFFER, false)) {

                    if (Loggers.logger().isDebugEnabled()) {
                        Loggers.logger().debug("Failed to end response of {} after processing", toString());
                    }
                }
            } else if (!response().tryEnd(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    () -> toErrorMsg(f.cause()), false)) {

                Loggers.logger().warn(
                        "Error occurred while processing {}, but failed to end response", toString());
            }
        } finally {
            release();
        }
    }

    void handleError(Throwable err) {
        try {
            if (onError != null) {
                onError.accept(err);
            }
        } finally {
            release();
        }
    }

    private void release() {
        if (multipart != null) {
            multipart.release();
            multipart = null;
        }
        if (aggregation != null) {
            aggregation.release();
            aggregation = null;
        }
    }


    private void checkEnded() {
        if (isEnded()) {
            throw new IllegalStateException("Already ended");
        }
    }

    protected abstract HttpRequest toHttpRequest();

    @Override
    public String toString() {
        return StringUtils.concat("Request",
                response().isCommitted() ? "![" : "-[",
                version().name(),
                " ", rawMethod(),
                " ", path(),
                "]");
    }

}
