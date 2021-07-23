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

import io.esastack.httpserver.metrics.OpenSslSessionMetrics;
import io.netty.handler.ssl.OpenSslSessionStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenSslSessionMetricsImplTest {

    @Test
    void testDisabled() {
        final OpenSslSessionMetrics metrics = OpenSslSessionMetricsImpl.DISABLED;
        assertEquals(0L, metrics.number());
        assertEquals(0L, metrics.accept());
        assertEquals(0L, metrics.acceptGood());
        assertEquals(0L, metrics.acceptRenegotiate());
        assertEquals(0L, metrics.hits());
        assertEquals(0L, metrics.cbHits());
        assertEquals(0L, metrics.misses());
        assertEquals(0L, metrics.timeouts());
        assertEquals(0L, metrics.cacheFull());
        assertEquals(0L, metrics.ticketKeyFail());
        assertEquals(0L, metrics.ticketKeyNew());
        assertEquals(0L, metrics.ticketKeyRenew());
        assertEquals(0L, metrics.ticketKeyResume());
    }

    @Test
    void testStats() {
        final OpenSslSessionStats stats = mockSessionStats();
        final OpenSslSessionMetricsImpl metrics = new OpenSslSessionMetricsImpl(stats);
        assertSessionMetrics(metrics);
    }

    static OpenSslSessionStats mockSessionStats() {
        final OpenSslSessionStats stats = mock(OpenSslSessionStats.class);
        when(stats.number()).thenReturn(1L);
        when(stats.accept()).thenReturn(1L);
        when(stats.acceptGood()).thenReturn(1L);
        when(stats.acceptRenegotiate()).thenReturn(1L);
        when(stats.hits()).thenReturn(1L);
        when(stats.cbHits()).thenReturn(1L);
        when(stats.misses()).thenReturn(1L);
        when(stats.timeouts()).thenReturn(1L);
        when(stats.cacheFull()).thenReturn(1L);
        when(stats.ticketKeyFail()).thenReturn(1L);
        when(stats.ticketKeyNew()).thenReturn(1L);
        when(stats.ticketKeyRenew()).thenReturn(1L);
        when(stats.ticketKeyResume()).thenReturn(1L);
        return stats;
    }

    static void assertSessionMetrics(OpenSslSessionMetrics metrics) {
        assertEquals(1L, metrics.number());
        assertEquals(1L, metrics.accept());
        assertEquals(1L, metrics.acceptGood());
        assertEquals(1L, metrics.acceptRenegotiate());
        assertEquals(1L, metrics.hits());
        assertEquals(1L, metrics.cbHits());
        assertEquals(1L, metrics.misses());
        assertEquals(1L, metrics.timeouts());
        assertEquals(1L, metrics.cacheFull());
        assertEquals(1L, metrics.ticketKeyFail());
        assertEquals(1L, metrics.ticketKeyNew());
        assertEquals(1L, metrics.ticketKeyRenew());
        assertEquals(1L, metrics.ticketKeyResume());
    }


}
