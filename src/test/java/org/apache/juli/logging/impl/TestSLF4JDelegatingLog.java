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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.slf4j.impl.StaticLoggerBinder.getSingleton;
import static org.slf4j.spi.LocationAwareLogger.DEBUG_INT;
import static org.slf4j.spi.LocationAwareLogger.ERROR_INT;
import static org.slf4j.spi.LocationAwareLogger.INFO_INT;
import static org.slf4j.spi.LocationAwareLogger.TRACE_INT;
import static org.slf4j.spi.LocationAwareLogger.WARN_INT;

import org.apache.juli.logging.Log;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.impl.StaticLoggerBinder;
import org.slf4j.spi.LocationAwareLogger;

/**
 * Here we test use cases of {@link SLF4JDelegatingLog}.
 *
 * @author Benjamin Gandon
 */
public class TestSLF4JDelegatingLog {

    @Mock
    private Logger logger;
    @Mock
    private LocationAwareLogger locatingLogger;
    @Mock
    private ILoggerFactory loggerFactory;

    @InjectMocks
    private StaticLoggerBinder binder = getSingleton();

    @Before
    public void setup() {
        initMocks(this);
        binder.setLoggerFactory(loggerFactory);

        when(loggerFactory.getLogger(any(String.class))).thenReturn(logger);
    }

    @Test
    public void shouldDelegateLoggingAtProperLevel() {
        // Given
        Log log = new SLF4JDelegatingLog("toto.titi");

        // When
        log.trace("plip plop details");
        log.debug("plip plop dbg");
        log.info("plip plop nfo");
        log.warn("plip plop warning");
        log.error("plip plop err");
        log.fatal("plip plop boom!");

        // Then
        verify(logger).trace(eq("plip plop details"), isNull(Throwable.class));
        verify(logger).debug(eq("plip plop dbg"), isNull(Throwable.class));
        verify(logger).info(eq("plip plop nfo"), isNull(Throwable.class));
        verify(logger).warn(eq("plip plop warning"), isNull(Throwable.class));
        verify(logger).error(eq("plip plop err"), isNull(Throwable.class));
        verify(logger).error(eq("plip plop boom!"), isNull(Throwable.class));
        verifyNoMoreInteractions(logger);
        verifyZeroInteractions(locatingLogger);
    }

    @Test
    public void shouldDelegateWithThrowableAtProperLevel() {
        // Given
        Log log = new SLF4JDelegatingLog("toto.titi");
        Throwable trcExc, dbgExc, nfoExc, warnExc, errExc, fatExc;

        // When
        log.trace("bim details", trcExc = new IllegalArgumentException());
        log.debug("bim dbg", dbgExc = new RuntimeException());
        log.info("bim nfo", nfoExc = new IllegalStateException());
        log.warn("bim warning", warnExc = new NullPointerException());
        log.error("bim err", errExc = new Exception());
        log.fatal("bim boom!", fatExc = new Error());

        // Then
        verify(logger).trace("bim details", trcExc);
        verify(logger).debug("bim dbg", dbgExc);
        verify(logger).info("bim nfo", nfoExc);
        verify(logger).warn("bim warning", warnExc);
        verify(logger).error("bim err", errExc);
        verify(logger).error("bim boom!", fatExc);
        verifyNoMoreInteractions(logger);
        verifyZeroInteractions(locatingLogger);
    }

    @Test
    public void shouldAcceptAnyObject() {
        // Given
        Log log = new SLF4JDelegatingLog("toto.titi");

        // When
        log.info(new Object() {
            @Override
            public String toString() {
                return "pif paf pouf";
            }
        });

        // Then
        verify(logger).info(eq("pif paf pouf"), isNull(Throwable.class));
        verifyNoMoreInteractions(logger);
        verifyZeroInteractions(locatingLogger);
    }

    @Test
    public void shouldLogStringOfNullForNullMessages() {
        // Given
        Log log = new SLF4JDelegatingLog("toto.titi");

        // When
        log.trace(null);
        log.debug(null);
        log.info(null);
        log.warn(null);
        log.error(null);
        log.fatal(null);

        // Then
        verify(logger).trace(eq("null"), isNull(Throwable.class));
        verify(logger).debug(eq("null"), isNull(Throwable.class));
        verify(logger).info(eq("null"), isNull(Throwable.class));
        verify(logger).warn(eq("null"), isNull(Throwable.class));
        verify(logger, times(2)).error(eq("null"), isNull(Throwable.class));
        verifyNoMoreInteractions(logger);
        verifyZeroInteractions(locatingLogger);
    }

    @Test
    public void shouldSupportUnderlyingLocationAwareLoggers() {
        // Given
        when(loggerFactory.getLogger(any(String.class))).thenReturn(locatingLogger);
        Log log = new SLF4JDelegatingLog("titi.toto");

        // When
        log.trace("plop plip details");
        log.debug("plop plip dbg");
        log.info("plop plip nfo");
        log.warn("plop plip warning");
        log.error("plop plip err");
        log.fatal("plop plip boom!");

        // Then
        final String LOG_FQCN = SLF4JDelegatingLog.class.getName();
        verify(locatingLogger).log(null, LOG_FQCN, TRACE_INT, "plop plip details", null, null);
        verify(locatingLogger).log(null, LOG_FQCN, DEBUG_INT, "plop plip dbg", null, null);
        verify(locatingLogger).log(null, LOG_FQCN, INFO_INT, "plop plip nfo", null, null);
        verify(locatingLogger).log(null, LOG_FQCN, WARN_INT, "plop plip warning", null, null);
        verify(locatingLogger).log(null, LOG_FQCN, ERROR_INT, "plop plip err", null, null);
        verify(locatingLogger).log(null, LOG_FQCN, ERROR_INT, "plop plip boom!", null, null);
        verifyNoMoreInteractions(locatingLogger);
        verifyZeroInteractions(logger);
    }
}
