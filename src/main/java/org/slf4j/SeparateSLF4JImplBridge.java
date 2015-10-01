/*
 * Copyright 2015 Benjamin Gandon
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
package org.slf4j;

import static org.slf4j.helpers.Util.report;
import static org.slf4j.spi.LocationAwareLogger.DEBUG_INT;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class defines helper methods for our customized
 * {@link org.slf4j.LoggerFactory LoggerFactory} to bind to a separated SLF4J
 * implementation, i.e. an SLF4J backend Jar that is on the classpath of a child
 * class loader.
 * <p>
 * The SLF4J backend shall typically be Logback, but our implementation here is
 * valid for any other SLF4J backend.
 * <p>
 * This implementation uses introspection to access the separated
 * {@code org.slf4j.impl.StaticLoggerBinder} class.
 * <p>
 * Diagnostics can be activated by lowering the
 * {@link org.apache.juli.logging.impl.SLF4JDelegatingLog#diagnostics
 * SLF4JDelegatingLog.diagnostics} level.
 *
 * @since 1.1.0
 * @author Benjamin Gandon
 * @see org.slf4j.LoggerFactory
 * @see org.slf4j.spi.LoggerFactoryBinder
 */
public class SeparateSLF4JImplBridge {

    private static final String SLF4J_IMPL_STATIC_LOGGER_BINDER_CLASS = "org.slf4j.impl.StaticLoggerBinder";
    private static final String REQUESTED_API_VERSION_FIELD = "REQUESTED_API_VERSION";
    private static final String GET_SINGLETON_METHOD = "getSingleton";
    private static final String GET_LOGGER_FACTORY_METHOD = "getLoggerFactory";
    private static final String GET_LOGGER_FACTORY_CLASS_STR_METHOD = "getLoggerFactoryClassStr";

    private static Field requestedApiVersionField;
    private static Method getSingletonMethod;
    private static Method getLoggerFactoryMethod;
    private static Method getLoggerFactoryClassStrMethod;

    /**
     * Loads an {@code org.slf4j.impl.StaticLoggerBinder} class using the given
     * class loader.
     * <p>
     * This immediately triggers the activation of the SLF4J implementation.
     *
     * @param slf4jImplLoader
     *            the class loader to use in order to access the SLF4J
     *            implementation classes. This is typically the Catalina class
     *            loader, which defaults to the common class loader in Tomcat.
     */
    static void doBootstrap(final ClassLoader slf4jImplLoader) {
        if (org.apache.juli.logging.impl.SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
            report("SeparateSLF4JImplBridge.doBootstrap('" + slf4jImplLoader + "')");
        }

        Class<?> staticLoggerBinder;
        Field requestedApiVersion;
        Method getSingleton;
        Method getLoggerFactory;
        Method getLoggerFactoryClassStr;
        try {
            staticLoggerBinder = slf4jImplLoader.loadClass(SLF4J_IMPL_STATIC_LOGGER_BINDER_CLASS);
            requestedApiVersion = staticLoggerBinder.getDeclaredField(REQUESTED_API_VERSION_FIELD);
            getSingleton = staticLoggerBinder.getMethod(GET_SINGLETON_METHOD);
            getLoggerFactory = staticLoggerBinder.getMethod(GET_LOGGER_FACTORY_METHOD);
            getLoggerFactoryClassStr = staticLoggerBinder.getMethod(GET_LOGGER_FACTORY_CLASS_STR_METHOD);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | SecurityException exc) {
            throw new RuntimeException("unexpected error while bootstrapping the actual StaticLoggerBinder", exc);
        }
        requestedApiVersionField = requestedApiVersion;
        getSingletonMethod = getSingleton;
        getLoggerFactoryMethod = getLoggerFactory;
        getLoggerFactoryClassStrMethod = getLoggerFactoryClassStr;
    }

    /**
     * Accesses the {@code StaticLoggerBinder.REQUESTED_API_VERSION} static
     * field of the separated SLF4J implementation.
     *
     * @return the version of the SLF4J API that the implementation is compiled
     *         against.
     */
    static String requestedApiVersion() {
        try {
            return (String) requestedApiVersionField.get(null);
        } catch (IllegalArgumentException | IllegalAccessException exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Runs the {@code StaticLoggerBinder.getSingleton()} method of the
     * separated SLF4J implementation.
     *
     * @return the applicable {@code StaticLoggerBinder} singleton of the
     *         separated SLF4J implementation.
     */
    static Object getStaticLoggerBinder() {
        try {
            return getSingletonMethod.invoke(null);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Runs the {@code StaticLoggerBinder.getSingleton().getLoggerFactory()}
     * method of the separated SLF4J implementation.
     *
     * @return the instance of {@link ILoggerFactory} that
     *         {@link org.slf4j.LoggerFactory} class should bind to.
     * @see org.slf4j.spi.LoggerFactoryBinder#getLoggerFactory()
     */
    static ILoggerFactory getLoggerFactory() {
        try {
            return (ILoggerFactory) getLoggerFactoryMethod.invoke(getStaticLoggerBinder());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException exc) {
            throw new RuntimeException(exc);
        }
    }

    /**
     * Runs the
     * {@code StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr()}
     * method of the separated SLF4J implementation.
     *
     * @return the class name of the intended {@link ILoggerFactory} instance.
     * @see org.slf4j.spi.LoggerFactoryBinder#getLoggerFactoryClassStr()
     */
    static ILoggerFactory getLoggerFactoryClassStr() {
        try {
            return (ILoggerFactory) getLoggerFactoryClassStrMethod.invoke(getStaticLoggerBinder());
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException exc) {
            throw new RuntimeException(exc);
        }
    }
}
