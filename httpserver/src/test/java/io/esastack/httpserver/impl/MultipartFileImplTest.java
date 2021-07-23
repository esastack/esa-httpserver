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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultipartFileImplTest {

    @Test
    void testDelegate() throws IOException {
        final FileUpload mock = mock(FileUpload.class);
        final MultipartFileImpl multipart = new MultipartFileImpl(mock);

        when(mock.getName()).thenReturn("foo");
        assertEquals("foo", multipart.name());
        verify(mock).getName();

        when(mock.getFilename()).thenReturn("bar");
        assertEquals("bar", multipart.fileName());
        verify(mock).getFilename();

        when(mock.getContentType()).thenReturn("baz");
        assertEquals("baz", multipart.contentType());
        verify(mock).getContentType();

        when(mock.length()).thenReturn(1L);
        assertEquals(1L, multipart.length());
        verify(mock).length();

        when(mock.getContentTransferEncoding()).thenReturn("qux");
        assertEquals("qux", multipart.contentTransferEncoding());
        verify(mock).getContentTransferEncoding();

        when(mock.isInMemory()).thenReturn(true);
        assertTrue(multipart.isInMemory());
        verify(mock).isInMemory();

        final File f = new File("");
        when(mock.getFile()).thenReturn(f);
        assertSame(f, multipart.file());
        verify(mock).getFile();

        when(mock.getByteBuf()).thenReturn(Unpooled.EMPTY_BUFFER);
        assertSame(Unpooled.EMPTY_BUFFER, multipart.getByteBuf());
        verify(mock).getByteBuf();

        multipart.transferTo(f);
        verify(mock).renameTo(same(f));

        when(mock.getString()).thenReturn("x");
        assertEquals("x", multipart.string());
        verify(mock).getString();

        when(mock.getString(any())).thenReturn("y");
        assertEquals("y", multipart.string(StandardCharsets.UTF_8));
        verify(mock).getString(eq(StandardCharsets.UTF_8));

        multipart.delete();
        verify(mock).delete();
    }

}
