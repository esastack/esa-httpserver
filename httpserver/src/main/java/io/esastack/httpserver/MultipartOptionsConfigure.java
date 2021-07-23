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

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;

import java.nio.charset.Charset;

public final class MultipartOptionsConfigure {
    private long memoryThreshold = 2L * 1024L * 1024L;
    private long maxSize = DefaultHttpDataFactory.MAXSIZE;
    private Charset charset = HttpConstants.DEFAULT_CHARSET;
    private String tempDir;
    private boolean useDisk;

    private MultipartOptionsConfigure() {
    }

    public static MultipartOptionsConfigure newOpts() {
        return new MultipartOptionsConfigure();
    }

    public MultipartOptionsConfigure memoryThreshold(long memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
        return this;
    }

    public MultipartOptionsConfigure maxSize(long maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    public MultipartOptionsConfigure charset(Charset charset) {
        this.charset = charset;
        return this;
    }

    public MultipartOptionsConfigure tempDir(String tempDir) {
        this.tempDir = tempDir;
        return this;
    }

    public MultipartOptionsConfigure useDisk(boolean useDisk) {
        this.useDisk = useDisk;
        return this;
    }

    public MultipartOptions configured() {
        MultipartOptions multipartOptions = new MultipartOptions();
        multipartOptions.setUseDisk(useDisk);
        multipartOptions.setMemoryThreshold(memoryThreshold);
        multipartOptions.setMaxSize(maxSize);
        multipartOptions.setCharset(charset);
        multipartOptions.setTempDir(tempDir);
        return multipartOptions;
    }
}
