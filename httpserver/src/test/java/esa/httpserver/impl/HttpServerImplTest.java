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
package esa.httpserver.impl;

import esa.commons.NetworkUtils;
import esa.httpserver.HttpServer;
import esa.httpserver.ServerOptions;
import esa.httpserver.ServerOptionsConfigure;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HttpServerImplTest {

    @Test
    void testStatus() {
        final HttpServer server = HttpServer.create("foo", ServerOptionsConfigure.newOpts()
                .metricsEnabled(true)
                .bossThreads(1)
                .ioThreads(1)
                .configured());
        assertTrue(server.name().startsWith("foo"));
        assertNull(server.address());
        assertNull(server.bossGroup());
        assertNull(server.ioGroup());
        assertNotNull(server.metrics());
        assertTrue(server.metrics().enabled());
        assertNotNull(server.closeFuture());
        assertDoesNotThrow(server::close);


        final AtomicBoolean onClose = new AtomicBoolean();
        final AtomicBoolean onConnected = new AtomicBoolean();
        final AtomicBoolean onDisconnected = new AtomicBoolean();
        final AtomicBoolean onHandle = new AtomicBoolean();
        final CompletableFuture<Boolean> closed = new CompletableFuture<>();
        assertDoesNotThrow(() -> server.onClose(() -> onClose.set(true)));
        assertDoesNotThrow(() -> server.onConnected(ctx -> onConnected.set(true)));
        assertDoesNotThrow(() -> server.onDisconnected(ctx -> onDisconnected.set(true)));
        assertDoesNotThrow(() -> server.handle(ctx -> onHandle.set(true)));
        assertThrows(IllegalStateException.class, server::awaitUninterruptibly);
        assertThrows(IllegalStateException.class, server::await);

        server.closeFuture().addListener(f -> closed.complete(true));

        final int port = NetworkUtils.selectRandomPort();

        boolean started = false;
        try {
            server.listen(port);
            started = true;
        } catch (Exception ignored) {
        }

        assumeTrue(started);
        CompletableFuture<Boolean> awaited1 = null;
        CompletableFuture<Boolean> awaited2 = null;
        try {
            awaited1 = CompletableFuture.supplyAsync(() -> {
                try {
                    server.awaitUninterruptibly();
                } catch (Exception t) {
                    return false;
                }
                return true;
            });

            awaited2 = CompletableFuture.supplyAsync(() -> {
                try {
                    server.await();
                } catch (Exception t) {
                    return false;
                }
                return true;
            });

            assertEquals(port, NetworkUtils.getPort(server.address()));
            assertNotNull(server.bossGroup());
            assertNotNull(server.ioGroup());

            assertThrows(IllegalStateException.class, () -> server.onConnected(ctx -> onConnected.set(true)));
            assertThrows(IllegalStateException.class, () -> server.onDisconnected(ctx -> onDisconnected.set(true)));
            assertThrows(IllegalStateException.class, () -> server.handle(ctx -> onHandle.set(true)));

            assertFalse(onConnected.get());
            assertFalse(onDisconnected.get());
            assertFalse(onHandle.get());
            assertFalse(onClose.get());
            assertFalse(closed.isDone());
        } finally {
            server.close();
            assertTrue(onClose.get());
            assertTrue(closed.join());
            assertNull(server.address());
            assertNull(server.bossGroup());
            assertNull(server.ioGroup());
            if (awaited1 != null) {
                assertTrue(awaited1.join());
            }
            if (awaited2 != null) {
                assertTrue(awaited2.join());
            }
        }
    }

    @Test
    void testListenWithHostAndPort() {
        final HttpServer server = HttpServer.create(ServerOptionsConfigure.newOpts()
                .bossThreads(1)
                .ioThreads(1)
                .configured())
                .handle(r -> r.response().end());
        final String host = "127.0.0.1";
        final int port = NetworkUtils.selectRandomPort();

        boolean started = false;
        try {
            server.listen(host, port);
            started = true;
        } catch (Exception ignored) {
        }

        assumeTrue(started);

        try {
            assertEquals(host, ((InetSocketAddress) server.address()).getHostString());
            assertEquals(port, NetworkUtils.getPort(server.address()));
        } finally {
            server.close();
        }
    }

    @Test
    void testFailedToStart() {
        final ServerOptions options = ServerOptionsConfigure.newOpts()
                .bossThreads(1)
                .ioThreads(1)
                .configured();
        final HttpServer server = HttpServer.create(options)
                .handle(r -> r.response().end());
        final String host = "127.0.0.1";
        final int port = NetworkUtils.selectRandomPort();

        boolean started = false;
        try {
            server.listen(host, port);
            started = true;
        } catch (Exception ignored) {
        }

        assumeTrue(started);

        try {
            final HttpServer server1 = HttpServer.create(options)
                    .handle(r -> r.response().end());


            final AtomicBoolean onClose = new AtomicBoolean();
            final CompletableFuture<Boolean> closed = new CompletableFuture<>();
            server.onClose(() -> onClose.set(true));
            assertThrows(Exception.class, () -> server1.listen(host, port));

            assertNull(server1.address());
            assertNull(server1.bossGroup());
            assertNull(server1.ioGroup());
            assertFalse(onClose.get());
            assertFalse(closed.isDone());
        } finally {
            server.close();
        }
    }

    @Test
    void testAutoServerNameCreation() {
        final HttpServer server = HttpServer.create("foo", ServerOptionsConfigure.newOpts().configured());
        assertTrue(server.name().startsWith("foo"));

        final HttpServer server1 = HttpServer.create("foo", ServerOptionsConfigure.newOpts().configured());
        assertTrue(server.name().startsWith("foo"));

        assertNotEquals(server.name(), server1.name());

        final HttpServer server2 = HttpServer.create(ServerOptionsConfigure.newOpts().configured());
        assertNotNull(server2.name());

        final HttpServer server3 = HttpServer.create();
        assertNotNull(server3.name());
    }

    @Test
    void testCloseFuture() {
        final HttpServerImpl.CloseFuture closeFuture = new HttpServerImpl.CloseFuture();
        assertFalse(closeFuture.isDone());
        assertThrows(IllegalStateException.class, () -> closeFuture.setSuccess(null));
        assertThrows(IllegalStateException.class, () -> closeFuture.setFailure(new Exception()));
        assertThrows(IllegalStateException.class, () -> closeFuture.trySuccess(null));
        assertThrows(IllegalStateException.class, () -> closeFuture.tryFailure(new Exception()));
    }

}
