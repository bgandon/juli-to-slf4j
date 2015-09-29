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
package org.apache.juli.logging.impl;

import static java.lang.String.valueOf;
import static org.slf4j.LoggerFactory.getLogger;
import static org.slf4j.spi.LocationAwareLogger.DEBUG_INT;
import static org.slf4j.spi.LocationAwareLogger.ERROR_INT;
import static org.slf4j.spi.LocationAwareLogger.INFO_INT;
import static org.slf4j.spi.LocationAwareLogger.TRACE_INT;
import static org.slf4j.spi.LocationAwareLogger.WARN_INT;

import java.io.ObjectStreamException;
import java.io.Serializable;

import org.apache.juli.logging.Log;
import org.slf4j.Logger;
import org.slf4j.spi.LocationAwareLogger;

/**
 * A {@link Log} facade that delegates to an underlying wrapped {@link Logger}
 * implementation, as returned by
 * {@link org.slf4j.LoggerFactory#getLogger(String) LoggerFactory.getLogger}.
 * <p>
 * This class exposes a constructor that accepts a String as argument, so that
 * the default "lean" JULI {@link org.apache.juli.logging.LogFactory LogFactory}
 * can discover it as a proper {@link Log} implementation
 * {@linkplain java.util.ServiceLoader provider}.
 * <p>
 * This implementation also properly supports the case were the underlying
 * logger is a {@link LocationAwareLogger}.
 *
 * @author Benjamin Gandon
 */
public class SLF4JDelegatingLog implements Log, Serializable {

    private static final long serialVersionUID = 5406218772899675141L;

    private static final String FQCN = SLF4JDelegatingLog.class.getName();

    /**
     * The logger's name.
     * <p>
     * This is used to recreate {@link SLF4JDelegatingLog} objects after they
     * have been deserialized, so that they properly belong to the context of
     * the applicable {@link java.util.logging.LogManager LogManager}.
     */
    protected String name;

    /**
     * The delegate slf4j logger.
     * <p>
     * NOTE: in both {@code Log4jLogger} and {@code Jdk14Logger} classes in the
     * original JCL, as well as in the
     * {@code org.apache.commons.logging.impl.SLF4JLog} of
     * {@code jcl-over-slf4j}, the logger instance is <em>transient</em>, so we
     * do the same here.
     */
    private transient Logger log;

    /**
     * This flag is {@code false} when the underlying implementation is a
     * {@link LocationAwareLogger}.
     * <p>
     * We use a final boolean here to give a chance to JITs to properly optimize
     * methods bodies.
     * <p>
     * We use the {@code true} value for the most common case that we want to
     * optimize. Location aware loggers generally have a larger computational
     * cost.
     */
    private final boolean noLocationAware;

    /**
     * The default constructor is mandatory, as per the
     * {@link java.util.ServiceLoader ServiceLoader} specification.
     * <p>
     * This will construct an unusable {@link Log}, though.
     */
    public SLF4JDelegatingLog() {
        super();
        noLocationAware = true;
    }

