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
package io.esastack.httpserver.it;

import esa.commons.NetworkUtils;
import esa.commons.io.IOUtils;
import io.esastack.httpserver.HttpServer;
import io.esastack.httpserver.ServerOptionsConfigure;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.logging.LogLevel;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GzipTest {

    @Test
    void testCompressAndDecompress() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final int port = NetworkUtils.selectRandomPort();
        final CompositeByteBuf buf = Unpooled.compositeBuffer();
        final CompletableFuture<Boolean> ended = new CompletableFuture<>();
        final HttpServer server = HttpServer.create(ServerOptionsConfigure.newOpts()
                .logging(LogLevel.INFO)
                .ioThreads(1)
                .compress(true)
                .decompress(true)
                .configured())
                .handle(req -> {
                    req.onData(data -> buf.writeBytes(data.retain()))
                            .onEnd(p -> {
                                ended.complete(true);
                                return p.setSuccess(null);
                            });
                    req.response().write("12".getBytes());
                    req.response().write("34".getBytes());
                    req.response().write("56".getBytes());
                    req.response().end("78".getBytes());
                });

        server.listen(port);

        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection =
                    (HttpURLConnection) new URL("http://localhost:" + port).openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestProperty("Content-Encoding", "gzip");
            httpURLConnection.setRequestProperty("Accept-Encoding", "gzip");
            httpURLConnection.getOutputStream().write(zip("data for test".getBytes(StandardCharsets.UTF_8)));
            assertEquals(200, httpURLConnection.getResponseCode());
            assertTrue(ended.get(3L, TimeUnit.SECONDS));
            assertEquals("data for test", buf.toString(StandardCharsets.US_ASCII));
            assertEquals("12345678",
                    new String(unzip(httpURLConnection.getInputStream()), StandardCharsets.UTF_8));

        } finally {
            try {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception ignored) {
            }
            server.close();
            buf.release();
        }
    }


    private static byte[] zip(byte[] input) {
        GZIPOutputStream gzipOS = null;
        try {
            ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
            gzipOS = new GZIPOutputStream(byteArrayOS);
            gzipOS.write(input);
            gzipOS.flush();
            gzipOS.close();
            gzipOS = null;
            return byteArrayOS.toByteArray();
        } catch (Exception ignored) {
        } finally {
            if (gzipOS != null) {
                try {
                    gzipOS.close();
                } catch (Exception ignored) {
                }
            }
        }
        return new byte[0];
    }

    private static byte[] unzip(InputStream input) {
        try (GZIPInputStream gzipIS = new GZIPInputStream(input)) {
            return IOUtils.toByteArray(gzipIS);
        } catch (Exception ignored) {
        }
        return new byte[0];
    }


}
