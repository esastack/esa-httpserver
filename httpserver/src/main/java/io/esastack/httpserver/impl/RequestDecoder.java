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

import io.esastack.commons.net.netty.http.Http1HeadersImpl;
import io.esastack.httpserver.utils.Constants;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpVersion;

/**
 * This implementation of {@link HttpRequestDecoder} will set a value of timestamp into the underlying of req after
 * decoding req line.
 */
final class RequestDecoder extends HttpRequestDecoder {

    RequestDecoder(int maxInitialLineLength, int maxHeaderSize, int maxChunkSize) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize);
    }

    @Override
    protected HttpMessage createMessage(String[] initialLine) {
        HttpMessage msg = new DefaultHttpRequest(
                HttpVersion.valueOf(initialLine[2]),
                HttpMethod.valueOf(initialLine[0]),
                initialLine[1],
                new Http1HeadersImpl());
        msg.headers().add(Constants.TTFB, System.currentTimeMillis());
        return msg;
    }


}
