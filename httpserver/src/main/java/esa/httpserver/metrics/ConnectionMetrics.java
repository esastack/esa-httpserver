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
package esa.httpserver.metrics;

public interface ConnectionMetrics {

    /**
     * Returns the number of active http1 connections.
     */
    long activeHttp1Connections();

    /**
     * Returns the number of active http2 connections.
     */
    long activeHttp2Connections();

    /**
     * Returns the number of active https connections.
     */
    long activeHttpsConnections();

    /**
     * Returns the number of active non-https connections.
     */
    long activeHttpConnections();

    /**
     * Returns the number of all the active http1 connections and active http2 connections.
     */
    default long activeConnections() {
        return activeHttp1Connections() + activeHttp2Connections();
    }

    /**
     * Returns the accumulated number of http1 connections.
     */
    long http1ConnectionCount();

    /**
     * Returns the accumulated number of http2 connections.
     */
    long http2ConnectionCount();

    /**
     * Returns the accumulated number of non-https connections.
     */
    long httpConnectionCount();

    /**
     * Returns the accumulated number of https connections.
     */
    long httpsConnectionCount();

    /**
     * Returns the accumulated number of all the https and non-https connections.
     */
    default long connectionCount() {
        return http1ConnectionCount() + http2ConnectionCount();
    }
}
