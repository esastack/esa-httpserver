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

import io.esastack.httpserver.ServerOptions;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ServerRuntimeTest {

    @Test
    void testOptions() {

        final HttpServerImpl.CloseFuture closeFuture = new HttpServerImpl.CloseFuture();
        final ServerOptions options = ServerOptionsConfigure.newOpts()
                .preferNativeTransport(false)
                .metricsEnabled(false)
                .configured();
        final ServerRuntime runtime = new ServerRuntime("foo", options, closeFuture);

        assertEquals("foo", runtime.name());
        Assertions.assertFalse(runtime.options().isPreferNativeTransport());
        assertNotSame(options, runtime.options());
        assertFalse(runtime.shutdownStatus().get());
        assertNotNull(runtime.metrics());
        Assertions.assertFalse(runtime.metrics().enabled());

        final HttpDataFactory httpDataFactory = runtime.multipartDataFactory();
        assertNotNull(httpDataFactory);
        assertSame(httpDataFactory, runtime.multipartDataFactory());

        assertFalse(runtime.isRunning());
        assertNull(runtime.address());
        assertNull(runtime.bossGroup());
        assertNull(runtime.ioGroup());
        assertSame(closeFuture, runtime.closeFuture());

        final LocalAddress addr = new LocalAddress("foo");
        final EventLoopGroup boss = mock(EventLoopGroup.class);
        final EventLoopGroup io = mock(EventLoopGroup.class);

        runtime.setStarted(addr, boss, io);

        assertTrue(runtime.isRunning());
        assertSame(addr, runtime.address());
        assertSame(boss, runtime.bossGroup());
        assertSame(io, runtime.ioGroup());
    }

}
