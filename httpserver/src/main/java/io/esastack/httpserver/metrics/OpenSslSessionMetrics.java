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
package io.esastack.httpserver.metrics;

/**
 * Metrics of {@link io.netty.handler.ssl.OpenSslSessionStats}
 */
public interface OpenSslSessionMetrics {

    /**
     * Returns the current number of sessions in the internal session cache.
     */
    long number();

    /**
     * Returns the number of started SSL/TLS handshakes in server mode.
     */
    long accept();

    /**
     * Returns the number of successfully established SSL/TLS sessions in server mode.
     */
    long acceptGood();

    /**
     * Returns the number of start renegotiations in server mode.
     */
    long acceptRenegotiate();

    /**
     * Returns the number of successfully reused sessions. In client mode, a session set with {@code SSL_set_session}
     * successfully reused is counted as a hit. In server mode, a session successfully retrieved from internal or
     * external cache is counted as a hit.
     */
    long hits();

    /**
     * Returns the number of successfully retrieved sessions from the external session cache in server mode.
     */
    long cbHits();

    /**
     * Returns the number of sessions proposed by clients that were not found in the internal session cache in server
     * mode.
     */
    long misses();

    /**
     * Returns the number of sessions proposed by clients and either found in the internal or external session cache in
     * server mode, but that were invalid due to timeout. These sessions are not included in the {@link #hits()} count.
     */
    long timeouts();

    /**
     * Returns the number of sessions that were removed because the maximum session cache size was exceeded.
     */
    long cacheFull();

    /**
     * Returns the number of times a client presented a ticket that did not match any key in the list.
     */
    long ticketKeyFail();

    /**
     * Returns the number of times a client did not present a ticket and we issued a new one
     */
    long ticketKeyNew();

    /**
     * Returns the number of times a client presented a ticket derived from an older key,
     * and we upgraded to the primary key.
     */
    long ticketKeyRenew();

    /**
     * Returns the number of times a client presented a ticket derived from the primary key.
     */
    long ticketKeyResume();
}
