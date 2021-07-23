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

import esa.commons.http.HttpVersion;
import io.esastack.httpserver.core.BaseRequest;
import io.esastack.httpserver.utils.Constants;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.OpenSslServerContext;
import io.netty.handler.ssl.OpenSslServerSessionContext;
import io.netty.handler.ssl.OpenSslSessionStats;
import io.netty.handler.ssl.SslContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsImplTest {

    @Test
    void testDisabled() {
        final MetricsReporter reporter = MetricsImpl.of(false);
        final SslContext context = mock(SslContext.class);
        reporter.initSsl(context);

        assertFalse(reporter.enabled());
        assertSame(RequestMetricsImpl.DISABLED, reporter.request());
        assertSame(ConnectionMetricsImpl.DISABLED, reporter.connection());
        assertSame(OpenSslSessionMetricsImpl.DISABLED, reporter.openSslSession());

        final Channel channel = mock(Channel.class);
        reporter.reportConnect(channel, HttpVersion.HTTP_1_1);
        reporter.reportDisconnect(channel);
        reporter.reportUpgrade(channel);
        final BaseRequest request = mock(BaseRequest.class);
        reporter.reportRequest(request);

        assertSame(RequestMetricsImpl.DISABLED, reporter.request());
        assertSame(ConnectionMetricsImpl.DISABLED, reporter.connection());
        assertSame(OpenSslSessionMetricsImpl.DISABLED, reporter.openSslSession());
    }

    @Test
    void testReport() {
        final MetricsReporter reporter = MetricsImpl.of(true);

        final OpenSslServerContext context = mock(OpenSslServerContext.class);
        final OpenSslServerSessionContext sessionContext = mock(OpenSslServerSessionContext.class);

        final OpenSslSessionStats stats = OpenSslSessionMetricsImplTest.mockSessionStats();
        when(sessionContext.stats()).thenReturn(stats);
        when(context.sessionContext()).thenReturn(sessionContext);
        reporter.initSsl(context);

        assertTrue(reporter.enabled());
        assertNotNull(reporter.request());
        assertNotNull(reporter.connection());
        assertNotNull(reporter.openSslSession());

        OpenSslSessionMetricsImplTest.assertSessionMetrics(reporter.openSslSession());


        final Channel ch1 = channel(true);
        final Channel ch2 = channel(false);
        reporter.reportConnect(ch1, HttpVersion.HTTP_1_1);
        reporter.reportConnect(ch2, HttpVersion.HTTP_2);
        final BaseRequest request = mock(BaseRequest.class);
        reporter.reportRequest(request);

        assertEquals(1L, reporter.connection().activeHttp1Connections());
        assertEquals(1L, reporter.connection().activeHttp2Connections());
        assertEquals(1L, reporter.connection().activeHttpConnections());
        assertEquals(1L, reporter.connection().activeHttpsConnections());
        assertEquals(2L, reporter.connection().activeConnections());
        assertEquals(1L, reporter.connection().http1ConnectionCount());
        assertEquals(1L, reporter.connection().http2ConnectionCount());
        assertEquals(1L, reporter.connection().httpConnectionCount());
        assertEquals(1L, reporter.connection().httpsConnectionCount());
        assertEquals(2L, reporter.connection().connectionCount());

        reporter.reportUpgrade(ch1);
        assertEquals(0L, reporter.connection().activeHttp1Connections());
        assertEquals(2L, reporter.connection().activeHttp2Connections());
        assertEquals(1L, reporter.connection().activeHttpConnections());
        assertEquals(1L, reporter.connection().activeHttpsConnections());
        assertEquals(2L, reporter.connection().activeConnections());
        assertEquals(1L, reporter.connection().http1ConnectionCount());
        assertEquals(1L, reporter.connection().http2ConnectionCount());
        assertEquals(1L, reporter.connection().httpConnectionCount());
        assertEquals(1L, reporter.connection().httpsConnectionCount());
        assertEquals(2L, reporter.connection().connectionCount());

        reporter.reportDisconnect(ch1);
        reporter.reportDisconnect(ch2);

        assertEquals(0L, reporter.connection().activeHttp1Connections());
        assertEquals(0L, reporter.connection().activeHttp2Connections());
        assertEquals(0L, reporter.connection().activeHttpConnections());
        assertEquals(0L, reporter.connection().activeHttpsConnections());
        assertEquals(0L, reporter.connection().activeConnections());
        assertEquals(1L, reporter.connection().http1ConnectionCount());
        assertEquals(1L, reporter.connection().http2ConnectionCount());
        assertEquals(1L, reporter.connection().httpConnectionCount());
        assertEquals(1L, reporter.connection().httpsConnectionCount());
        assertEquals(2L, reporter.connection().connectionCount());
    }

    private static Channel channel(boolean https) {
        final EmbeddedChannel ch = new EmbeddedChannel();
        ch.attr(Constants.SCHEME).set(https ? Constants.SCHEMA_HTTPS : Constants.SCHEMA_HTTP);
        return ch;
    }

}
