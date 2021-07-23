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

import esa.commons.http.Cookie;
import esa.commons.http.HttpHeaders;
import esa.commons.http.HttpMethod;
import esa.commons.http.HttpVersion;

import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

/**
 * Base interface of a http request.
 */
public interface BaseRequest {

    /**
     * HttpVersion, such as HTTP/1.1
     *
     * @return version
     */
    HttpVersion version();

    /**
     * HTTP or HTTPS
     *
     * @return scheme
     */
    String scheme();

    /**
     * Http aggregated url. For example '/foo/bar?baz=qux'.
     *
     * @return url
     */
    String uri();

    /**
     * Http path except parameters. For example path of uri '/foo/bar?baz=qux' is '/foo/bar'.
     *
     * @return uri
     */
    String path();

    /**
     * Returns the query part of the uri. For example query of uri '/foo/bar?baz=qux' is 'baz=qux'.
     *
     * @return query string
     */
    String query();

    /**
     * HTTP method
     *
     * @return method
     */
    HttpMethod method();

    /**
     * HTTP method as String type.
     *
     * @return method
     */
    default String rawMethod() {
        return method().name();
    }

    /**
     * Gets parameter, This pair of parameter can be from url parameters or body k-v values when Content-Type equals to
     * 'x-www-form-urlencoded'
     *
     * @param parName parameter name
     * @return value
     */
    default String getParam(String parName) {
        final List<String> params = getParams(parName);
        if (params != null && params.size() > 0) {
            return params.get(0);
        }
        return null;
    }

    /**
     * Get parameters This pair of parameter can be from url parameters or body k-v values when Content-Type equals to
     * 'x-www-form-urlencoded'
     *
     * @param parName parameter name
     * @return value
     */
    default List<String> getParams(String parName) {
        return paramMap().get(parName);
    }

    /**
     * Get parameter map This pair of parameters can be from url parameters or body k-v values when Content-Type equals
     * to 'x-www-form-urlencoded'
     *
     * @return map
     */
    Map<String, List<String>> paramMap();

    /**
     * Gets http headers
     *
     * @return headers
     */
    HttpHeaders headers();

    /**
     * Returns a map containing all of the {@link Cookie} objects the client sent with this request.
     *
     * @return all of the {@link Cookie} objects the client sent with this request, returns an empty map if no cookies
     * were sent.
     */
    Map<String, Cookie> cookies();

    /**
     * Gets the {@link Cookie} with given name.
     *
     * @param name cookie name
     * @return cookie or {@code null} if did not find.
     */
    default Cookie getCookie(String name) {
        return cookies().get(name);
    }

    /**
     * Returns the Internet Protocol address of the client or last proxy that sent the request.
     *
     * @return addr
     */
    SocketAddress remoteAddress();

    /**
     * Returns the last proxy that sent the request.
     *
     * @return addr
     */
    SocketAddress tcpSourceAddress();

    /**
     * Returns the Internet Protocol address of the interface on which the request was received.
     *
     * @return addr
     */
    SocketAddress localAddress();
}
