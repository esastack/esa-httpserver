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
import io.netty.handler.ssl.ClientAuth;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

public class SslOptions implements Serializable {

    private static final long serialVersionUID = 6772202423835136508L;

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

    public SslOptions() {
    }

    public SslOptions(SslOptions other) {
        Checks.checkNotNull(other, "other");
        this.clientAuth = other.clientAuth;
        this.ciphers = other.ciphers == null ? null : Arrays.copyOf(other.ciphers, other.ciphers.length);
        this.enabledProtocols = other.enabledProtocols == null
                ? null
                : Arrays.copyOf(other.enabledProtocols, other.enabledProtocols.length);
        this.certificate = other.certificate;
        this.privateKey = other.privateKey;
        this.keyPassword = other.keyPassword;
        this.trustCertificates = other.trustCertificates;
        this.sessionTimeout = other.sessionTimeout;
        this.sessionCacheSize = other.sessionCacheSize;
        this.handshakeTimeoutMillis = other.handshakeTimeoutMillis;
    }

    public ClientAuth getClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(ClientAuth clientAuth) {
        this.clientAuth = clientAuth;
    }

    public String[] getCiphers() {
        return ciphers;
    }

    public void setCiphers(String[] ciphers) {
        this.ciphers = ciphers;
    }

    public String[] getEnabledProtocols() {
        return enabledProtocols;
    }

    public void setEnabledProtocols(String[] enabledProtocols) {
        this.enabledProtocols = enabledProtocols;
    }

    public File getCertificate() {
        return certificate;
    }

    public void setCertificate(File certificate) {
        this.certificate = certificate;
    }

    public File getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(File privateKey) {
        this.privateKey = privateKey;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public File getTrustCertificates() {
        return trustCertificates;
    }

    public void setTrustCertificates(File trustCertificates) {
        this.trustCertificates = trustCertificates;
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public long getSessionCacheSize() {
        return sessionCacheSize;
    }

    public void setSessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
    }

    public long getHandshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public void setHandshakeTimeoutMillis(long handshakeTimeoutMillis) {
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }
}
