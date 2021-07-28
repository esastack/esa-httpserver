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
package io.esastack.httpserver.core;

import esa.commons.Checks;
import esa.commons.annotation.Beta;
import io.esastack.commons.net.http.Cookie;
import io.esastack.commons.net.http.HttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.Future;

import java.io.File;

/**
 * An interface defines a http server response.
 * <p>
 * !Note: It would be uncompleted state which means current {@link Response} hasn't been completely written(eg. only
 * part of the body was written, and waiting to write the left. and it could be indicated by the return values of {@link
 * #isCommitted()} and {@link #isEnded()}.
 * <p>
 * Also, you should know that this response is not designed as thread-safe, but it does a special committing control:
 * <p>
 * <strong>Every call of writes should be kept in order.</strong>
 * </p>
 * which means it is not allowed to call {@link #write(byte[])} or {@link #end(byte[])} concurrently.
 * Simply put, you must keep your writes in order, whatever these writes are in one thread or different threads.
 * <p>
 * Typically, write response in one thread
 * <pre>{@code
 * response.write("foo".getBytes());
 * response.write("bar".getBytes());
 * res.end("baz".getBytes());
 * }</pre>
 * <p>
 * Write response asynchronously
 * <pre>{@code
 * response.write("foo".getBytes()).addListener(f -> {
 *     response.write("bar".getBytes()).addListener(f1 -> {
 *         response.end("baz".getBytes());
 *     });
 * });
 * }</pre>
 */
public interface Response {

    /**
     * Get current http response code.
     *
     * @return status
     */
    int status();

    /**
     * Set the response code
     *
     * @param code code
     * @return this
     */
    Response setStatus(int code);

    /**
     * Returns the headers of this response.
     *
     * @return headers
     */
    HttpHeaders headers();

    /**
     * Returns the trailing headers of this message.
     *
     * @return trailers
     */
    HttpHeaders trailers();

    /**
     * Adds the specified cookie to the response. This method can be called multiple times to set more than one cookie.
     *
     * @param cookie the Cookie to return to the client
     * @return this
     */
    Response addCookie(Cookie cookie);

    /**
     * Adds the specified cookie to the response. This method can be called multiple times to set more than one cookie.
     *
     * @param name  cookie name
     * @param value value
     * @return this
     */
    Response addCookie(String name, String value);

    /**
     * Whether response will be written as keepalive on current connection or stream(depends on the version of http
     * protocol)
     *
     * @return isKeepAlive
     */
    boolean isKeepAlive();

    /**
     * @see #write(byte[], int, int)
     */
    default Future<Void> write(byte[] data) {
        return write(data, 0);
    }

    /**
     * @see #write(byte[], int, int)
     */
    default Future<Void> write(byte[] data, int offset) {
        return write(data, offset, data == null ? 0 : data.length - offset);
    }

    /**
     * Writes some data to the remote.
     * <p>
     * You may need to check the result of {@link #isWritable()} before writing.
     *
     * @param data   data to write
     * @param offset offset
     * @param length length
     * @return future
     */
    Future<Void> write(byte[] data, int offset, int length);

    /**
     * Writes some data to the remote.
     * <p>
     * You may need to check the result of {@link #isWritable()} before writing.
     * <p>
     * !Note: It is recommended to use this method in current channel event loop, because of the complexity of the
     * {@link ByteBuf} allocation/deallocation
     *
     * @param data data to write
     *             3     * @return future
     */
    Future<Void> write(ByteBuf data);


    /**
     * @see #end(byte[], int, int)
     */
    default Future<Void> end(byte[] data) {
        return end(data, 0);
    }

    /**
     * @see #end(byte[], int, int)
     */
    default Future<Void> end(byte[] data, int offset) {
        return end(data, offset, data == null ? 0 : data.length - offset);
    }

    /**
     * Ends current response with writing given {@code data} to the remote.
     * <p>
     * You may need to check the result of {@link #isWritable()} before writing.
     *
     * @param data   data to write
     * @param offset offset
     * @param length length
     * @return future
     */
    Future<Void> end(byte[] data, int offset, int length);

    /**
     * Ends current response.
     * <p>
     * You may need to check the result of {@link #isWritable()} before writing.
     *
     * @return future
     */
    default Future<Void> end() {
        return end(Unpooled.EMPTY_BUFFER);
    }

    /**
     * Ends current response with writing given {@code data} to the remote.
     * <p>
     * You may need to check the result of {@link #isWritable()} before writing.
     * <p>
     * !Note: It is recommended to use this method in current channel event loop, because of the complexity of the
     * {@link ByteBuf} allocation/deallocation
     *
     * @param data data to write
     * @return future
     */
    Future<Void> end(ByteBuf data);

    /**
     * Sends a temporary redirect response to the client using the given redirect uri.
     * <p>
     * If the new url is relative without a leading '/' it will be regarded as a relative path to the current request
     * URI. otherwise it will be regarded as a relative path to root path.
     *
     * @param newUri target uri
     * @return future
     */
    default Future<Void> sendRedirect(String newUri) {
        Checks.checkNotEmptyArg(newUri, "newUri");
        headers().set(HttpHeaderNames.LOCATION, newUri);
        setStatus(HttpResponseStatus.FOUND.code());
        return end();
    }

    /**
     * @see #sendFile(File, long, long)
     */
    default Future<Void> sendFile(File file) {
        return sendFile(file, 0L);
    }


    /**
     * @see #sendFile(File, long, long)
     */
    default Future<Void> sendFile(File file, long offset) {
        return sendFile(file, offset, Long.MAX_VALUE);
    }

    /**
     * Sends a file to client.
     * <p>
     * Note that this is an asynchronous method but we do not provides any promise or callback for user.
     *
     * @param file   target file to send.
     * @param offset start index to send
     * @param length length.
     */
    Future<Void> sendFile(File file, long offset, long length);

    /**
     * Returns whether the extra response data is writable.
     *
     * @return {@code true} if extra response data is writable, otherwise {@code false}
     */
    boolean isWritable();

    /**
     * Is current response has been write.
     *
     * @return isCommitted
     */
    boolean isCommitted();

    /**
     * Is current response has been write.
     *
     * @return isCommitted
     */
    boolean isEnded();

    /**
     * Returns the {@link Future} which is notified when the response is about to ending.
     *
     * @return future
     */
    Future<Void> onEndFuture();

    /**
     * Returns the {@link Future} which is notified when the response is ended.
     *
     * @return future
     */
    Future<Void> endFuture();

    /**
     * Get current allocator.
     *
     * @return allocator
     */
    @Beta
    ByteBufAllocator alloc();

}
