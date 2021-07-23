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

import io.esastack.httpserver.metrics.ConnectionMetrics;

import java.util.concurrent.atomic.LongAdder;

final class ConnectionMetricsImpl implements ConnectionMetrics {
    static final ConnectionMetrics DISABLED = new Disabled();

    private static final int HTTP_VERSION_SHIFT = 32;
    private static final long HTTP1_UNIT = (1L << HTTP_VERSION_SHIFT);
    private static final long HTTP2_MASK = (1L << HTTP_VERSION_SHIFT) - 1L;
    private static final int HTTP_SCHEMA_SHIFT = 32;
    private static final long HTTP_UNIT = (1L << HTTP_SCHEMA_SHIFT);
    private static final long HTTPS_MASK = (1L << HTTP_SCHEMA_SHIFT) - 1L;

    /**
     * activeHttpVersionState state is logically divided into two unsigned ints: The lower one representing the
     * activeHttp2Connections, and the upper the activeHttp1Connections.
     */
    private final LongAdder activeHttpVersionState = new LongAdder();
    /**
     * activeHttpSchemaState state is logically divided into two unsigned ints: The lower one representing the
     * activeHttpsConnections, and the upper the activeHttpConnections.
     */
    private final LongAdder activeHttpSchemaState = new LongAdder();
    final LongAdder http1ConnectionCount = new LongAdder();
    final LongAdder http2ConnectionCount = new LongAdder();
    final LongAdder httpConnectionCount = new LongAdder();
    final LongAdder httpsConnectionCount = new LongAdder();

    @Override
    public long activeHttp1Connections() {
        return activeHttpVersionState.sum() >>> HTTP_VERSION_SHIFT;
    }

    @Override
    public long activeHttp2Connections() {
        return activeHttpVersionState.sum() & HTTP2_MASK;
    }

    @Override
    public long activeHttpConnections() {
        return activeHttpSchemaState.sum() >>> HTTP_SCHEMA_SHIFT;
    }

    @Override
    public long activeHttpsConnections() {
        return activeHttpSchemaState.sum() & HTTPS_MASK;
    }

    @Override
    public long http1ConnectionCount() {
        return http1ConnectionCount.sum();
    }

    @Override
    public long http2ConnectionCount() {
        return http2ConnectionCount.sum();
    }

    @Override
    public long httpConnectionCount() {
        return httpConnectionCount.sum();
    }

    @Override
    public long httpsConnectionCount() {
        return httpsConnectionCount.sum();
    }

    void incrementActiveHttp1Connections() {
        activeHttpVersionState.add(HTTP1_UNIT);
    }

    void decrementActiveHttp1Connections() {
        activeHttpVersionState.add(-HTTP1_UNIT);
    }

    void incrementActiveHttp2Connections() {
        activeHttpVersionState.add(1L);
    }

    void decrementActiveHttp2Connections() {
        activeHttpVersionState.add(-1L);
    }

    void incrementActiveHttpConnections() {
        activeHttpSchemaState.add(HTTP_UNIT);
    }

    void decrementActiveHttpConnections() {
        activeHttpSchemaState.add(-HTTP_UNIT);
    }

    void incrementActiveHttpsConnections() {
        activeHttpSchemaState.add(1L);
    }

    void decrementActiveHttpsConnections() {
        activeHttpSchemaState.add(-1L);
    }

    private static class Disabled implements ConnectionMetrics {

        @Override
        public long activeHttp1Connections() {
            return 0L;
        }

        @Override
        public long activeHttp2Connections() {
            return 0L;
        }

        @Override
        public long activeHttpsConnections() {
            return 0L;
        }

        @Override
        public long activeHttpConnections() {
            return 0L;
        }

        @Override
        public long http1ConnectionCount() {
            return 0L;
        }

        @Override
        public long http2ConnectionCount() {
            return 0L;
        }

        @Override
        public long httpConnectionCount() {
            return 0L;
        }

        @Override
        public long httpsConnectionCount() {
            return 0L;
        }

        @Override
        public long activeConnections() {
            return 0L;
        }

        @Override
        public long connectionCount() {
            return 0L;
        }
    }
}
