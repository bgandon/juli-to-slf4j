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

import static java.lang.System.currentTimeMillis;
import static org.apache.juli.logging.impl.SeparateLogbackSupport.obtainLogger;
import static org.slf4j.helpers.Util.report;
import static org.slf4j.spi.LocationAwareLogger.DEBUG_INT;
import static org.slf4j.spi.LocationAwareLogger.ERROR_INT;
import static org.slf4j.spi.LocationAwareLogger.INFO_INT;
import static org.slf4j.spi.LocationAwareLogger.TRACE_INT;
import static org.slf4j.spi.LocationAwareLogger.WARN_INT;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.slf4j.Logger;
import org.slf4j.spi.LocationAwareLogger;

/**
 * Helper class for {@link PreBootstrapLogger} that stores timestamped logging
 * events, and later flushes them to Logback appenders.
 * <p>
 * At instance level, we implement here an immutable holder for a logging event
 * that might occur at pre-bootstrap time, i.e. before the actual logging system
 * is initialized.
 * <p>
 * At class level, all created instances are stored in a FIFO queue, so that
 * they can be flushed to Logback appenders once the logging system is
 * bootstrapped.
 * <p>
 * This class <strong>does <em>not</em> implement thread safety</strong>. It's
 * the responsibility of client code to properly synchronize calls to these two
 * methods, using a single lock:
 * <ul>
 * <li>{@link #add(String, String, int, String, Throwable)}</li>
 * <li>{@link #flushEvents(ClassLoader)}</li>
 * </ul>
 * Typically, facade loggers {@link SLF4JDelegatingLog} do (globally)
 * synchronize access to {@link PreBootstrapLogger}, which implies synchronized
 * access to this class. See the documentation of these classes for more
 * details.
 * <p>
 * Diagnostics can be activated by lowering the
 * {@link SLF4JDelegatingLog#diagnostics} level.
 *
 * @since 1.1.0
 * @author Benjamin Gandon
 * @see PreBootstrapLogger
 * @see SLF4JDelegatingLog
 */
public class PreBootstrapLoggingEvent {

    private static final String LOGBACK_CLASSIC_LOGGER_CLASS = "ch.qos.logback.classic.Logger";
    private static final String LOGBACK_CLASSIC_ILOGGING_EVENT_CLASS = "ch.qos.logback.classic.spi.ILoggingEvent";
    private static final String LOGBACK_CLASSIC_LOGGING_EVENT_CLASS = "ch.qos.logback.classic.spi.LoggingEvent";
    private static final String LOGBACK_CLASSIC_LEVEL_CLASS = "ch.qos.logback.classic.Level";
    private static final String LOGBACK_LEVEL_ERROR_FIELD = "ERROR";
    private static final String LOGBACK_LEVEL_WARN_FIELD = "WARN";
    private static final String LOGBACK_LEVEL_INFO_FIELD = "INFO";
    private static final String LOGBACK_LEVEL_DEBUG_FIELD = "DEBUG";
    private static final String LOGBACK_LEVEL_TRACE_FIELD = "TRACE";

    private static List<PreBootstrapLoggingEvent> events = new LinkedList<PreBootstrapLoggingEvent>();

    /**
     * Stores a new pre-bootstrap logging event.
     * <p>
     * This method has a package-restricted visibility in order to prevent any
     * unsynchronized access.
     *
     * @param logName
     *            the logger name
     * @param fqcn
     *            the fully qualified class name of the original logger
     * @param level
     *            the detail level for this logging event
     * @param msg
     *            the message to log
     * @param thrown
     *            any throwable to log along with the message
     */
    static void add(final String logName, final String fqcn, final int level, final String msg, final Throwable thrown) {
        PreBootstrapLoggingEvent evt = new PreBootstrapLoggingEvent(logName, fqcn, level, msg, thrown);
        events.add(evt);
        if (SLF4JDelegatingLog.diagnostics <= TRACE_INT) {
            report(evt.toString());
        }
    }

