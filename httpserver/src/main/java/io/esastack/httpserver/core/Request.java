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
package io.esastack.httpserver.core;

import esa.commons.annotation.Beta;
import io.netty.buffer.ByteBufAllocator;

/**
 * An interface defines a http server request.
 * <p>
 * Each instance of this {@link Request} is associated with a corresponding {@link #response()}.
 * <p>
 * It would be in unfinished state which means current {@link Request} haven't been completely decoded(eg. only part of
 * the body is decoded, and waiting the left.) and it could be indicated by the return value of {@link #isEnded()}. So
 * you so would see an unfinished result by calling {@link #aggregated()} and {@link #multipart()} before {@link
 * #isEnded()} returns {@code true}.
 * <p>
 * !Note: This class is not designed as thread-safe, please do not use it in multi-thread before {@link #isEnded()}
 * returns {@code true}
 */
public interface Request extends BaseRequest {

    /**
     * Returns the corresponding {@link Response}.
     *
     * @return response
     */
    Response response();

    /**
     * Returns the multipart result of this request.
     * <p>
     * This is only used when current request IS a multipart request and also multipart is expected.
     * <p>
     * !Note: You may see an unfinished result before {@link #isEnded()} returns {@code true}.
     *
     * @return multipart result or {@code empty}.
     */
    MultiPart multipart();

    /**
     * Returns the aggregation result of this request.
     * <p>
     * This is only used when aggregation is expected.
     * <p>
     * !Note: You may see an unfinished result before {@link #isEnded()} returns {@code true}.
     *
     * @return aggregation result or {@code empty}.
     */
    Aggregation aggregated();

    /**
     * Indicates whether current request is decoded completely.
     *
     * @return {@code true} if request is decoded completely, otherwise {@code false}
     */
    boolean isEnded();

    /**
     * Get current allocator.
     *
     * @return allocator
     */
    @Beta
    ByteBufAllocator alloc();

}
