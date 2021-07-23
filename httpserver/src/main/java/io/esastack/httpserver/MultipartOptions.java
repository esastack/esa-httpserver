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

import esa.commons.Checks;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskFileUpload;

import java.nio.charset.Charset;

/**
 * The wrapper config for {@link DefaultHttpDataFactory}
 */
public class MultipartOptions {

    /**
     * Save the multipart to disk no matter what size the item is when the value is true.
     */
    private boolean useDisk;

    /**
     * Default memoryThreshold as 2MB.
     */
    private long memoryThreshold = 2L * 1024L * 1024L;

    /**
     * Default maxSize as -1 which means disable sizeLimit
     */
    private long maxSize = DefaultHttpDataFactory.MAXSIZE;

    /**
     * Default charset as UTF-8
     */
    private Charset charset = HttpConstants.DEFAULT_CHARSET;

    /**
     * The directory of temp file. see {@link DiskFileUpload#baseDirectory}
     */
    private String tempDir;

    public MultipartOptions() {
    }

    public MultipartOptions(MultipartOptions other) {
        Checks.checkNotNull(other, "other");
        this.useDisk = other.useDisk;
        this.memoryThreshold = other.memoryThreshold;
        this.maxSize = other.maxSize;
        this.charset = other.charset;
        this.tempDir = other.tempDir;
    }

    public boolean isUseDisk() {
        return useDisk;
    }

    public void setUseDisk(boolean useDisk) {
        this.useDisk = useDisk;
    }

    public long getMemoryThreshold() {
        return memoryThreshold;
    }

    public void setMemoryThreshold(long memoryThreshold) {
        this.memoryThreshold = memoryThreshold;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }
}
