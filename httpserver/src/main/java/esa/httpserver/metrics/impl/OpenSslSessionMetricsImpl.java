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
package esa.httpserver.metrics.impl;

import esa.commons.Checks;
import esa.httpserver.metrics.OpenSslSessionMetrics;
import io.netty.handler.ssl.OpenSslSessionStats;

final class OpenSslSessionMetricsImpl implements OpenSslSessionMetrics {

    static final OpenSslSessionMetrics DISABLED = new Disabled();

    private final OpenSslSessionStats stats;

    OpenSslSessionMetricsImpl(OpenSslSessionStats stats) {
        Checks.checkNotNull(stats, "stats");
        this.stats = stats;
    }

    @Override
    public long number() {
        return stats.number();
    }

    @Override
    public long accept() {
        return stats.accept();
    }

    @Override
    public long acceptGood() {
        return stats.acceptGood();
    }

    @Override
    public long acceptRenegotiate() {
        return stats.acceptRenegotiate();
    }

    @Override
    public long hits() {
        return stats.hits();
    }

    @Override
    public long cbHits() {
        return stats.cbHits();
    }

    @Override
    public long misses() {
        return stats.misses();
    }

    @Override
    public long timeouts() {
        return stats.timeouts();
    }

    @Override
    public long cacheFull() {
        return stats.cacheFull();
    }

    @Override
    public long ticketKeyFail() {
        return stats.ticketKeyFail();
    }

    @Override
    public long ticketKeyNew() {
        return stats.ticketKeyNew();
    }

    @Override
    public long ticketKeyRenew() {
        return stats.ticketKeyRenew();
    }

    @Override
    public long ticketKeyResume() {
        return stats.ticketKeyResume();
    }

    private static class Disabled implements OpenSslSessionMetrics {

        @Override
        public long number() {
            return 0L;
        }

        @Override
        public long accept() {
            return 0L;
        }

        @Override
        public long acceptGood() {
            return 0L;
        }

        @Override
        public long acceptRenegotiate() {
            return 0L;
        }

        @Override
        public long hits() {
            return 0L;
        }

        @Override
        public long cbHits() {
            return 0L;
        }

        @Override
        public long misses() {
            return 0L;
        }

        @Override
        public long timeouts() {
            return 0L;
        }

        @Override
        public long cacheFull() {
            return 0L;
        }

        @Override
        public long ticketKeyFail() {
            return 0L;
        }

        @Override
        public long ticketKeyNew() {
            return 0L;
        }

        @Override
        public long ticketKeyRenew() {
            return 0L;
        }

        @Override
        public long ticketKeyResume() {
            return 0L;
        }
    }
}
