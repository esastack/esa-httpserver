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

import esa.commons.Checks;
import io.esastack.httpserver.core.MultipartFile;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

final class MultipartFileImpl implements MultipartFile {

    private final FileUpload upload;

    MultipartFileImpl(FileUpload upload) {
        Checks.checkNotNull(upload, "upload");
        this.upload = upload;
    }

    @Override
    public String name() {
        return upload.getName();
    }

    @Override
    public String fileName() {
        return upload.getFilename();
    }

    @Override
    public String contentType() {
        return upload.getContentType();
    }

    @Override
    public long length() {
        return upload.length();
    }

    @Override
    public String contentTransferEncoding() {
        return upload.getContentTransferEncoding();
    }

    @Override
    public boolean isInMemory() {
        return upload.isInMemory();
    }

    @Override
    public File file() throws IOException {
        return upload.getFile();
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
        return upload.getByteBuf();
    }

    @Override
    public void transferTo(File dest) throws IOException {
        upload.renameTo(dest);
    }

    @Override
    public String string() throws IOException {
        return upload.getString();
    }

    @Override
    public String string(Charset charset) throws IOException {
        return upload.getString(charset);
    }

    @Override
    public void delete() {
        upload.delete();
    }
}
