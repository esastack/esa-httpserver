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

public class ServerOptions extends NetOptions {

    private static final long serialVersionUID = 822297421367421793L;

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

    public ServerOptions() {
    }

    public ServerOptions(ServerOptions other) {
        super(other);
        this.daemon = other.daemon;
        this.bossThreads = other.bossThreads;
        this.ioThreads = other.ioThreads;
        this.compress = other.compress;
        this.decompress = other.decompress;
        this.compressionLevel = other.compressionLevel;
        this.keepAliveEnable = other.keepAliveEnable;
        this.maxContentLength = other.maxContentLength;
        this.maxInitialLineLength = other.maxInitialLineLength;
        this.maxHeaderSize = other.maxHeaderSize;
        this.maxChunkSize = other.maxChunkSize;
        this.metricsEnabled = other.metricsEnabled;
        this.haProxy = other.haProxy;
        this.ssl = other.ssl == null ? null : new SslOptions(other.ssl);
        this.h2 = other.h2 == null ? null : new H2Options(other.h2);
        this.multipart = other.multipart == null ? null : new MultipartOptions(other.multipart);
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getIoThreads() {
        return ioThreads;
    }

    public void setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
    }

    public boolean isCompress() {
        return compress;
    }

    public void setCompress(boolean compress) {
        this.compress = compress;
    }

    public boolean isDecompress() {
        return decompress;
    }

    public void setDecompress(boolean decompress) {
        this.decompress = decompress;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public boolean isKeepAliveEnable() {
        return keepAliveEnable;
    }

    public void setKeepAliveEnable(boolean keepAliveEnable) {
        this.keepAliveEnable = keepAliveEnable;
    }

    public long getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(long maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    public void setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
    }

    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    public void setMaxHeaderSize(int maxHeaderSize) {
        this.maxHeaderSize = maxHeaderSize;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public HAProxyMode getHaProxy() {
        return haProxy;
    }

    public void setHaProxy(HAProxyMode haProxy) {
        this.haProxy = haProxy;
    }

    public SslOptions getSsl() {
        return ssl;
    }

    public void setSsl(SslOptions ssl) {
        this.ssl = ssl;
    }

    public H2Options getH2() {
        return h2;
    }

    public void setH2(H2Options h2) {
        this.h2 = h2;
    }

    public MultipartOptions getMultipart() {
        return multipart;
    }

    public void setMultipart(MultipartOptions multipart) {
        this.multipart = multipart;
    }
}
