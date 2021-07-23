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
import esa.commons.StringUtils;
import io.esastack.httpserver.MultipartOptions;
import io.esastack.httpserver.MultipartOptionsConfigure;
import io.esastack.httpserver.ServerOptions;
import io.esastack.httpserver.metrics.impl.MetricsImpl;
import io.esastack.httpserver.metrics.impl.MetricsReporter;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.util.DefaultAttributeMap;
import io.netty.util.concurrent.Future;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ServerRuntime
 */
public class ServerRuntime extends DefaultAttributeMap {

    private final String name;
    private final ServerOptions options;
    private final AtomicBoolean shutdownStatus = new AtomicBoolean(false);
    private final MetricsReporter metrics;
    private final HttpServerImpl.CloseFuture closeFuture;
    private volatile HttpDataFactory multipartDataFactory;
    private volatile Running running;

    ServerRuntime(String name,
                  ServerOptions options,
                  HttpServerImpl.CloseFuture closeFuture) {
        this.closeFuture = closeFuture;
        Checks.checkNotEmptyArg(name, "name");
        Checks.checkNotNull(options, "options");
        Checks.checkNotNull(shutdownStatus, "shutdownStatus");
        this.name = name;
        this.options = new ServerOptions(options);
        this.metrics = MetricsImpl.of(options.isMetricsEnabled());
    }

    public String name() {
        return name;
    }

    public ServerOptions options() {
        return options;
    }

    public AtomicBoolean shutdownStatus() {
        return shutdownStatus;
    }

    public MetricsReporter metrics() {
        return metrics;
    }

    public HttpDataFactory multipartDataFactory() {
        if (multipartDataFactory == null) {
            synchronized (this) {
                if (multipartDataFactory == null) {
                    multipartDataFactory = buildMultipartDataFactory();
                }
            }
        }
        return multipartDataFactory;
    }

    public boolean isRunning() {
        return running != null;
    }

    public SocketAddress address() {
        final Running status = running;
        return status == null ? null : status.address;
    }

    public EventLoopGroup bossGroup() {
        final Running status = running;
        return status == null ? null : status.bossGroup;
    }

    public EventLoopGroup ioGroup() {
        final Running status = running;
        return status == null ? null : status.ioGroup;
    }

    public Future<Void> closeFuture() {
        return closeFuture;
    }

    void setStarted(SocketAddress address,
                    EventLoopGroup bossGroup,
                    EventLoopGroup ioGroup) {
        this.running = new Running(address, bossGroup, ioGroup);
    }

    Running setClosed() {
        this.shutdownStatus.set(true);
        Running status = this.running;
        this.running = null;
        return status;
    }

    private HttpDataFactory buildMultipartDataFactory() {
        MultipartOptions config = options().getMultipart();
        HttpDataFactory factory;
        if (config == null) {
            config = MultipartOptionsConfigure.newOpts().configured();
        }
        if (config.isUseDisk()) {
            factory = new DefaultHttpDataFactory(config.isUseDisk(), config.getCharset());
        } else {
            factory = new DefaultHttpDataFactory(config.getMemoryThreshold(), config.getCharset());
        }
        factory.setMaxLimit(config.getMaxSize());
        final String tempDir = config.getTempDir();
        if (StringUtils.isNotEmpty(tempDir)) {
            DiskFileUpload.baseDirectory = tempDir;
        }
        return factory;
    }

    static final class Running {
        final SocketAddress address;
        final EventLoopGroup bossGroup;
        final EventLoopGroup ioGroup;

        private Running(SocketAddress address,
                        EventLoopGroup bossGroup,
                        EventLoopGroup ioGroup) {
            this.address = address;
            this.bossGroup = bossGroup;
            this.ioGroup = ioGroup;
        }
    }

}
