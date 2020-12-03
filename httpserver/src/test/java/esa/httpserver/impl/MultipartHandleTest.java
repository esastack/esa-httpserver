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
package esa.httpserver.impl;

import esa.commons.collection.MultiMaps;
import esa.httpserver.core.MultiPart;
import esa.httpserver.core.MultipartFile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultipartHandleTest {

    @Test
    void testEmpty() {
        final MultiPart empty = MultipartHandle.EMPTY;
        assertSame(Collections.emptyList(), empty.uploadFiles());
        assertSame(MultiMaps.emptyMultiMap(), empty.attributes());
    }

    @Test
    void testDecodeFilesAndAttributes() throws IOException {

        final HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/foo");
        final String boundary = "dLV9Wyq26L_-JQxk6ferf-RT153LhOO";
        request.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
        request.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);


        final HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(request);
        final MultipartHandle handle = new MultipartHandle(decoder);
        assertSame(Collections.emptyList(), handle.uploadFiles());
        assertSame(MultiMaps.emptyMultiMap(), handle.attributes());

        final String body1 =
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"tmp-0.txt\"\r\n" +
                        "Content-Type: image/gif\r\n" +
                        "\r\n" +
                        "foo" + "\r\n" +
                        "--" + boundary;
        final String body2 = "\r\n" +
                "Content-Disposition: form-data; name=\"foo\"\r\n" +
                "\r\n" +
                "bar\r\n" +
                "--" + boundary + "--\r\n";

        handle.onData(Unpooled.copiedBuffer(body1, StandardCharsets.UTF_8));
        handle.onData(Unpooled.copiedBuffer(body2, StandardCharsets.UTF_8));
        assertSame(Collections.emptyList(), handle.uploadFiles());
        assertSame(MultiMaps.emptyMultiMap(), handle.attributes());

        try {
            handle.end();
            assertEquals(1, handle.uploadFiles().size());
            final MultipartFile upload = handle.uploadFiles().get(0);
            assertEquals("file", upload.name());
            assertEquals("tmp-0.txt", upload.fileName());
            assertEquals("foo", upload.string(StandardCharsets.UTF_8));

            assertEquals(1, handle.attributes().size());
            assertEquals("bar", handle.attributes().getFirst("foo"));
        } finally {
            handle.release();
        }

    }

}
