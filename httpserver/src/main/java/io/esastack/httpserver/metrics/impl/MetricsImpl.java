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
import io.esastack.httpserver.metrics.ConnectionMetrics;
import io.esastack.httpserver.metrics.Metrics;
import io.esastack.httpserver.metrics.OpenSslSessionMetrics;
import io.esastack.httpserver.metrics.RequestMetrics;
import io.esastack.httpserver.utils.Constants;
import io.netty.channel.Channel;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;

public final class MetricsImpl implements MetricsReporter {

    private static final Disabled DISABLED = new Disabled();
    private static final AttributeKey<HttpVersion> HTTP_VERSION = AttributeKey.valueOf("$http.version");

    private final ConnectionMetricsImpl connectionMetrics;
    private final RequestMetricsImpl requestMetrics;
    private volatile OpenSslSessionMetricsImpl openSslSessionMetrics;

    public static MetricsReporter of(boolean enabled) {
        return enabled ? new MetricsImpl() : DISABLED;
    }

    private MetricsImpl() {
        connectionMetrics = new ConnectionMetricsImpl();
        requestMetrics = new RequestMetricsImpl();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public RequestMetrics request() {
        return requestMetrics;
    }

    @Override
    public ConnectionMetrics connection() {
        return connectionMetrics;
    }

    @Override
    public OpenSslSessionMetrics openSslSession() {
        return openSslSessionMetrics == null ? OpenSslSessionMetricsImpl.DISABLED : openSslSessionMetrics;
    }

    @Override
    public void reportConnect(Channel ch, HttpVersion httpVersion) {
        ch.attr(HTTP_VERSION).set(httpVersion);
        if (HttpVersion.HTTP_2 == httpVersion) {
            connectionMetrics.incrementActiveHttp2Connections();
            connectionMetrics.http2ConnectionCount.increment();
        } else {
            connectionMetrics.incrementActiveHttp1Connections();
            connectionMetrics.http1ConnectionCount.increment();
        }

        if (isHttps(ch)) {
            connectionMetrics.incrementActiveHttpsConnections();
            connectionMetrics.httpsConnectionCount.increment();
        } else {
            connectionMetrics.incrementActiveHttpConnections();
            connectionMetrics.httpConnectionCount.increment();
        }
    }

    @Override
    public void reportDisconnect(Channel ch) {
        final HttpVersion version = ch.attr(HTTP_VERSION).get();
        if (version != null) {
            if (HttpVersion.HTTP_2 == version) {
                connectionMetrics.decrementActiveHttp2Connections();
            } else {
                connectionMetrics.decrementActiveHttp1Connections();
            }
        }

        if (isHttps(ch)) {
            connectionMetrics.decrementActiveHttpsConnections();
        } else {
            connectionMetrics.decrementActiveHttpConnections();
        }
    }

    @Override
    public void reportUpgrade(Channel ch) {
        ch.attr(HTTP_VERSION).set(HttpVersion.HTTP_2);
        connectionMetrics.incrementActiveHttp2Connections();
        connectionMetrics.decrementActiveHttp1Connections();
    }

    @Override
    public void reportRequest(BaseRequest request) {
        requestMetrics.requestCount.increment();
    }

    @Override
    public void initSsl(SslContext context) {
        if (context instanceof OpenSslServerContext) {
            OpenSslServerContext openSslServerContext = (OpenSslServerContext) context;
            this.openSslSessionMetrics = new OpenSslSessionMetricsImpl(openSslServerContext.sessionContext().stats());
        }
    }

    private static boolean isHttps(Channel ch) {
        String schema = ch.attr(Constants.SCHEME).get();
        return schema != null && Constants.SCHEMA_HTTPS.equals(schema);
    }

    private static class Disabled implements Metrics, MetricsReporter {

        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public RequestMetrics request() {
            return RequestMetricsImpl.DISABLED;
        }

        @Override
        public ConnectionMetrics connection() {
            return ConnectionMetricsImpl.DISABLED;
        }

        @Override
        public OpenSslSessionMetrics openSslSession() {
            return OpenSslSessionMetricsImpl.DISABLED;
        }

        @Override
        public void reportConnect(Channel ch, HttpVersion httpVersion) {
        }

        @Override
        public void reportDisconnect(Channel ch) {
        }

        @Override
        public void reportUpgrade(Channel ch) {
        }

        @Override
        public void reportRequest(BaseRequest request) {
        }

        @Override
        public void initSsl(SslContext context) {
        }
    }
}
