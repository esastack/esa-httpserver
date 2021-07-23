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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartOptionsTest {

    @Test
    void testConfigure() {
        final MultipartOptions options = MultipartOptionsConfigure.newOpts()
                .charset(StandardCharsets.UTF_8)
                .maxSize(1L)
                .memoryThreshold(2L)
                .tempDir("/foo")
                .useDisk(true)
                .configured();

        assertEquals(StandardCharsets.UTF_8, options.getCharset());
        assertEquals(1L, options.getMaxSize());
        assertEquals(2L, options.getMemoryThreshold());
        assertEquals("/foo", options.getTempDir());
        assertTrue(options.isUseDisk());
    }

    @Test
    void testCopyFromAnother() {

        final MultipartOptions another = MultipartOptionsConfigure.newOpts()
                .charset(StandardCharsets.UTF_8)
                .maxSize(1L)
                .memoryThreshold(2L)
                .tempDir("/foo")
                .useDisk(true)
                .configured();

        final MultipartOptions options = new MultipartOptions(another);
        assertEquals(StandardCharsets.UTF_8, options.getCharset());
        assertEquals(1L, options.getMaxSize());
        assertEquals(2L, options.getMemoryThreshold());
        assertEquals("/foo", options.getTempDir());
        assertTrue(options.isUseDisk());
    }

}
