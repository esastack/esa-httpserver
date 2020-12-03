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
package esa.httpserver.core;

import esa.commons.http.HttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An interface defines a http server {@link Request} and provides the guiding to handle the coming request.
 * <p>
 * When the {@link RequestHandle} is coming, current {@link Request} may have not been ended, which means the request
 * line and the request headers is coming and decoded as a instance of {@link RequestHandle} but the body(if present)
 * and trailing headers(if present) have not been received, and next you may want to handle them via {@link
 * #onData(Consumer)}, {@link #onTrailer(Consumer)}, {@link #onEnd(Function)}...
 * <p>
 * !Note: This class is not designed as thread-safe, please handing requests in current thread(probably the EventLoop),
 * because it is in unfinished state before calling {@link #onEnd(Function)} method, so you can use the {@link
 * #aggregated()} and {@link #multipart()} in {@link #onEnd(Function)} or use it after {@link #isEnded()} returns {@code
 * true}
 */
public interface RequestHandle extends Request {

    /**
     * Sets a handler to handle the coming http body.
     * <p>
     * The handle set would not be called if there's no http body data.
     *
     * @param h handler
     *
     * @return this
     */
    RequestHandle onData(Consumer<ByteBuf> h);

    /**
     * Sets a handler to handle the coming tailing headers.
     * <p>
     * The handle set would not be called if there's no tailing headers.
     *
     * @param h handler
     *
     * @return this
     */
    RequestHandle onTrailer(Consumer<HttpHeaders> h);

    /**
     * {@link #aggregated()} could be used after request is ended, which means you can get a completed request such as
     * using the {@link #aggregated()} in handle {@link #onEnd(Function)}.
     *
     * @param aggregate whether request aggregation is expected
     *
     * @return this
     */
    RequestHandle aggregate(boolean aggregate);

    /**
     * {@link #multipart()} could be used after request is ended, which means you can get a completed request that
     * multipart contents have already been decoded such as using the {@link #aggregated()} in handle {@link
     * #onEnd(Function)}. It would be useful if there's large upload files in this request.
     *
     * @param expect whether multipart is expected
     *
     * @return this
     */
    RequestHandle multipart(boolean expect);

    /**
     * Sets a handler to handle this request when current request has been decoded completely.
     * <p>
     * Only one of the following handler will be called for each request except that there's something wrong with the
     * onEnd handler(eg. throw an exception)
     *
     * <ul>
     * <li>onEnd handler set by {@link #onEnd(Function)}</li>
     * <li>onError handler set by {@link #onError(Consumer)}</li>
     * </ul>
     *
     * @param h handler
     *
     * @return this
     */
    RequestHandle onEnd(Function<Promise<Void>, Future<Void>> h);

    /**
     * Sets a handler to handle unexpected error.
     * <p>
     * Only one of the following handler will be called for each request except that there's something wrong with the
     * onEnd handler(eg. throw an exception)
     *
     * <ul>
     * <li>onEnd handler set by {@link #onEnd(Function)}</li>
     * <li>onError handler set by {@link #onError(Consumer)}</li>
     * </ul>
     *
     * @param h handler
     *
     * @return this
     */
    RequestHandle onError(Consumer<Throwable> h);
}
