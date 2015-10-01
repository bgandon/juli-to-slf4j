package org.apache.juli.logging.impl;

import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.currentThread;
import static org.slf4j.helpers.Util.report;
import static org.slf4j.spi.LocationAwareLogger.DEBUG_INT;
import static org.slf4j.spi.LocationAwareLogger.TRACE_INT;

import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class that the {@link SLF4JDelegatingLog} relies on for bootstrapping
 * the separated Logback system when the time has come.
 * <p>
 * When an SLF4J implementation is detected at class initialization, no fancy
 * bootstrapping occurs. The detected implementation will just be used. This
 * behavior is compatible with version 1.0.x of the {@code juli-to-slf4j}
 * library.
 * <p>
 * When no SLF4J implementation is available right from the start, its
 * bootstrapping will automatically be triggered as soon as it is detected.
 * <p>
 * This deferred bootstrapping is made of three steps, during which all loggers
 * and logging events creations are blocked.
 * <ol>
 * <li>The {@link LoggerFactory} is triggered, binds to the
 * {@code StaticLoggerBinder} of the Logback that is accessible only by the
 * Catalina class loader, which is a child class loader</li>
 * <li>All pre-bootstrap logging events are flushed to Logback appenders.</li>
 * <li>All pre-bootstrap loggers are swapped with their actual counterpart as
 * obtained by the SLF4J API through the standard
 * {@link LoggerFactory#getLogger(String)}</li>
 * </ol>
 * <p>
 * For the bootstrapping process to properly block loggers and logging events
 * creations in multi-thraded environments, this implementation synchronizes
 * globally on the {@link SLF4JDelegatingLog SLF4JDelegatingLog.class}, exactly
 * as the facade {@link SLF4JDelegatingLog} instances do (and are supposed to
 * do).
 * <p>
 * The automatic bootstrap-time detection runs a check once every
 * {@value #ONCE_EVERY_FIVE_TIMES} log requests. The current thread
 * {@linkplain Thread#getContextClassLoader() context class loader} is compared
 * with the class loader that has loaded this {@link SeparateLogbackSupport}
 * class. When they start being different, then we conclude that the Catalina
 * classloader has been created. The Logback system is then initialized. As a
 * reminder, here is the difference between those two loaders:
 * <ul>
 * <li><strong>System class loader:</strong> its classpath is defined by the
 * {@code $CLASSPATH} environment variable that is built from scratch in
 * {@code catalina.sh} startup script, with the help of {@code setenv.sh}, if
 * any. The Jar files it contains should be in {@code $CATALINA_HOME/bin} or
 * {@code $CATALINA_BASE/bin}.</li>
 * <li><strong>Catalina class loader:</strong> in most setups, this is the
 * <em>common</em> class loader in Tomcat, with a classpath made of
 * {@code $CATALINA_HOME/lib} and {@code $CATALINA_BASE/lib}. Very few setups
 * diverge from this default configuration.</li>
 * </ul>
 * For more details about Tomcat class loaders, see the <a
 * href="https://tomcat.apache.org/tomcat-8.0-doc/class-loader-howto.html">Class
 * Loader</a> section of the Tomcat documentation.
 *
 * @since 1.1.0
 * @author Benjamin Gandon
 */
public class SeparateLogbackSupport {

    private static final String SLF4J_IMPL_STATIC_LOGGER_BINDER_RSC = "org/slf4j/impl/StaticLoggerBinder.class";

    static final String LOGBACK_STATUS_LISTENER_PROPERTY = "logback.statusListenerClass";
    static final String LOGBACK_CONFIG_PROPERTY = "logback.configurationFile";
    static final String LOGBACK_CTX_SELECTOR_PROPERTY = "logback.ContextSelector";
    private static final String JULI_PREXIX = "juli.";
    static final String JULI_LOGBACK_STATUS_LISTENER_PROPERTY = JULI_PREXIX + LOGBACK_STATUS_LISTENER_PROPERTY;
    static final String JULI_LOGBACK_CONFIG_PROPERTY = JULI_PREXIX + LOGBACK_CONFIG_PROPERTY;
    static final String JULI_LOGBACK_CTX_SELECTOR_PROPERTY = JULI_PREXIX + LOGBACK_CTX_SELECTOR_PROPERTY;

    /**
     * This volatile flag is {@code false} before the actual logging system is
     * bootstrapped, then {@code true} afterwards. It only changes once, and is
     * supposed to never go back to {@code false}.
     * <p>
     * As an optimization, it is package-private, as opposed to accessible
     * through a getter method.
     */
    static volatile boolean bootstrapped = false;

    /**
     * When this flag is {@code true} when some SLF4J implementation is detected
     * on the class path right from the start. As we'll use this available
     * implementation, a value of {@code true} indicates that we won't defer the
     * backend initialization.
     */
    private static boolean doBootstrapASAP;

    static {
        ClassLoader systemLoader = SeparateLogbackSupport.class.getClassLoader();
        if (null != systemLoader && null != systemLoader.getResource(SLF4J_IMPL_STATIC_LOGGER_BINDER_RSC)) {
            if (SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
                report("SeparateLogbackSupport.<static init>(): detected SLF4J implementation on classpath. Using it ASAP.");
            }
            doBootstrapASAP = true;
        } else {
            doBootstrapASAP = false;
            runBootstrapAtShutdownIfNotYetDone();
        }
    }

    /**
     * A central helper method to create loggers in the actual SLF4J
     * implementation. ths is the method to use when {@link #bootstrapped} is
     * {@code true}.
     * <p>
     * When {@link #bootstrapped} is {@code false}, calling this method will
     * trigger the initialization of the SLF4J implementation. This should be
     * done with care.
     */
    static Logger obtainLogger(final String name) {
        if (SLF4JDelegatingLog.diagnostics <= TRACE_INT) {
            report("SeparateLogbackSupport.obtainLogger('" + name + "')");
        }
        return LoggerFactory.getLogger(name);
    }

    /**
     * Helper method for creating pre-bootstrap loggers. When the logging system
     * is bootstrapped, an actual logger is returned instead.
     *
     * @param facade
     *            the facade log for the underlying logger to create
     * @param name
     *            the name of the logger to create
     * @return the created logger
     */
    static Logger obtainPreBootstrapLoggerIfNecessary(final SLF4JDelegatingLog facade, final String name) {
        if (doBootstrapASAP) {
            final Logger[] loggerRef = { null };
            doBootstrapRunning(new Runnable() {
                @Override
                public void run() {
                    // The logger factory will bootstrap as we create this very
                    // first logger
                    loggerRef[0] = obtainLogger(name);
                }
            });
            return loggerRef[0];
        }

        // Here we are in the case of deferred bootstrap
        bootstrapLoggingSystemIfPossible();
        synchronized (SLF4JDelegatingLog.class) {
            // The 'bootstrapMode' might change while we are waiting at the
            // entrance of this critical section, so we check it again here
            if (bootstrapped) {
                return obtainLogger(name);
            } else {
                if (SLF4JDelegatingLog.diagnostics <= TRACE_INT) {
                    report("SLF4JDelegatingLog > new PreBootstrapLogger('" + name + "')");
                }
                return new PreBootstrapLogger(facade, name);
            }
        }
    }

    private static final AtomicInteger count = new AtomicInteger(0);
    static final int ONCE_EVERY_FIVE_TIMES = 5;

    /**
     * Starts the bootstrapping process when the expected Catalina class loader
     * is detected.
     * <p>
     * For performance reasons, the detection is run once every
     * {@value #ONCE_EVERY_FIVE_TIMES} invocations.
     */
    static void bootstrapLoggingSystemIfPossible() {
        if (count.incrementAndGet() % ONCE_EVERY_FIVE_TIMES != 0) {
            return;
        }
        if (SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
            report("SeparateLogbackSupport.doBootstrapIfPossible()");
        }
        ClassLoader systemLoader = SeparateLogbackSupport.class.getClassLoader();
        ClassLoader catalinaLoader = currentThread().getContextClassLoader();
        if (systemLoader != catalinaLoader && catalinaLoader != null
                && catalinaLoader instanceof java.net.URLClassLoader) {

            if (SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
                URL systemRsc = systemLoader.getResource(SLF4J_IMPL_STATIC_LOGGER_BINDER_RSC);
                URL catalinaRsc = catalinaLoader.getResource(SLF4J_IMPL_STATIC_LOGGER_BINDER_RSC);
                report("in system loader [" + systemLoader + "]: " + systemRsc);
                report("in common loader [" + catalinaLoader + "]: " + catalinaRsc);
            }

            doBootstrapRunning(new DeferredInit(catalinaLoader));
        }
    }

    /**
     * Mutualized deferred init code for
     * {@link SeparateLogbackSupport#doBootstrapRunning doBootstrapRunning()}.
     */
    private static class DeferredInit implements Runnable {
        private ClassLoader catalinaLoader;

        public DeferredInit(final ClassLoader catalinaLoader) {
            super();
            this.catalinaLoader = catalinaLoader;
        }

        @Override
        public void run() {
            // The logger factory will bootstrap when we create the first logger
            // while flushing events
            PreBootstrapLoggingEvent.flushEvents(catalinaLoader);
            PreBootstrapLogger.swapLoggers();
        }
    }

    /**
     * Registers a {@linkplain Runtime#addShutdownHook shutdown hook} that
     * triggers the bootstrapping of the logging system if it has not been done
     * yet when the JVM shuts down.
     * <p>
     * The result will be SLF4J warnings if no SLF4J implementation is
     * available.
     */
    private static void runBootstrapAtShutdownIfNotYetDone() {
        try {
            getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    if (SLF4JDelegatingLog.diagnostics <= DEBUG_INT) {
                        report("SeparateLogbackSupport.ShutdownHook.run()");
                    }
                    ClassLoader catalinaLoader = currentThread().getContextClassLoader();
                    doBootstrapRunning(new DeferredInit(catalinaLoader));
                }
            });
        } catch (IllegalStateException ignore) {
            // We are probably already being shutdown. Ignore this error.
        }
    }

    /**
     * Run the bootstrapping process, flushing early log events, binding the
     * existing {@link LoggerFactory} to the actual {@code StaticLoggerBinder},
     * and replacing pre-bootstrap loggers with actual loggers.
     */
    private static void doBootstrapRunning(final Runnable actualInitCode) {
        synchronized (SLF4JDelegatingLog.class) {
            // The 'bootstrapMode' might change while we are waiting at the
            // entrance of this critical section, so we check it again here
            if (bootstrapped) {
                return;
            }

            String juliStatusListener = System.getProperty(JULI_LOGBACK_STATUS_LISTENER_PROPERTY);
            String juliConfigFile = System.getProperty(JULI_LOGBACK_CONFIG_PROPERTY);
            String juliCtxSelector = System.getProperty(JULI_LOGBACK_CTX_SELECTOR_PROPERTY);
            String generalStatusListener = System.getProperty(LOGBACK_STATUS_LISTENER_PROPERTY);
            String generalConfigFile = System.getProperty(LOGBACK_CONFIG_PROPERTY);
            String generalCtxSelector = System.getProperty(LOGBACK_CTX_SELECTOR_PROPERTY);
            try {
                // We trick Logback into giving it a config file just for this
                // default context to initialize. Otherwise JNDI logging
                // contexts try to initialize this as their default context.
                overrideSystemProperty(LOGBACK_STATUS_LISTENER_PROPERTY, juliStatusListener);
                overrideSystemProperty(LOGBACK_CONFIG_PROPERTY, juliConfigFile);
                overrideSystemProperty(LOGBACK_CTX_SELECTOR_PROPERTY, juliCtxSelector);

                actualInitCode.run();

                bootstrapped = true;
            } finally {
                restoreOverriddenSystemProperty(LOGBACK_STATUS_LISTENER_PROPERTY, juliStatusListener,
                        generalStatusListener);
                restoreOverriddenSystemProperty(LOGBACK_CONFIG_PROPERTY, juliConfigFile, generalConfigFile);
                restoreOverriddenSystemProperty(LOGBACK_CTX_SELECTOR_PROPERTY, juliCtxSelector, generalCtxSelector);
            }
        }
    }

    /**
     * Helper method to set a system property only if the overriding value is
     * not {@code null}.
     */
    private static void overrideSystemProperty(final String propName, final String overridingValue) {
        if (overridingValue != null) {
            System.setProperty(propName, overridingValue);
        }
    }

    /**
     * Helper method to restore the value of a system property after it has been
     * overridden with the {@link #overrideSystemProperty} helper.
     */
    private static void restoreOverriddenSystemProperty(final String propName, final String overridingValue,
            final String previousValue) {
        if (overridingValue != null) {
            if (previousValue == null) {
                System.getProperties().remove(propName);
            } else {
                System.setProperty(propName, previousValue);
            }
        }
    }

}
