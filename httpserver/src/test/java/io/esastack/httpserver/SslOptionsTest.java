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
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SslOptionsTest {

    @Test
    void testConfigure() {
        final File f = new File("");
        final SslOptions ssl = SslOptionsConfigure.newOpts()
                .clientAuth(ClientAuth.NONE)
                .ciphers(new String[]{"foo"})
                .enabledProtocols(new String[]{"bar"})
                .certificate(f)
                .privateKey(f)
                .keyPassword("baz")
                .trustCertificates(f)
                .sessionTimeout(1L)
                .sessionCacheSize(2L)
                .handshakeTimeoutMillis(3L)
                .configured();

        assertEquals(ClientAuth.NONE, ssl.getClientAuth());
        assertArrayEquals(new String[]{"foo"}, ssl.getCiphers());
        assertArrayEquals(new String[]{"bar"}, ssl.getEnabledProtocols());
        assertSame(f, ssl.getCertificate());
        assertSame(f, ssl.getPrivateKey());
        assertEquals("baz", ssl.getKeyPassword());
        assertSame(f, ssl.getTrustCertificates());
        assertEquals(1L, ssl.getSessionTimeout());
        assertEquals(2L, ssl.getSessionCacheSize());
        assertEquals(3L, ssl.getHandshakeTimeoutMillis());
    }

    @Test
    void testCopyFromAnother() {
        final File f = new File("");
        final SslOptions another = SslOptionsConfigure.newOpts()
                .clientAuth(ClientAuth.NONE)
                .ciphers(new String[]{"foo"})
                .enabledProtocols(new String[]{"bar"})
                .certificate(f)
                .privateKey(f)
                .keyPassword("baz")
                .trustCertificates(f)
                .sessionTimeout(1L)
                .sessionCacheSize(2L)
                .handshakeTimeoutMillis(3L)
                .configured();

        final SslOptions ssl = new SslOptions(another);

        assertEquals(ClientAuth.NONE, ssl.getClientAuth());
        assertArrayEquals(new String[]{"foo"}, ssl.getCiphers());
        assertArrayEquals(new String[]{"bar"}, ssl.getEnabledProtocols());
        assertSame(f, ssl.getCertificate());
        assertSame(f, ssl.getPrivateKey());
        assertEquals("baz", ssl.getKeyPassword());
        assertSame(f, ssl.getTrustCertificates());
        assertEquals(1L, ssl.getSessionTimeout());
        assertEquals(2L, ssl.getSessionCacheSize());
        assertEquals(3L, ssl.getHandshakeTimeoutMillis());
    }

}
