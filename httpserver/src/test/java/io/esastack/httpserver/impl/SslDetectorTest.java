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
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.junit.jupiter.api.Assertions.*;

class SslDetectorTest {

    @Test
    void testSslDetected() throws CertificateException {

        final SslOptions ssl = new SslOptions();
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        final SelfSignedCertificate certificate = new SelfSignedCertificate();
        ssl.setCertificate(certificate.certificate());
        ssl.setPrivateKey(certificate.privateKey());

        final EmbeddedChannel channel = new EmbeddedChannel(new SslDetector(new SslHelper(ssl, false),
                (isSsl, ch, t) -> {
                }));

        final int handlerSize = channel.pipeline().names().size();
        // Push the first part of a 5-byte handshake message.
        channel.writeInbound(wrappedBuffer(new byte[]{22, 3, 1}));
        // need more bytes
        assertEquals(handlerSize, channel.pipeline().names().size());
        assertNull(channel.readInbound());
        channel.writeInbound(wrappedBuffer(new byte[]{0, 5}));

        assertNotNull(channel.pipeline().get(SslHandler.class));
        assertNotNull(channel.pipeline().get(SslCompletionHandler.class));

        assertTrue(channel.pipeline().names().indexOf("SslHandler")
                < channel.pipeline().names().indexOf("SslCompletionHandler"));
    }

    @Test
    void testSslDetectFailed() throws CertificateException {

        final SslOptions ssl = new SslOptions();
        ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
        final SelfSignedCertificate certificate = new SelfSignedCertificate();
        ssl.setCertificate(certificate.certificate());
        ssl.setPrivateKey(certificate.privateKey());

        final AtomicInteger ret = new AtomicInteger(0);
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final EmbeddedChannel channel = new EmbeddedChannel(new SslDetector(new SslHelper(ssl, false),
                (isSsl, ch, t) -> {
                    ret.set(isSsl ? 1 : -1);
                    err.set(t);
                }));

        final ByteBuf buf = wrappedBuffer("12345".getBytes());
        channel.writeInbound(buf);

        assertSame(buf, channel.readInbound());

        assertNull(channel.pipeline().get(SslHandler.class));
        assertNull(channel.pipeline().get(SslCompletionHandler.class));
        assertEquals(-1, ret.get());
        assertNull(err.get());

    }

}
