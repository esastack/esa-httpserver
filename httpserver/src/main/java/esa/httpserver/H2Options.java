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

import esa.commons.Checks;

import java.io.Serializable;

public class H2Options implements Serializable {

    private static final long serialVersionUID = -1962829341931588557L;

    private boolean enabled;
    private int maxReservedStreams;
    private int maxFrameSize;
    private long gracefulShutdownTimeoutMillis = 60L * 1000L;

    public H2Options() {
    }

    public H2Options(H2Options other) {
        Checks.checkNotNull(other, "other");
        this.enabled = other.enabled;
        this.maxReservedStreams = other.maxReservedStreams;
        this.maxFrameSize = other.maxFrameSize;
        this.gracefulShutdownTimeoutMillis = other.gracefulShutdownTimeoutMillis;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxReservedStreams() {
        return maxReservedStreams;
    }

    public void setMaxReservedStreams(int maxReservedStreams) {
        this.maxReservedStreams = maxReservedStreams;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public void setMaxFrameSize(int maxFrameSize) {
        this.maxFrameSize = maxFrameSize;
    }

    public long getGracefulShutdownTimeoutMillis() {
        return gracefulShutdownTimeoutMillis;
    }

    public void setGracefulShutdownTimeoutMillis(long gracefulShutdownTimeoutMillis) {
        this.gracefulShutdownTimeoutMillis = gracefulShutdownTimeoutMillis;
    }
}
