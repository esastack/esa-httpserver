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

import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.impl.HttpServerImpl;
import io.esastack.httpserver.metrics.Metrics;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.function.Consumer;

/**
 * An http server to provide a way to handle http requests received from the clients. Also, this interface would be an
 * entrance for bootstrapping a new instance of this http server.
 * <p>
 * Every instance of this {@link HttpServer} have a identity {@link #name()} which also means that the different
 * instance should be isolated with others.
 * <p>
 * All the requests received would be handled by the request handler you set by {@link #handle(Consumer)}.
 */
public interface HttpServer {

    /**
     * Creates a new instance of {@link HttpServer} by the default options.
     *
     * @return a new instance of {@link HttpServer}
     */
    static HttpServer create() {
        return create(ServerOptionsConfigure.newOpts().configured());
    }

    /**
     * Creates a new instance of {@link HttpServer} by the {@code options}.
     *
     * @param options options
     *
     * @return a new instance of {@link HttpServer}
     */
    static HttpServer create(ServerOptions options) {
        return new HttpServerImpl(options);
    }

    /**
     * Creates a new instance of {@link HttpServer} by the given {@code name} and {@code options}.
     *
     * @param name    server name
     * @param options options
     *
     * @return a new instance of {@link HttpServer}
     */
    static HttpServer create(String name, ServerOptions options) {
        return new HttpServerImpl(name, options);
    }

    /**
     * Sets the handler for handing requests received.
     *
     * @param h handler
     *
     * @return this
     */
    HttpServer handle(Consumer<RequestHandle> h);

    /**
     * Sets the handler for listening connection connected.
     *
     * @param h handler
     *
     * @return this
     */
    HttpServer onConnected(Consumer<ChannelHandlerContext> h);

    /**
     * Sets the handler for listening connection disconnected.
     *
     * @param h handler
     *
     * @return this
     */
    HttpServer onDisconnected(Consumer<Channel> h);

    /**
     * Adds a close hook which will be called when server is about to closing.
     *
     * @param closure hook
     *
     * @return this
     */
    HttpServer onClose(Runnable closure);

    /**
     * Starts the server which is listening on the given {@code port}.
     *
     * @param port port to listen on
     *
     * @return this
     */
    HttpServer listen(int port);

    /**
     * Starts the server which is listening on the given {@code host} and {@code port}.
     *
     * @param host host to listen on
     * @param port port to listen on
     *
     * @return binding future
     */
    HttpServer listen(String host, int port);

    /**
     * Starts the server which is listening on the given {@code address}.
     *
     * @param address address to listen on
     *
     * @return binding future
     */
    HttpServer listen(SocketAddress address);

    /**
     * Returns the identity name of this server.
     * <p>
     * !Note: this name would not be same with the {@code name} you passed in the {@link #create(String, ServerOptions)}
     * for avoiding passing the same many a time
     *
     * @return server name
     */
    String name();

    /**
     * Waits for this server to be closed.
     */
    void await() throws InterruptedException;

    /**
     * Waits for this server to be closed.
     */
    void awaitUninterruptibly();

    /**
     * Returns the {@link Future} which is notified when server is closed(probably by calling {@link #close()}).
     *
     * @return future
     */
    Future<Void> closeFuture();

    /**
     * Returns the boss {@link EventLoopGroup} which is used to accepting new connections.
     *
     * @return boss {@link EventLoopGroup} or {@code null} if server is not running.
     */
    EventLoopGroup bossGroup();

    /**
     * Returns the io {@link EventLoopGroup} which is used to handing I/O events.
     *
     * @return io {@link EventLoopGroup} or {@code null} if server is not running.
     */
    EventLoopGroup ioGroup();

    /**
     * Returns the {@link SocketAddress} that the server is listening on.
     *
     * @return {@link SocketAddress} or {@code null} if server is not running.
     */
    SocketAddress address();

    /**
     * Returns the metrics of current server.
     * <p>
     * This method will never return null, but may return an {@link Metrics} which does not have any metrics.
     *
     * @return metrics
     */
    Metrics metrics();

    /**
     * Closes current server.
     */
    void close();
}
