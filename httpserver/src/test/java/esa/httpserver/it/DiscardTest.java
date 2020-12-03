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
import esa.httpserver.HttpServer;
import esa.httpserver.ServerOptionsConfigure;
import io.netty.handler.logging.LogLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscardTest {

    @Test
    void testDiscard() throws IOException {
        final int port = NetworkUtils.selectRandomPort();
        final HttpServer server = HttpServer.create(ServerOptionsConfigure.newOpts()
                .logging(LogLevel.INFO)
                .ioThreads(1)
                .configured())
                .handle(req -> req.response().end());

        server.listen(port);

        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection =
                    (HttpURLConnection) new URL("http://localhost:" + port).openConnection();
            assertEquals(200, httpURLConnection.getResponseCode());
        } finally {
            try {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception ignored) {
            }
            server.close();
        }
    }


}