    /**
     * Flushes all pre-bootstrap logging events to Logback appenders.
     * <p>
     * Logback is not supposed to be accessible with the class loader that has
     * loaded this {@link PreBootstrapLoggingEvent} class. Typically, this class
     * has been loaded by the System class loader, whereas Logback is just about
     * to be loaded by the Catalina class loader.
     * <p>
     * That's why this implementation uses introspection to create Logback
     * logging events and send them to Logback appenders.
     *
     * @param logbackLoader
     *            the class loader to use in order to access Logback classes.
     *            This is typically the Catalina class loader, which defaults to
     *            the common class loader in Tomcat.
     */
    static void flushEvents(final ClassLoader logbackLoader) {
        if (SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
            report("PreBootstrapLoggingEvent.flushEvents()");
        }
        Class<?> loggerClass;
        Class<?> iLoggingEventClass;
        Class<?> loggingEventClass;
        Class<?> levelClass;
        try {
            loggerClass = logbackLoader.loadClass(LOGBACK_CLASSIC_LOGGER_CLASS);
            iLoggingEventClass = logbackLoader.loadClass(LOGBACK_CLASSIC_ILOGGING_EVENT_CLASS);
            loggingEventClass = logbackLoader.loadClass(LOGBACK_CLASSIC_LOGGING_EVENT_CLASS);
            levelClass = logbackLoader.loadClass(LOGBACK_CLASSIC_LEVEL_CLASS);
        } catch (ClassNotFoundException exc) {
            report("ERROR: The class '" + LOGBACK_CLASSIC_LOGGING_EVENT_CLASS
                    + "' must be loadable by the given class loader: " + logbackLoader, exc);
            return;
        }

        Constructor<?> loggingEventConstructor;
        Method callAppendersMethod;
        Method setTimeStampMethod;
        try {
            loggingEventConstructor = loggingEventClass.getConstructor(String.class, loggerClass, levelClass,
                    String.class, Throwable.class, Object[].class);
            callAppendersMethod = loggerClass.getMethod("callAppenders", iLoggingEventClass);
            setTimeStampMethod = loggingEventClass.getMethod("setTimeStamp", long.class);
        } catch (NoSuchMethodException | SecurityException exc) {
            report("ERROR: unexpected issue while preparing flush of pre-bootstrap logs to Logback", exc);
            return;
        }

        if (SLF4JDelegatingLog.diagnostics <= TRACE_INT) {
            report("PreBootstrapLoggingEvent.flushEvents() flushing " + events.size() + " pre-bootstrap logging events");
        }
        for (ListIterator<PreBootstrapLoggingEvent> itr = events.listIterator(); itr.hasNext();) {
            PreBootstrapLoggingEvent evt = itr.next();
            Logger logger = obtainLogger(evt.logName);
            if (!LOGBACK_CLASSIC_LOGGER_CLASS.equals(logger.getClass().getName())) {
                int count = 0;
                for (ListIterator<PreBootstrapLoggingEvent> itr2 = events.listIterator(itr.previousIndex()); itr2
                        .hasNext();) {
                    if (evt.logName.equals(itr2.next().logName)) {
                        ++count;
                    }
                }
                report("ERROR: unexpected logger class [" + logger.getClass().getName() + "]. Discarding " + count
                        + " pre-bootstrap logging events made to the [" + evt.logName + "] logger.");
            } else {
                try {
                    Object loggingEvent = newLoggingEvent(evt, logger, loggingEventConstructor, setTimeStampMethod,
                            levelClass);
                    callAppendersMethod.invoke(logger, loggingEvent);
                    itr.remove();
                } catch (NoSuchFieldException | InstantiationException | IllegalAccessException
                        | IllegalArgumentException | InvocationTargetException | SecurityException exc) {
                    report("ERROR: unexpected issue while flushing pre-bootstrap log events to Logback appenders", exc);
                }
            }
        }
    }

    /**
     * Builds a new {@code ch.qos.logback.classic.spi.LoggingEvent} using
     * introstection.
     */
    private static Object newLoggingEvent(final PreBootstrapLoggingEvent evt, final Object logger,
            final Constructor<?> loggingEventConstructor, final Method setTimeStampMethod, final Class<?> levelClass)
                    throws NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Object level = toLogbackLevel(evt.level, levelClass);

        if (SLF4JDelegatingLog.diagnostics <= TRACE_INT) {
            Class<?>[] paramsTypes = loggingEventConstructor.getParameterTypes();
            for (int idx = 0; idx < 0 * paramsTypes.length; ++idx) {
                Class<?> type = paramsTypes[idx];
                report("argument #" + idx + " type: " + type.getName() + ", loader: " + type.getClassLoader());
            }
        }
        Object[] args = { evt.fqcn, logger, level, evt.msg, evt.thrown, null };
        if (SLF4JDelegatingLog.diagnostics <= TRACE_INT) {
            for (int idx = 0; idx < 0 * args.length; idx++) {
                Class<?> type = args[idx] == null ? Void.class : args[idx].getClass();
                report("argument #" + idx + " type: " + type.getName() + ", loader: " + type.getClassLoader());
            }
        }
        Object loggingEvent = loggingEventConstructor.newInstance(args);

        setTimeStampMethod.invoke(loggingEvent, evt.timeStamp);
        return loggingEvent;
    }

    /**
     * Converts a log level from {@link LocationAwareLogger} into an instance of
     * {@code ch.qos.logback.classic.Level}, using introspection.
     */
    private static Object toLogbackLevel(final int level, final Class<?> levelClass) throws NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException {
        Field levelField = null;
        switch (level) {
        case TRACE_INT:
            levelField = levelClass.getDeclaredField(LOGBACK_LEVEL_TRACE_FIELD);
            break;
        case DEBUG_INT:
            levelField = levelClass.getDeclaredField(LOGBACK_LEVEL_DEBUG_FIELD);
            break;
        case INFO_INT:
            levelField = levelClass.getDeclaredField(LOGBACK_LEVEL_INFO_FIELD);
            break;
        case WARN_INT:
            levelField = levelClass.getDeclaredField(LOGBACK_LEVEL_WARN_FIELD);
            break;
        case ERROR_INT:
            levelField = levelClass.getDeclaredField(LOGBACK_LEVEL_ERROR_FIELD);
            break;
        }
        return levelField.get(null);
    }

    private String logName;
    private String fqcn;
    private int level;
    private String msg;
    private Throwable thrown;
    private long timeStamp;

    /**
     * Private constructor because the interface for creating pre-bootstrap log
     * events is the {@link #add} class method.
     */
    private PreBootstrapLoggingEvent(final String logName, final String fqcn, final int level, final String msg,
            final Throwable thrown) {
        super();
        this.logName = logName;
        this.fqcn = fqcn;
        this.level = level;
        this.msg = msg;
        this.thrown = thrown;
        timeStamp = currentTimeMillis();
    }

    /**
     * The implementation here builds a very basic representation of this
     * logging event for the sake of low level diagnostics only.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(timeStamp).append(" [").append(level).append("] ").append(msg);
        if (thrown != null) {
            builder.append('\n');
            StringWriter writer = new StringWriter();
            thrown.printStackTrace(new PrintWriter(writer));
            builder.append(writer);
        }
        return builder.toString();
    }
}
