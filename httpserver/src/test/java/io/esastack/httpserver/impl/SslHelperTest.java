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

import io.esastack.httpserver.SslOptions;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SslHelperTest {

    @Test
    void testNoneSsl() {
        final SslHelper sslHelper = new SslHelper(null, true);
        assertFalse(sslHelper.isSsl());
        assertNull(sslHelper.options());
        assertNull(sslHelper.getSslContext());
    }

    @Test
    void testCreateSslContext() throws CertificateException {

        final SslOptions ssl = new SslOptions();
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        ssl.setCiphers(new String[]{"AES256-SHA", "AES128-SHA"});
        final SelfSignedCertificate cert = new SelfSignedCertificate();
        ssl.setCertificate(cert.certificate());
        ssl.setPrivateKey(cert.privateKey());
        ssl.setClientAuth(ClientAuth.NONE);
        ssl.setSessionCacheSize(10L);


        final SslHelper sslHelper = new SslHelper(ssl, true);
        assertTrue(sslHelper.isSsl());
        assertSame(ssl, sslHelper.options());

        final SslContext sslContext = sslHelper.getSslContext();
        assertNotNull(sslContext);
        assertTrue(sslContext.isServer());
        assertArrayEquals(ssl.getCiphers(), sslContext.cipherSuites().toArray());
        assertEquals(10L, sslContext.sessionCacheSize());
    }

}
