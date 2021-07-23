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

import esa.commons.Checks;
import esa.commons.annotation.Beta;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class NetOptions implements Serializable {

    private static final long serialVersionUID = -1189110101771190532L;

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
    private List<ChannelHandler> channelHandlers = new LinkedList<>();

    public NetOptions() {
    }

    public NetOptions(NetOptions other) {
        Checks.checkNotNull(other, "other");
        this.preferNativeTransport = other.preferNativeTransport;
        this.soKeepalive = other.soKeepalive;
        this.tcpNoDelay = other.tcpNoDelay;
        this.reuseAddress = other.reuseAddress;
        this.reusePort = other.reusePort;
        this.tcpFastOpen = other.tcpFastOpen;
        this.tcpCork = other.tcpCork;
        this.tcpQuickAck = other.tcpQuickAck;
        this.soRcvbuf = other.soRcvbuf;
        this.soSendbuf = other.soSendbuf;
        this.soBacklog = other.soBacklog;
        this.soLinger = other.soLinger;
        this.writeBufferHighWaterMark = other.writeBufferHighWaterMark;
        this.writeBufferLowWaterMark = other.writeBufferLowWaterMark;
        this.idleTimeoutSeconds = other.idleTimeoutSeconds;
        this.options.putAll(other.options);
        this.childOptions.putAll(other.childOptions);
        this.logging = other.logging;
        this.channelHandlers.addAll(other.channelHandlers);
    }

    public boolean isPreferNativeTransport() {
        return preferNativeTransport;
    }

    public void setPreferNativeTransport(boolean preferNativeTransport) {
        this.preferNativeTransport = preferNativeTransport;
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public boolean isReusePort() {
        return reusePort;
    }

    public void setReusePort(boolean reusePort) {
        this.reusePort = reusePort;
    }

    public boolean isSoKeepalive() {
        return soKeepalive;
    }

    public void setSoKeepalive(boolean soKeepalive) {
        this.soKeepalive = soKeepalive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public boolean isTcpFastOpen() {
        return tcpFastOpen;
    }

    public void setTcpFastOpen(boolean tcpFastOpen) {
        this.tcpFastOpen = tcpFastOpen;
    }

    public boolean isTcpCork() {
        return tcpCork;
    }

    public void setTcpCork(boolean tcpCork) {
        this.tcpCork = tcpCork;
    }

    public boolean isTcpQuickAck() {
        return tcpQuickAck;
    }

    public void setTcpQuickAck(boolean tcpQuickAck) {
        this.tcpQuickAck = tcpQuickAck;
    }

    public int getSoRcvbuf() {
        return soRcvbuf;
    }

    public void setSoRcvbuf(int soRcvbuf) {
        this.soRcvbuf = soRcvbuf;
    }

    public int getSoSendbuf() {
        return soSendbuf;
    }

    public void setSoSendbuf(int soSendbuf) {
        this.soSendbuf = soSendbuf;
    }

    public int getSoBacklog() {
        return soBacklog;
    }

    public void setSoBacklog(int soBacklog) {
        this.soBacklog = soBacklog;
    }

    public int getSoLinger() {
        return soLinger;
    }

    public void setSoLinger(int soLinger) {
        this.soLinger = soLinger;
    }

    public int getWriteBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
    }

    public int getWriteBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public Map<ChannelOption<?>, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<ChannelOption<?>, Object> options) {
        if (options != null && !options.isEmpty()) {
            this.options.clear();
            this.options.putAll(options);
        }
    }

    public Map<ChannelOption<?>, Object> getChildOptions() {
        return childOptions;
    }

    public void setChildOptions(Map<ChannelOption<?>, Object> options) {
        if (options != null && !options.isEmpty()) {
            this.childOptions.clear();
            this.childOptions.putAll(options);
        }
    }

    public LogLevel getLogging() {
        return logging;
    }

    public void setLogging(LogLevel logging) {
        this.logging = logging;
    }

    public List<ChannelHandler> getChannelHandlers() {
        return channelHandlers;
    }

    public void setChannelHandlers(List<ChannelHandler> channelHandlers) {
        this.channelHandlers = channelHandlers;
    }
}
