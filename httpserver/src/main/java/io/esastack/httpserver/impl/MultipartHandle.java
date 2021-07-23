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

import esa.commons.ExceptionUtils;
import esa.commons.collection.LinkedMultiValueMap;
import esa.commons.collection.MultiMaps;
import esa.commons.collection.MultiValueMap;
import io.esastack.httpserver.core.MultiPart;
import io.esastack.httpserver.core.MultipartFile;
import io.esastack.httpserver.utils.Loggers;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

final class MultipartHandle implements MultiPart {

    static final Empty EMPTY = new Empty();

    private final HttpPostRequestDecoder decoder;
    private List<MultipartFile> files;
    private MultiValueMap<String, String> attrs;

    MultipartHandle(HttpPostRequestDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public List<MultipartFile> uploadFiles() {
        if (files == null) {
            return EMPTY.uploadFiles();
        }
        return files;
    }

    @Override
    public MultiValueMap<String, String> attributes() {
        if (attrs == null) {
            return EMPTY.attributes();
        }
        return attrs;
    }

    void onData(ByteBuf buf) {
        decoder.offer(new DefaultHttpContent(buf));
    }

    void end() {
        List<MultipartFile> files = null;
        MultiValueMap<String, String> attrs = null;
        try {
            decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
            List<InterfaceHttpData> decoded = decoder.getBodyHttpDatas();
            for (InterfaceHttpData data : decoded) {
                if (InterfaceHttpData.HttpDataType.Attribute.equals(data.getHttpDataType())) {
                    Attribute attr = (Attribute) data;
                    if (attrs == null) {
                        attrs = new LinkedMultiValueMap<>();
                    }
                    attrs.add(attr.getName(), attr.getValue());
                } else if (InterfaceHttpData.HttpDataType.FileUpload.equals(data.getHttpDataType())) {
                    if (files == null) {
                        files = new LinkedList<>();
                    }
                    files.add(new MultipartFileImpl((FileUpload) data));
                }
            }

            if (files != null) {
                this.files = files;
            }
            if (attrs != null) {
                this.attrs = attrs;
            }
        } catch (IOException e) {
            ExceptionUtils.throwException(e);
        }
    }

    void release() {
        try {
            decoder.destroy();
        } catch (Exception e) {
            Loggers.logger().warn(
                    "Unexpected error while releasing multipart resources", e);
        }
    }

    private static class Empty implements MultiPart {

        @Override
        public List<MultipartFile> uploadFiles() {
            return Collections.emptyList();
        }

        @Override
        public MultiValueMap<String, String> attributes() {
            return MultiMaps.emptyMultiMap();
        }
    }
}
