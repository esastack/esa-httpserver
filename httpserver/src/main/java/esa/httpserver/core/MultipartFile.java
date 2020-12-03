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
package esa.httpserver.core;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * A representation of uploaded file which is wrapped from {@link FileUpload}.
 */
public interface MultipartFile {
    /**
     * Return the name of the parameter in the multipart form.
     *
     * @return the name of the parameter (never {@code null} or empty)
     */
    String name();

    /**
     * Get the original file name.
     *
     * @return original file name.
     */
    String fileName();

    /**
     * Return the content type of the file.
     *
     * @return the content type, or {@code null} if not defined (or no file has been chosen in the multipart form)
     */
    String contentType();

    /**
     * Return the size of the file in bytes.
     *
     * @return the size of the file, or 0 if empty
     */
    long length();

    /**
     * The content transfer encoding.
     *
     * @return encoding
     */
    String contentTransferEncoding();

    /**
     * Whether the content is in memory.
     *
     * @return true if in memory, otherwise false
     */
    boolean isInMemory();

    /**
     * Get the file on disk. Note that if the {@link #isInMemory()} is true then an IOException will be thrown.
     *
     * @return file
     * @throws IOException ex
     */
    File file() throws IOException;

    /**
     * Returns the content of the file item as a ByteBuf
     *
     * @return the content of the file item as a ByteBuf
     */
    ByteBuf getByteBuf() throws IOException;

    /**
     * Transfer this to destination file.
     *
     * @param dest destination
     */
    void transferTo(File dest) throws IOException;

    /**
     * Get file content with default charset of UTF-8.
     *
     * @return file content
     * @throws IOException ex
     */
    String string() throws IOException;

    /**
     * Get file content with specified charset.
     *
     * @param charset charset
     *
     * @return file content in string
     * @throws IOException ex
     */
    String string(Charset charset) throws IOException;

    /**
     * Release the byteBuf and delete the temp file on disk. Note: It's important to release resource, you can get more
     * information from {@link FileUpload#delete()}. In fact, we'll be happy that you call this method manually.
     */
    void delete();
}
