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
package io.esastack.httpserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2OptionsTest {

    @Test
    void testConfigure() {
        final H2Options options = H2OptionsConfigure.newOpts()
                .enabled(true)
                .gracefulShutdownTimeoutMillis(1L)
                .maxFrameSize(1)
                .maxReservedStreams(2)
                .configured();

        assertTrue(options.isEnabled());
        assertEquals(1L, options.getGracefulShutdownTimeoutMillis());
        assertEquals(1, options.getMaxFrameSize());
        assertEquals(2, options.getMaxReservedStreams());
    }

    @Test
    void testCopyFromAnother() {

        final H2Options another = H2OptionsConfigure.newOpts()
                .enabled(true)
                .gracefulShutdownTimeoutMillis(1L)
                .maxFrameSize(1)
                .maxReservedStreams(2)
                .configured();

        final H2Options options = new H2Options(another);
        assertTrue(options.isEnabled());
        assertEquals(1L, options.getGracefulShutdownTimeoutMillis());
        assertEquals(1, options.getMaxFrameSize());
        assertEquals(2, options.getMaxReservedStreams());
    }


}
