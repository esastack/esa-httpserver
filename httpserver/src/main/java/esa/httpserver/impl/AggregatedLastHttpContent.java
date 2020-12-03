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

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.internal.StringUtil;

import java.util.Map;

final class AggregatedLastHttpContent implements LastHttpContent {

    private final ByteBuf content;
    private final HttpHeaders trailers;

    AggregatedLastHttpContent(ByteBuf content, HttpHeaders trailers) {
        this.content = content;
        this.trailers = trailers;
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public AggregatedLastHttpContent copy() {
        return replace(content.copy());
    }

    @Override
    public AggregatedLastHttpContent duplicate() {
        return replace(content.duplicate());
    }

    @Override
    public AggregatedLastHttpContent replace(ByteBuf content) {
        return new AggregatedLastHttpContent(content, trailers.copy());
    }

    @Override
    public AggregatedLastHttpContent retainedDuplicate() {
        return replace(content.retainedDuplicate());
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return trailers;
    }

    @Override
    public DecoderResult decoderResult() {
        return DecoderResult.SUCCESS;
    }

    @Override
    @Deprecated
    public DecoderResult getDecoderResult() {
        return decoderResult();
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public int refCnt() {
        return content.refCnt();
    }

    @Override
    public AggregatedLastHttpContent retain() {
        content.retain();
        return this;
    }

    @Override
    public AggregatedLastHttpContent retain(int increment) {
        content.retain(increment);
        return this;
    }

    @Override
    public AggregatedLastHttpContent touch() {
        content.touch();
        return this;
    }

    @Override
    public AggregatedLastHttpContent touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content.release();
    }

    @Override
    public boolean release(int decrement) {
        return content.release(decrement);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(StringUtil.simpleClassName(this) +
                "(data: " + content() + ", decoderResult: " + decoderResult() + ')');
        buf.append(StringUtil.NEWLINE);
        appendHeaders(buf);

        // Remove the last newline.
        buf.setLength(buf.length() - StringUtil.NEWLINE.length());
        return buf.toString();
    }

    private void appendHeaders(StringBuilder buf) {
        for (Map.Entry<String, String> e : trailingHeaders()) {
            buf.append(e.getKey());
            buf.append(": ");
            buf.append(e.getValue());
            buf.append(StringUtil.NEWLINE);
        }
    }
}
