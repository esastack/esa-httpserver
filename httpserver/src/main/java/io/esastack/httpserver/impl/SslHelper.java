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

import esa.commons.ExceptionUtils;
import io.esastack.httpserver.SslOptions;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.SSLException;
import java.util.Arrays;

final class SslHelper {

    private final SslOptions ssl;
    private final SslContext sslContext;

    SslHelper(SslOptions ssl, boolean isH2Enabled) {
        this.ssl = ssl;
        this.sslContext = createContext(isH2Enabled);
    }

    private SslContext createContext(boolean isH2Enabled) {
        if (!isSsl()) {
            return null;
        }

        final SslContextBuilder sslContextBuilder =
                SslContextBuilder.forServer(ssl.getCertificate(),
                        ssl.getPrivateKey(),
                        ssl.getKeyPassword());

        sslContextBuilder.sslProvider(detectSslProvider());

        if (isH2Enabled) {
            sslContextBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1));
        }

        if (ssl.getClientAuth() != null) {
            sslContextBuilder.clientAuth(ssl.getClientAuth());
        }
        if (ssl.getSessionTimeout() > 0) {
            sslContextBuilder.sessionTimeout(ssl.getSessionTimeout());
        }
        if (ssl.getSessionCacheSize() > 0) {
            sslContextBuilder.sessionCacheSize(ssl.getSessionCacheSize());
        }
        if (ssl.getCiphers() != null && ssl.getCiphers().length > 0) {
            sslContextBuilder.ciphers(Arrays.asList(ssl.getCiphers()));
        }
        if (ssl.getTrustCertificates() != null) {
            sslContextBuilder.trustManager(ssl.getTrustCertificates());
        }

        try {
            return sslContextBuilder.build();
        } catch (SSLException e) {
            throw ExceptionUtils.asRuntime(e);
        }
    }

    boolean isSsl() {
        return ssl != null;
    }

    SslOptions options() {
        return ssl;
    }

    SslContext getSslContext() {
        return sslContext;
    }

    private static SslProvider detectSslProvider() {
        if (OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL;
        } else {
            return SslProvider.JDK;
        }
    }

}
