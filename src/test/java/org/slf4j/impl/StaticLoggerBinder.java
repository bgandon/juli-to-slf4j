/*
 * Copyright 2015-2017 Benjamin Gandon
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
 *
 */
package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * An {@code org.slf4j.impl.StaticLoggerBinder} that stores and returns logger
 * factories per thread.
 * <p>
 * The name and package of this class cannot be changed because the
 * {@link org.slf4j.LoggerFactory} looks for this exact class name on its class
 * path.
 * <p>
 * This is especially designed to run in multi-threaded automated tests
 * environments.
 *
 * @author Benjamin Gandon
 */
public class StaticLoggerBinder implements LoggerFactoryBinder {

    /**
     * Declare the version of the SLF4J API this implementation is compiled
     * against.
     */
    // to avoid constant folding by the compiler, this field must *not* be final
    public static String REQUESTED_API_VERSION = "1.6"; // !final

    private static StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    private ThreadLocal<ILoggerFactory> loggerFactory = new ThreadLocal<>();

    /**
     * Set the logger factory to use for the current thread. This will typically
     * be a mock object.
     */
    public void setLoggerFactory(final ILoggerFactory loggerFactory) {
        this.loggerFactory.set(loggerFactory);
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory.get();
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return loggerFactory.get().getClass().getName();
    }

}
