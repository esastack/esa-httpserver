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

import io.netty.handler.ssl.ClientAuth;

import java.io.File;

public final class SslOptionsConfigure {
    private ClientAuth clientAuth;
    private String[] ciphers;
    private String[] enabledProtocols;
    private File certificate;
    private File privateKey;
    private String keyPassword;
    private File trustCertificates;
    private long sessionTimeout;
    private long sessionCacheSize;
    private long handshakeTimeoutMillis;

    private SslOptionsConfigure() {
    }

    public static SslOptionsConfigure newOpts() {
        return new SslOptionsConfigure();
    }

    public SslOptionsConfigure clientAuth(ClientAuth clientAuth) {
        this.clientAuth = clientAuth;
        return this;
    }

    public SslOptionsConfigure ciphers(String[] ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    public SslOptionsConfigure enabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
        return this;
    }

    public SslOptionsConfigure certificate(File certificate) {
        this.certificate = certificate;
        return this;
    }

    public SslOptionsConfigure privateKey(File privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public SslOptionsConfigure keyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
        return this;
    }

    public SslOptionsConfigure trustCertificates(File trustCertificates) {
        this.trustCertificates = trustCertificates;
        return this;
    }

    public SslOptionsConfigure sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    public SslOptionsConfigure sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    public SslOptionsConfigure handshakeTimeoutMillis(long handshakeTimeoutMillis) {
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        return this;
    }

    public SslOptions configured() {
        SslOptions sslOptions = new SslOptions();
        sslOptions.setClientAuth(clientAuth);
        sslOptions.setCiphers(ciphers);
        sslOptions.setEnabledProtocols(enabledProtocols);
        sslOptions.setCertificate(certificate);
        sslOptions.setPrivateKey(privateKey);
        sslOptions.setKeyPassword(keyPassword);
        sslOptions.setTrustCertificates(trustCertificates);
        sslOptions.setSessionTimeout(sessionTimeout);
        sslOptions.setSessionCacheSize(sessionCacheSize);
        sslOptions.setHandshakeTimeoutMillis(handshakeTimeoutMillis);
        return sslOptions;
    }
}
