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

import esa.commons.Platforms;
import esa.commons.annotation.Beta;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class ServerOptionsConfigure {

    private boolean daemon = true;
    private int bossThreads = 1;
    private int ioThreads = Platforms.cpuNum() << 1;
    private boolean compress = false;
    private boolean decompress = false;
    private int compressionLevel = 6;
    private boolean keepAliveEnable = true;
    private long maxContentLength = -1L;
    private int maxInitialLineLength = 4096;
    private int maxHeaderSize = 8192;
    private int maxChunkSize = 8192;
    private boolean metricsEnabled = false;
    private HAProxyMode haProxy = HAProxyMode.OFF;
    private SslOptions ssl;
    private H2Options h2 = new H2Options();
    private MultipartOptions multipart = new MultipartOptions();

    private boolean preferNativeTransport = true;
    private boolean soKeepalive = true;
    private boolean tcpNoDelay = true;
    private boolean reuseAddress = true;
    private boolean reusePort = true;
    private boolean tcpFastOpen;
    private boolean tcpCork;
    private boolean tcpQuickAck;
    private int soRcvbuf;
    private int soSendbuf;
    private int soBacklog = 128;
    private int soLinger = -1;
    private int writeBufferHighWaterMark;
    private int writeBufferLowWaterMark;
    private int idleTimeoutSeconds = 60;
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<>();
    private LogLevel logging;
    @Beta
    private final List<ChannelHandler> channelHandlers = new LinkedList<>();

    private ServerOptionsConfigure() {
    }

    public static ServerOptionsConfigure newOpts() {
        return new ServerOptionsConfigure();
    }

    public ServerOptionsConfigure daemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    public ServerOptionsConfigure bossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
        return this;
    }

    public ServerOptionsConfigure ioThreads(int ioThreads) {
        this.ioThreads = ioThreads;
        return this;
    }

    public ServerOptionsConfigure compress(boolean compress) {
        this.compress = compress;
        return this;
    }

    public ServerOptionsConfigure decompress(boolean decompress) {
        this.decompress = decompress;
        return this;
    }

    public ServerOptionsConfigure compressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
        return this;
    }

    public ServerOptionsConfigure keepAliveEnable(boolean keepAliveEnable) {
        this.keepAliveEnable = keepAliveEnable;
        return this;
    }

    public ServerOptionsConfigure maxContentLength(long maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }

    public ServerOptionsConfigure maxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public ServerOptionsConfigure maxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
        return this;
    }

    public ServerOptionsConfigure maxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public ServerOptionsConfigure metricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
        return this;
    }

    public ServerOptionsConfigure haProxy(HAProxyMode haProxy) {
        this.haProxy = haProxy;
        return this;
    }

    public ServerOptionsConfigure ssl(SslOptions ssl) {
        this.ssl = ssl;
        return this;
    }

    public ServerOptionsConfigure h2(H2Options h2) {
        this.h2 = h2;
        return this;
    }

    public ServerOptionsConfigure multipart(MultipartOptions multipart) {
        this.multipart = multipart;
        return this;
    }

    public ServerOptionsConfigure preferNativeTransport(boolean preferNativeTransport) {
        this.preferNativeTransport = preferNativeTransport;
        return this;
    }

    public ServerOptionsConfigure soKeepalive(boolean soKeepalive) {
        this.soKeepalive = soKeepalive;
        return this;
    }

    public ServerOptionsConfigure tcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public ServerOptionsConfigure reuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return this;
    }

    public ServerOptionsConfigure reusePort(boolean reusePort) {
        this.reusePort = reusePort;
        return this;
    }

    public ServerOptionsConfigure tcpFastOpen(boolean tcpFastOpen) {
        this.tcpFastOpen = tcpFastOpen;
        return this;
    }

    public ServerOptionsConfigure tcpCork(boolean tcpCork) {
        this.tcpCork = tcpCork;
        return this;
    }

    public ServerOptionsConfigure tcpQuickAck(boolean tcpQuickAck) {
        this.tcpQuickAck = tcpQuickAck;
        return this;
    }

    public ServerOptionsConfigure soRcvbuf(int soRcvbuf) {
        this.soRcvbuf = soRcvbuf;
        return this;
    }

    public ServerOptionsConfigure soSendbuf(int soSendbuf) {
        this.soSendbuf = soSendbuf;
        return this;
    }

    public ServerOptionsConfigure soBacklog(int soBacklog) {
        this.soBacklog = soBacklog;
        return this;
    }

    public ServerOptionsConfigure soLinger(int soLinger) {
        this.soLinger = soLinger;
        return this;
    }

    public ServerOptionsConfigure writeBufferHighWaterMark(int writeBufferHighWaterMark) {
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
        return this;
    }

    public ServerOptionsConfigure writeBufferLowWaterMark(int writeBufferLowWaterMark) {
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
        return this;
    }

    public ServerOptionsConfigure idleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        return this;
    }

    public ServerOptionsConfigure logging(LogLevel logging) {
        this.logging = logging;
        return this;
    }

    public ServerOptionsConfigure channelHandlers(Collection<? extends ChannelHandler> channelHandlers) {
        if (channelHandlers != null && !channelHandlers.isEmpty()) {
            this.channelHandlers.addAll(channelHandlers);
        }
        return this;
    }

    public ServerOptionsConfigure options(Map<ChannelOption<?>, Object> options) {
        if (options != null && !options.isEmpty()) {
            this.options.clear();
            this.options.putAll(options);
        }
        return this;
    }

    public <T> ServerOptionsConfigure option(ChannelOption<T> option, T value) {
        this.options.put(option, value);
        return this;
    }

    public ServerOptionsConfigure childOptions(Map<ChannelOption<?>, Object> options) {
        if (options != null && !options.isEmpty()) {
            this.childOptions.clear();
            this.childOptions.putAll(options);
        }
        return this;
    }

    public <T> ServerOptionsConfigure childOption(ChannelOption<T> option, T value) {
        this.childOptions.put(option, value);
        return this;
    }

    public ServerOptions configured() {
        ServerOptions serverOptions = new ServerOptions();
        serverOptions.setDaemon(daemon);
        serverOptions.setBossThreads(bossThreads);
        serverOptions.setIoThreads(ioThreads);
        serverOptions.setCompress(compress);
        serverOptions.setDecompress(decompress);
        serverOptions.setCompressionLevel(compressionLevel);
        serverOptions.setKeepAliveEnable(keepAliveEnable);
        serverOptions.setMaxContentLength(maxContentLength);
        serverOptions.setMaxInitialLineLength(maxInitialLineLength);
        serverOptions.setMaxHeaderSize(maxHeaderSize);
        serverOptions.setMaxChunkSize(maxChunkSize);
        serverOptions.setMetricsEnabled(metricsEnabled);
        serverOptions.setHaProxy(haProxy);
        serverOptions.setSsl(ssl);
        serverOptions.setH2(h2);
        serverOptions.setMultipart(multipart);

        serverOptions.setPreferNativeTransport(preferNativeTransport);
        serverOptions.setSoKeepalive(soKeepalive);
        serverOptions.setTcpNoDelay(tcpNoDelay);
        serverOptions.setReuseAddress(reuseAddress);
        serverOptions.setReusePort(reusePort);
        serverOptions.setTcpFastOpen(tcpFastOpen);
        serverOptions.setTcpCork(tcpCork);
        serverOptions.setTcpQuickAck(tcpQuickAck);
        serverOptions.setSoRcvbuf(soRcvbuf);
        serverOptions.setSoSendbuf(soSendbuf);
        serverOptions.setSoBacklog(soBacklog);
        serverOptions.setSoLinger(soLinger);
        serverOptions.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        serverOptions.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        serverOptions.setIdleTimeoutSeconds(idleTimeoutSeconds);
        serverOptions.setLogging(logging);
        serverOptions.setChannelHandlers(channelHandlers);
        serverOptions.setOptions(options);
        serverOptions.setChildOptions(childOptions);
        return serverOptions;
    }
}
