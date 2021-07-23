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
package io.esastack.httpserver.utils;

import esa.commons.Checks;
import esa.commons.concurrent.InternalThreads;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An {@link ThreadFactory} that prefers to create {@link esa.commons.concurrent.InternalThread} and appends a log when
 * {@link #newThread(Runnable)} is called.
 */
public class LoggedThreadFactory implements ThreadFactory {

    private static final AtomicInteger POOL_ID = new AtomicInteger();

    private final AtomicInteger nextId = new AtomicInteger();
    private final String prefix;
    private final boolean daemon;

    public LoggedThreadFactory(String prefix) {
        this(prefix, false);
    }

    public LoggedThreadFactory(String prefix,
                               boolean daemon) {
        Checks.checkNotNull(prefix, "prefix");
        this.prefix = prefix + "-" + POOL_ID.getAndIncrement() + "#";
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = prefix + nextId.getAndIncrement();
        Thread t = InternalThreads.newThread(r, name).thread();
        try {
            if (t.isDaemon() != daemon) {
                t.setDaemon(daemon);
            }
        } catch (Exception ignored) {
            // Doesn't matter even if failed to set.
        }
        Loggers.logger().info("Create thread of ESA HttpServer '{}'", name);
        return t;
    }
}
