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
package io.esastack.httpserver.metrics.impl;

import io.esastack.commons.net.http.HttpVersion;
import io.esastack.httpserver.core.BaseRequest;
import io.esastack.httpserver.metrics.Metrics;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;

/**
 * Interface defines events that may be used in metrics.
 */
public interface MetricsReporter extends Metrics {

    /**
     * Channel active.
     *
     * @param ch          channel
     * @param httpVersion http version
     */
    void reportConnect(Channel ch, HttpVersion httpVersion);

    /**
     * Channel inactive.
     *
     * @param ch channel
     */
    void reportDisconnect(Channel ch);

    /**
     * Channel upgrade from http1 to http2.
     *
     * @param ch channel
     */
    void reportUpgrade(Channel ch);

    /**
     * Request received.
     *
     * @param request request
     */
    void reportRequest(BaseRequest request);

    /**
     * Server is about to starting to initialize ssl context.
     *
     * @param context  ssl context
     */
    void initSsl(SslContext context);
}