    /**
     * This {@link String}-based constructor is required by the default "lean"
     * JULI {@link org.apache.juli.logging.LogFactory LogFactory}, in order to
     * recognize this class as an appropriate {@link Log} implementation.
     *
     * @param name
     *            the name of the logger to create.
     */
    public SLF4JDelegatingLog(final String name) {
        super();
        log = getLogger(name);
        noLocationAware = !(log instanceof LocationAwareLogger);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here delegates to the wrapped {@link Logger} instance.
     *
     * @return {@code true} if the wrapped logger {@link Logger#isTraceEnabled
     *         isTraceEnabled}, or {@code false} otherwise.
     */
    @Override
    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here delegates to the wrapped {@link Logger} instance.
     *
     * @return {@code true} if the wrapped logger {@link Logger#isDebugEnabled
     *         isDebugEnabled}, or {@code false} otherwise.
     */
    @Override
    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here delegates to the wrapped {@link Logger} instance.
     *
     * @return {@code true} if the wrapped logger {@link Logger#isInfoEnabled
     *         isInfoEnabled}, or {@code false} otherwise.
     */
    @Override
    public boolean isInfoEnabled() {
        return log.isInfoEnabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here delegates to the wrapped {@link Logger} instance.
     *
     * @return {@code true} if the wrapped logger {@link Logger#isWarnEnabled
     *         isWarnEnabled}, or {@code false} otherwise.
     */
    @Override
    public boolean isWarnEnabled() {
        return log.isWarnEnabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here delegates to the wrapped {@link Logger} instance.
     *
     * @return {@code true} if the wrapped logger {@link Logger#isErrorEnabled
     *         isErrorEnabled}, or {@code false} otherwise.
     */
    @Override
    public boolean isErrorEnabled() {
        return log.isErrorEnabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here delegates to the {@link Logger#isErrorEnabled
     * isErrorEnabled} method of the wrapped {@link Logger} instance, because
     * SLF4J has no {@code FATAL} level.
     *
     * @return {@code true} if the wrapped logger {@link Logger#isErrorEnabled
     *         isErrorEnabled}, or {@code false} otherwise.
     */
    @Override
    public boolean isFatalEnabled() {
        return log.isErrorEnabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it as {@code TRACE} level using the wrapped
     * {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     */
    @Override
    public void trace(final Object msg) {
        if (noLocationAware) {
            log.trace(valueOf(msg));
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, TRACE_INT, valueOf(msg), null, null);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it along with the given throwable as {@code TRACE}
     * level, using the wrapped {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     * @param thrown
     *            the throwable to log
     */
    @Override
    public void trace(final Object msg, final Throwable thrown) {
        if (noLocationAware) {
            log.trace(valueOf(msg), thrown);
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, TRACE_INT, valueOf(msg), null, thrown);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it as {@code DEBUG} level using the wrapped
     * {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     */
    @Override
    public void debug(final Object msg) {
        if (noLocationAware) {
            log.debug(valueOf(msg));
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, DEBUG_INT, valueOf(msg), null, null);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it along with the given throwable as {@code DEBUG}
     * level, using the wrapped {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     * @param thrown
     *            the throwable to log
     */
    @Override
    public void debug(final Object msg, final Throwable thrown) {
        if (noLocationAware) {
            log.debug(valueOf(msg), thrown);
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, DEBUG_INT, valueOf(msg), null, thrown);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it as {@code INFO} level using the wrapped
     * {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     */
    @Override
    public void info(final Object msg) {
        if (noLocationAware) {
            log.info(valueOf(msg));
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, INFO_INT, valueOf(msg), null, null);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it along with the given throwable as {@code INFO}
     * level, using the wrapped {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     * @param thrown
     *            the throwable to log
     */
    @Override
    public void info(final Object msg, final Throwable thrown) {
        if (noLocationAware) {
            log.info(valueOf(msg), thrown);
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, INFO_INT, valueOf(msg), null, thrown);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it as {@code WARN} level using the wrapped
     * {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     */
    @Override
    public void warn(final Object msg) {
        if (noLocationAware) {
            log.warn(valueOf(msg));
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, WARN_INT, valueOf(msg), null, null);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it along with the given throwable as {@code WARN}
     * level, using the wrapped {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     * @param thrown
     *            the throwable to log
     */
    @Override
    public void warn(final Object msg, final Throwable thrown) {
        if (noLocationAware) {
            log.warn(String.valueOf(msg), thrown);
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, WARN_INT, valueOf(msg), null, thrown);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it as {@code ERROR} level using the wrapped
     * {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     */
    @Override
    public void error(final Object msg) {
        if (noLocationAware) {
            log.error(valueOf(msg));
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, ERROR_INT, valueOf(msg), null, null);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it along with the given throwable as {@code ERROR}
     * level, using the wrapped {@link Logger} instance.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     * @param thrown
     *            the throwable to log
     */
    @Override
    public void error(final Object msg, final Throwable thrown) {
        if (noLocationAware) {
            log.error(valueOf(msg), thrown);
        } else {
            ((LocationAwareLogger) log).log(null, FQCN, ERROR_INT, valueOf(msg), null, thrown);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it as {@code ERROR} level using the wrapped
     * {@link Logger} instance, SLF4J has no {@code FATAL} level.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     */
    @Override
    public void fatal(final Object msg) {
        error(msg);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation here converts the message to a {@linkplain String
     * string}, and logs it along with the given throwable as {@code ERROR}
     * level, using the wrapped {@link Logger} instance, because SLF4J has no
     * {@code FATAL} level.
     *
     * @param msg
     *            the message to log, to be converted to {@link String}
     * @param thrown
     *            the throwable to log
     */
    @Override
    public void fatal(final Object msg, final Throwable thrown) {
        error(msg, thrown);
    }

    /**
     * Replace the deserialized instance with a fresh new
     * {@link SLF4JDelegatingLog} logger of the same name, so that it properly
     * belong to the context of the applicable
     * {@link java.util.logging.LogManager LogManager}.
     *
     * @return a homonymous logger that properly belongs to the context of the
     *         applicable log manager.
     * @throws ObjectStreamException
     *             actually never thrown by this implementation.
     */
    protected Object readResolve() throws ObjectStreamException {
        return new SLF4JDelegatingLog(name);
    }
}
