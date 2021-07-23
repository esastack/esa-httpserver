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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionMetricsImplTest {

    @Test
    void testDisabled() {
        testInitial(ConnectionMetricsImpl.DISABLED);
    }

    @Test
    void testInitValue() {
        final ConnectionMetricsImpl metrics = new ConnectionMetricsImpl();
        testInitial(metrics);
    }

    private static void testInitial(ConnectionMetrics metrics) {
        assertEquals(0L, metrics.activeHttp1Connections());
        assertEquals(0L, metrics.activeHttp2Connections());
        assertEquals(0L, metrics.activeHttpConnections());
        assertEquals(0L, metrics.activeHttpsConnections());
        assertEquals(0L, metrics.http1ConnectionCount());
        assertEquals(0L, metrics.http2ConnectionCount());
        assertEquals(0L, metrics.httpConnectionCount());
        assertEquals(0L, metrics.httpsConnectionCount());
    }

    @Test
    void testAccumulateHttp1AndHttp2Connections() {
        final ConnectionMetricsImpl metrics = new ConnectionMetricsImpl();
        metrics.incrementActiveHttp1Connections();
        assertEquals(1L, metrics.activeHttp1Connections());
        assertEquals(0L, metrics.activeHttp2Connections());

        metrics.incrementActiveHttp2Connections();
        assertEquals(1L, metrics.activeHttp1Connections());
        assertEquals(1L, metrics.activeHttp2Connections());

        metrics.incrementActiveHttp1Connections();
        metrics.incrementActiveHttp2Connections();
        assertEquals(2L, metrics.activeHttp1Connections());
        assertEquals(2L, metrics.activeHttp2Connections());

        metrics.decrementActiveHttp1Connections();
        assertEquals(1L, metrics.activeHttp1Connections());
        assertEquals(2L, metrics.activeHttp2Connections());

        metrics.decrementActiveHttp1Connections();
        assertEquals(0L, metrics.activeHttp1Connections());
        assertEquals(2L, metrics.activeHttp2Connections());

        metrics.decrementActiveHttp2Connections();
        assertEquals(0L, metrics.activeHttp1Connections());
        assertEquals(1L, metrics.activeHttp2Connections());

        metrics.decrementActiveHttp2Connections();
        assertEquals(0L, metrics.activeHttp1Connections());
        assertEquals(0L, metrics.activeHttp2Connections());
    }

    @Test
    void testAccumulateHttpAndHttpsConnections() {
        final ConnectionMetricsImpl metrics = new ConnectionMetricsImpl();
        metrics.incrementActiveHttpConnections();
        assertEquals(1L, metrics.activeHttpConnections());
        assertEquals(0L, metrics.activeHttpsConnections());

        metrics.incrementActiveHttpsConnections();
        assertEquals(1L, metrics.activeHttpConnections());
        assertEquals(1L, metrics.activeHttpsConnections());

        metrics.incrementActiveHttpConnections();
        metrics.incrementActiveHttpsConnections();
        assertEquals(2L, metrics.activeHttpConnections());
        assertEquals(2L, metrics.activeHttpsConnections());

        metrics.decrementActiveHttpConnections();
        assertEquals(1L, metrics.activeHttpConnections());
        assertEquals(2L, metrics.activeHttpsConnections());

        metrics.decrementActiveHttpConnections();
        assertEquals(0L, metrics.activeHttpConnections());
        assertEquals(2L, metrics.activeHttpsConnections());

        metrics.decrementActiveHttpsConnections();
        assertEquals(0L, metrics.activeHttpConnections());
        assertEquals(1L, metrics.activeHttpsConnections());

        metrics.decrementActiveHttpsConnections();
        assertEquals(0L, metrics.activeHttpConnections());
        assertEquals(0L, metrics.activeHttpsConnections());
    }

    @Test
    void testAccumulateConnectionCount() {
        final ConnectionMetricsImpl metrics = new ConnectionMetricsImpl();

        metrics.http1ConnectionCount.increment();
        metrics.http2ConnectionCount.increment();
        metrics.httpConnectionCount.increment();
        metrics.httpsConnectionCount.increment();

        assertEquals(1L, metrics.http1ConnectionCount());
        assertEquals(1L, metrics.http2ConnectionCount());
        assertEquals(1L, metrics.httpConnectionCount());
        assertEquals(1L, metrics.httpsConnectionCount());

    }

}
