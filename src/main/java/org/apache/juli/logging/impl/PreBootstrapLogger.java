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
package org.apache.juli.logging.impl;

import static org.apache.juli.logging.impl.PreBootstrapLoggingEvent.add;
import static org.apache.juli.logging.impl.SeparateLogbackSupport.obtainLogger;
import static org.slf4j.helpers.Util.report;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.spi.LocationAwareLogger;

/**
 * An underlying logger implementation that collaborates with
 * {@link SLF4JDelegatingLog} to temporarily store
 * {@linkplain PreBootstrapLoggingEvent logging events} before the actual
 * logging system is loaded.
 * <p>
 * At instance level, we only implement a {@link LocationAwareLogger}. The rest
 * of the {@link Logger} interface is not implemented. This is a very limited
 * implementation that is tailored for the only use of
 * {@link SLF4JDelegatingLog}.
 * <p>
 * We only implement {@link LocationAwareLogger} because before the actual
 * logging system is bootstrapped, we cannot tell if actual loggers will be
 * location aware loggers or not, so we assume they will be. The same goes for
 * loggers activation. We make no assumption on activated log levels, so this
 * implementation acts as if all log levels where enabled.
 * <p>
 * At class level, all created instances are registered in a collection, so that
 * they can be swapped in their respective {@link SLF4JDelegatingLog} by an
 * actual {@linkplain Logger SLF4J logger}. When the Logback bootstrapping has
 * started, a call to {@link #swapLoggers()} runs this swapping process.
 * <p>
 * This class <strong>does <em>not</em> implement thread safety</strong>. It's
 * the responsibility of client code to properly synchronize calls to these
 * three methods, using a single lock:
 * <ul>
 * <li>{@link #PreBootstrapLogger(SLF4JDelegatingLog, String)} (constructor)</li>
 * <li>{@link #log(Marker, String, int, String, Object[], Throwable)} (instance
 * method)
 * <li>{@link #swapLoggers()} (class method)</li>
 * </ul>
 * Typically, facade loggers {@link SLF4JDelegatingLog} do (globally)
 * synchronize access to the three methods above.
 * <p>
 * Diagnostics can be activated by lowering the
 * {@link SLF4JDelegatingLog#diagnostics} level.
 *
 * @since 1.1.0
 * @author Benjamin Gandon
 * @see PreBootstrapLoggingEvent
 * @see SLF4JDelegatingLog
 */
public class PreBootstrapLogger extends MarkerIgnoringBase implements LocationAwareLogger, Logger {

    private static final long serialVersionUID = -3141897866237016968L;

    private static final String UNIMPLEMENTED_ERR = "This method is not implemented and should not be used."
            + " Only the log() method should be used.";

    private static Collection<PreBootstrapLogger> registry = new LinkedList<PreBootstrapLogger>();

    /**
     * Replaces pre-bootstrap loggers by actual SLF4J loggers in their
     * respective {@link SLF4JDelegatingLog} facades.
     */
    static void swapLoggers() {
        if (SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
            report("PreBootstrapLogger.swapLoggers()");
        }
        for (Iterator<PreBootstrapLogger> itr = registry.iterator(); itr.hasNext();) {
            PreBootstrapLogger logger = itr.next();
            logger.facade.setLogger(obtainLogger(logger.name));
            itr.remove();
        }
    }

    /** The facade for this underlying logger. */
    private SLF4JDelegatingLog facade;

    /**
     * Creates a new pre-bootstrap logger.
     *
     * @param facade
     *            the facade for the underlying logger to create
     * @param name
     *            the logger name
     */
    public PreBootstrapLogger(final SLF4JDelegatingLog facade, final String name) {
        super();
        this.name = name;
        this.facade = facade;
        registry.add(this);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementations just stores a pre-bootstrap logging event.
     */
    @Override
    public void log(final Marker marker, final String fqcn, final int level, final String msg, final Object[] args,
            final Throwable t) {
        add(name, fqcn, level, msg, t);
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public void trace(final String msg) {
        illegal();
    }

    @Override
    public void trace(final String format, final Object arg) {
        illegal();
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        illegal();
    }

    @Override
    public void trace(final String format, final Object... args) {
        illegal();
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        illegal();
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(final String msg) {
        illegal();
    }

    @Override
    public void debug(final String format, final Object arg) {
        illegal();
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        illegal();
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        illegal();
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        illegal();
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(final String msg) {
        illegal();
    }

    @Override
    public void info(final String format, final Object arg) {
        illegal();
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        illegal();
    }

    @Override
    public void info(final String format, final Object... arguments) {
        illegal();
    }

    @Override
    public void info(final String msg, final Throwable t) {
        illegal();
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(final String msg) {
        illegal();
    }

    @Override
    public void warn(final String format, final Object arg) {
        illegal();
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        illegal();
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        illegal();
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        illegal();
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(final String msg) {
        illegal();
    }

    @Override
    public void error(final String format, final Object arg) {
        illegal();
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        illegal();
    }

    @Override
    public void error(final String format, final Object... arguments) {
        illegal();
    }

    @Override
    public void error(final String msg, final Throwable t) {
        illegal();
    }

    private void illegal() {
        throw new UnsupportedOperationException(UNIMPLEMENTED_ERR);
    }
}
