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
package esa.httpserver;

public final class H2OptionsConfigure {
    private boolean enabled;
    private int maxReservedStreams;
    private int maxFrameSize;
    private long gracefulShutdownTimeoutMillis = 60L * 1000L;

    private H2OptionsConfigure() {
    }

    public static H2OptionsConfigure newOpts() {
        return new H2OptionsConfigure();
    }

    public H2OptionsConfigure enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public H2OptionsConfigure maxReservedStreams(int maxReservedStreams) {
        this.maxReservedStreams = maxReservedStreams;
        return this;
    }

    public H2OptionsConfigure maxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
        return this;
    }

    public H2OptionsConfigure gracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        this.gracefulShutdownTimeoutMillis = gracefulShutdownTimeoutMillis;
        return this;
    }

    public H2Options configured() {
        H2Options h2Options = new H2Options();
        h2Options.setEnabled(enabled);
        h2Options.setMaxReservedStreams(maxReservedStreams);
        h2Options.setMaxFrameSize(maxFrameSize);
        h2Options.setGracefulShutdownTimeoutMillis(gracefulShutdownTimeoutMillis);
        return h2Options;
    }
}
