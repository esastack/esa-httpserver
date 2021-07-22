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
package esa.httpserver.it;

import esa.commons.NetworkUtils;
import esa.commons.io.IOUtils;
import esa.httpserver.HttpServer;
import esa.httpserver.ServerOptionsConfigure;
import esa.httpserver.core.Response;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.logging.LogLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkWriteTest {

    @Test
    void testWriteChunkResponse() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final int port = NetworkUtils.selectRandomPort();
        final CompositeByteBuf buf = Unpooled.compositeBuffer();
        final CompletableFuture<Boolean> ended = new CompletableFuture<>();
        final HttpServer server = HttpServer.create(ServerOptionsConfigure.newOpts()
                .logging(LogLevel.INFO)
                .ioThreads(1)
                .configured())
                .handle(req -> {
                    req.onData(data -> buf.writeBytes(data.retain()))
                            .onEnd(p -> {
                                ended.complete(true);
                                return p.setSuccess(null);
                            });
                    final Response res = req.response();
                    res.headers().set("a", 1);
                    res.write("1".getBytes());
                    res.write(Unpooled.copiedBuffer("2".getBytes()));
                    res.write("3".getBytes()).addListener(f -> {
                        res.write("4".getBytes()).addListener(f1 -> {
                            new Thread(() -> {
                                res.write(Unpooled.copiedBuffer("5".getBytes())).addListener(f2 -> {
                                    new Thread(() -> {
                                        res.end(Unpooled.copiedBuffer("6".getBytes()));
                                    }).start();
                                });
                            }).start();
                        });
                    });
                });

        server.listen(port);

        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection =
                    (HttpURLConnection) new URL("http://localhost:" + port).openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.getOutputStream().write("foo".getBytes());
            assertEquals(200, httpURLConnection.getResponseCode());
            assertEquals("123456", IOUtils.toString(httpURLConnection.getInputStream()));
            assertEquals("1", httpURLConnection.getHeaderField("a"));
            assertTrue(ended.get(3L, TimeUnit.SECONDS));
            assertEquals("foo", buf.toString(StandardCharsets.US_ASCII));
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

}
