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

import esa.commons.Checks;
import esa.commons.ExceptionUtils;
import esa.commons.NetworkUtils;
import esa.commons.StringUtils;
import io.esastack.httpserver.HttpServer;
import io.esastack.httpserver.ServerOptions;
import io.esastack.httpserver.core.RequestHandle;
import io.esastack.httpserver.metrics.Metrics;
import io.esastack.httpserver.transport.Transport;
import io.esastack.httpserver.transport.Transports;
import io.esastack.httpserver.utils.LoggedThreadFactory;
import io.esastack.httpserver.utils.Loggers;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SocketUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class HttpServerImpl implements HttpServer {

    private static final AtomicInteger SERVER_NAME_IDENTIFIER = new AtomicInteger(0);

    private final ServerRuntime runtime;
    private Consumer<RequestHandle> handler;
    private Consumer<Channel> onConnectionInit;
    private Consumer<Channel> onConnected;
    private Consumer<Channel> onDisconnected;
    private final CloseFuture closeFuture = new CloseFuture();
    private final CopyOnWriteArrayList<Runnable> closures = new CopyOnWriteArrayList<>();

    public HttpServerImpl(ServerOptions serverOptions) {
        this(null, serverOptions);
    }

    public HttpServerImpl(String name, ServerOptions serverOptions) {
        if (StringUtils.isEmpty(name)) {
            name = nextServerName();
        } else {
            name = name + "#" + SERVER_NAME_IDENTIFIER.incrementAndGet();
        }
        this.runtime = new ServerRuntime(StringUtils.nonEmptyOrElse(name, nextServerName()),
                serverOptions, closeFuture);
    }

    @Override
    public synchronized HttpServerImpl handle(Consumer<RequestHandle> h) {
        checkStarted();
        this.handler = h;
        return this;
    }

    @Override
    public HttpServer onConnectionInit(Consumer<Channel> h) {
        checkStarted();
        this.onConnectionInit = h;
        return this;
    }

    @Override
    public synchronized HttpServerImpl onConnected(Consumer<Channel> h) {
        checkStarted();
        this.onConnected = h;
        return this;
    }

    @Override
    public synchronized HttpServerImpl onDisconnected(Consumer<Channel> h) {
        checkStarted();
        this.onDisconnected = h;
        return this;
    }

    @Override
    public HttpServerImpl onClose(Runnable closure) {
        Checks.checkNotNull(closure, "closure");
        closures.add(closure);
        return this;
    }

    @Override
    public HttpServerImpl listen(int port) {
        return listen(new InetSocketAddress(port));
    }

    @Override
    public HttpServerImpl listen(String host, int port) {
        return listen(SocketUtils.socketAddress(host, port));
    }

    @Override
    public HttpServerImpl listen(SocketAddress address) {
        return listen0(address);
    }

    @Override
    public String name() {
        return runtime.name();
    }

    @Override
    public void await() throws InterruptedException {
        if (runtime.isRunning()) {
            closeFuture().await();
        }
    }

    @Override
    public void awaitUninterruptibly() {
        if (runtime.isRunning()) {
            closeFuture().awaitUninterruptibly();
        }
    }

    @Override
    public Future<Void> closeFuture() {
        return closeFuture;
    }

    private synchronized HttpServerImpl listen0(SocketAddress address) {
        checkStarted();
        Checks.checkNotNull(address, "address");
        Checks.checkNotNull(handler, "Request handler required. Set it by HttpServer.handle(xxx)");
        final ServerBootstrap bootstrap = new ServerBootstrap();

        final Transport transport = Transports.transport(options().isPreferNativeTransport());
        bootstrap.channelFactory(transport.serverChannelFactory(address));
        transport.applyOptions(bootstrap, options(), address);

        final SslHelper sslHelper = new SslHelper(options().getSsl(),
                options().getH2() != null && options().getH2().isEnabled());
        runtime.metrics().initSsl(sslHelper.getSslContext());
        bootstrap.childHandler(new HttpServerChannelInitializr(runtime,
                sslHelper,
                handler,
                onConnectionInit,
                onConnected,
                onDisconnected));
        final EventLoopGroup bossGroup = transport.loop(options().getBossThreads(),
                new LoggedThreadFactory(runtime.name() + "-Boss", options().isDaemon()));
        final EventLoopGroup ioGroup = transport.loop(options().getIoThreads(),
                new LoggedThreadFactory(runtime.name() + "-I/O", options().isDaemon()));
        bootstrap.group(bossGroup, ioGroup);
        // bind on local address
        try {
            bootstrap.bind(address).syncUninterruptibly();
        } catch (Exception e) {
            Loggers.logger().error("Failed to start http server({}) on {}",
                    runtime.name(), NetworkUtils.parseAddress(address), e);
            bossGroup.shutdownGracefully();
            ioGroup.shutdownGracefully();
            ExceptionUtils.throwException(e);
        }
        Loggers.logger().info("Http server({}) is listening on {}",
                runtime.name(), NetworkUtils.parseAddress(address));
        runtime.setStarted(address, bossGroup, ioGroup);
        return this;
    }

    @Override
    public EventLoopGroup bossGroup() {
        return runtime.bossGroup();
    }

    @Override
    public EventLoopGroup ioGroup() {
        return runtime.ioGroup();
    }

    @Override
    public SocketAddress address() {
        return runtime.address();
    }

    @Override
    public Metrics metrics() {
        return runtime.metrics();
    }

    @Override
    public synchronized void close() {

        if (!runtime.isRunning()) {
            return;
        }
        close0();
    }

    private void close0() {
        Loggers.logger().info("Closing http server({}) ...", name());

        final long start = System.nanoTime();

        ServerRuntime.Running status = runtime.setClosed();
        assert status != null;

        Throwable t = null;

        // shutdown boss loop and stop accepting new connection
        try {
            if (status.bossGroup != null) {
                status.bossGroup.shutdownGracefully();
            }
        } catch (Throwable ex) {
            t = ex;
        }

        if (!closures.isEmpty()) {
            for (Runnable closure : closures) {
                try {
                    closure.run();
                } catch (Throwable ex) {
                    Loggers.logger().warn("Error while running closure of http server({})", name());
                }
            }
        }

        // shutdown boss loop and stop IO read/write
        if (status.ioGroup != null) {
            try {
                status.ioGroup.shutdownGracefully();
            } catch (Throwable ex) {
                t = ex;
            }
        }

        closeFuture.setClosed();
        if (t != null) {
            Loggers.logger().error("Error while closing http server({})", name(), t);
            ExceptionUtils.throwException(t);
        }
        Loggers.logger().info("Http server({}) closed in {} mills",
                name(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    }

    private void checkStarted() {
        if (runtime.isRunning()) {
            throw new IllegalStateException("Server already started yet.");
        }
    }

    private ServerOptions options() {
        return runtime.options();
    }

    private static String nextServerName() {
        return "esa.httpserver#" + SERVER_NAME_IDENTIFIER.incrementAndGet();
    }

    static final class CloseFuture extends DefaultPromise<Void> {

        CloseFuture() {
            super(GlobalEventExecutor.INSTANCE);
        }

        @Override
        public Promise<Void> setSuccess(Void result) {
            throw new IllegalStateException();
        }


        @Override
        public ChannelPromise setFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        @Override
        public boolean trySuccess(Void result) {
            throw new IllegalStateException();
        }

        @Override
        public boolean tryFailure(Throwable cause) {
            throw new IllegalStateException();
        }

        private boolean setClosed() {
            return super.trySuccess(null);
        }
    }
}
