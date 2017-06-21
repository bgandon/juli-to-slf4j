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

import static java.lang.Integer.getInteger;
import static java.lang.String.valueOf;
import static org.apache.juli.logging.impl.SeparateLogbackSupport.bootstrapLoggingSystemIfPossible;
import static org.apache.juli.logging.impl.SeparateLogbackSupport.bootstrapped;
import static org.apache.juli.logging.impl.SeparateLogbackSupport.obtainLogger;
import static org.apache.juli.logging.impl.SeparateLogbackSupport.obtainPreBootstrapLoggerIfNecessary;
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
 * A {@link Log} facade that can be plugged into the default Tomcat JULI logging
 * system, and supports the SLF4J API to be on Tomcat system classpath and the
 * SLF4J implementation to be on Tomcat common classpath.
 * <p>
 * The only supported SLF4J (backend) implementation is <a
 * href="http://logback.qos.ch/">Logback</a>.
 * <p>
 * This facade delegates to {@link SeparateLogbackSupport} the task of
 * automatically bootstrapping Logback at the right time. For this bootstrap to
 * run properly in multi-thraded environments, a global lock on this
 * {@link SLF4JDelegatingLog} class is used.
 * <p>
 * The logging behaves differently before and after Logback bootstrap. (Here we
 * refer to the “bootstrap” as being the Logback initialization, which happens
 * <em>during</em> Catalina's startup, but is separate in concept.)
 * <ol>
 * <li><strong>“Pre-bootstrap” time:</strong><br>
 * Instances delegate to {@link a PreBootstrapLogger}s that store all
 * pre-boostrap logging events in memory.</li>
 * <li><strong>“Post-bootstrap” time:</strong><br>
 * Instances delegate to underlying wrapped {@link Logger}s, as returned by the
 * SLF4J API.</li>
 * </ol>
 * <p>
 * The pluggability of this class relies on an undocumented feature of
 * {@link org.apache.juli.logging.LogFactory LogFactory} in the default JULI,
 * which discovers {@link Log} implementation
 * {@linkplain java.util.ServiceLoader providers} that expose a String-based
 * constructor.
 * <p>
 * At pre-bootstrap time, all log events are stored in memory, because we assume
 * that this stage will live a short time (typically less than 200ms) and log
 * few messages (typically 64 messages).
 * <p>
 * Since version 1.0.1, this implementation properly supports the case were the
 * underlying logger is a {@link LocationAwareLogger}.
 * <p>
 * Diagnostics can be activated by lowering their detail level with the
 * {@code org.apache.juli.logging.impl.SLF4JDelegatingLog.diagnostics} system
 * property. Reference values are those defined by the
 * {@link LocationAwareLogger} interface. The default is {@link #WARN_INT},
 * which only reports problems. Actual reporting is
 * {@linkplain org.slf4j.helpers.Util#report delegatedJ} to SLF4, which outputs
 * messages to the {@linkplain System#err standard error stream}.
 *
 * @since 1.0.0
 * @author Benjamin Gandon
 * @see SeparateLogbackSupport
 */
public class SLF4JDelegatingLog implements Log, Serializable {

    private static final long serialVersionUID = 4326548378678492807L;

    private static final String FQCN = SLF4JDelegatingLog.class.getName();

    private static final String DIAGNOSTICS_LEVEL_PROPERTY = "org.apache.juli.logging.impl.SLF4JDelegatingLog.diagnostics";
    public static volatile int diagnostics = getInteger(DIAGNOSTICS_LEVEL_PROPERTY, WARN_INT);

    /**
     * The logger's name.
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
    private transient volatile Logger log;

    /**
     * This flag becomes {@code true} when the underlying implementation is not
     * a {@link LocationAwareLogger}, which triggers some optimizations.
     * <p>
     * We use the {@code true} value for the most common case that we want to
     * optimize. Location aware loggers generally have a larger computational
     * cost.
     */
    private boolean noLocationAware;

    /**
     * The default constructor is mandatory, as per the
     * {@link java.util.ServiceLoader ServiceLoader} specification.
     * <p>
     * This will construct an unusable {@link Log}, though.
     */
    public SLF4JDelegatingLog() {
        super();
    }

    /**
     * This {@link String}-based constructor is required by the default "lean"
     * JULI {@link org.apache.juli.logging.LogFactory LogFactory}, in order to
     * accept this class a a {@link Log} implementation
     * {@linkplain java.util.ServiceLoader provider}.
     *
     * @param name
     *            the name of the logger to create.
     * @see org.apache.juli.logging.LogFactory#LogFactory
     */
    public SLF4JDelegatingLog(final String name) {
        super();
        if (bootstrapped) {
            setLogger(obtainLogger(name));
        } else {
            setLogger(obtainPreBootstrapLoggerIfNecessary(this, name));
        }
    }

    /**
     * Package-private setter, so that the swapping process can be done by the
     * PreBootstrapLogger collaborator.
     *
     * @param logger
     *            the underlying logger that this log facade will delegate
     *            logging to.
     */
    void setLogger(final Logger logger) {
        log = logger;
        noLocationAware = !(logger instanceof LocationAwareLogger) && !(logger instanceof PreBootstrapLogger);
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
        trace(msg, null);
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
        if (bootstrapped) {
            doTrace(msg, thrown);
        } else {
            bootstrapLoggingSystemIfPossible();
            synchronized (SLF4JDelegatingLog.class) {
                doTrace(msg, thrown);
            }
        }
    }

    private void doTrace(final Object msg, final Throwable thrown) {
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
        debug(msg, null);
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
        if (bootstrapped) {
            doDebug(msg, thrown);
        } else {
            bootstrapLoggingSystemIfPossible();
            synchronized (SLF4JDelegatingLog.class) {
                doDebug(msg, thrown);
            }
        }
    }

    private void doDebug(final Object msg, final Throwable thrown) {
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
        info(msg, null);
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
        if (bootstrapped) {
            doInfo(msg, thrown);
        } else {
            bootstrapLoggingSystemIfPossible();
            synchronized (SLF4JDelegatingLog.class) {
                doInfo(msg, thrown);
            }
        }
    }

    private void doInfo(final Object msg, final Throwable thrown) {
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
        warn(msg, null);
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
        if (bootstrapped) {
            doWarn(msg, thrown);
        } else {
            bootstrapLoggingSystemIfPossible();
            synchronized (SLF4JDelegatingLog.class) {
                doWarn(msg, thrown);
            }
        }
    }

    private void doWarn(final Object msg, final Throwable thrown) {
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
        error(msg, null);
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
        if (bootstrapped) {
            doError(msg, thrown);
        } else {
            bootstrapLoggingSystemIfPossible();
            synchronized (SLF4JDelegatingLog.class) {
                doError(msg, thrown);
            }
        }
    }

    private void doError(final Object msg, final Throwable thrown) {
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
        error(msg, null);
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
