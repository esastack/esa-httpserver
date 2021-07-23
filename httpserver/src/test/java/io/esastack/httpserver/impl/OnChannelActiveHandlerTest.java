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
package io.esastack.httpserver.impl;

import esa.commons.ExceptionUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnChannelActiveHandlerTest {

    @Test
    void testChannelActiveEvent() {
        assertThrows(NullPointerException.class, () -> new OnChannelActiveHandler(null));
        final AtomicBoolean active = new AtomicBoolean(false);
        final EmbeddedChannel channel = new EmbeddedChannel(new OnChannelActiveHandler(ctx -> {
            active.set(true);
        }));
        assertTrue(channel.isActive());
        assertTrue(active.get());
        assertNull(channel.pipeline().get(OnChannelActiveHandler.class));
    }

    @Test
    void testError() {
        assertThrows(NullPointerException.class, () -> new OnChannelActiveHandler(null));
        final EmbeddedChannel channel = new EmbeddedChannel(new OnChannelActiveHandler(ctx -> {
            ExceptionUtils.throwException(new IllegalStateException());
        }));
        assertTrue(channel.isActive());
        assertNull(channel.pipeline().get(OnChannelActiveHandler.class));
    }

}
