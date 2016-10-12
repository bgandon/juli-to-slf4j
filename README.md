<!--
   Copyright 2015 Benjamin Gandon

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

Tomcat JULI-to-SLF4J bridge library [![.](http://gaproxy.gstack.io/UA-68445280-1/bgandon/juli-to-slf4j/readme?pixel&dh=github.com)](https://github.com/gstackio/ga-beacon)
===================================

by Benjamin Gandon © 2015


Overview
--------

This library allows you to have Tomcat internal logs go to the
[Simple Logging Facade for Java](http://www.slf4j.org/) (SLF4J) and then
[any logging framework](http://www.slf4j.org/manual.html#swapping) of your
choice, among which stands the [modern](http://logback.qos.ch/reasonsToSwitch.html)
[Logback](http://logback.qos.ch/) backend.


Discussion
----------

### Why use SLF4J instead of JULI?

For several reasons.

 - You like SLF4J, and it's [a cool thing](http://www.slf4j.org/manual.html#summary)
   indeed. Logback [is, too](http://logback.qos.ch/reasonsToSwitch.html).

 - You have invested time in learning their concepts and configuration. You
   know how to properly leverage Logback: its possibilities, implied tradeoffs,
   for your various environments, etc.

 - You don't want to start over learning yet-another-logging-framework. And
   you find that JULI is a strange beast.

 - You would like unified logging format and configurations for both your
   Servlet container (Tomcat) and your web applications. You find the default
   JULI configuration awful (though it can be set up pretty right with quite a
   deal of effort).

 - You want the configuration to [automatically reload](http://logback.qos.ch/manual/configuration.html#autoScan),
   because you don't want to waiste your time reastarting Tomcat each time you
   tweak the logging configuration.

 - You have read the following code from `DirectJDKLog`, which is the only
   logger implementation in the default `tomcat-juli` jar.

```java
    // from commons logging. This would be my number one reason why java.util.logging
    // is bad - design by committee can be really bad ! The impact on performance of
    // using java.util.logging - and the ugliness if you need to wrap it - is far
    // worse than the unfriendly and uncommon default format for logs.

    private void log(Level level, String msg, Throwable ex) {
        if (logger.isLoggable(level)) {
            // Hack (?) to get the stack trace.
            Throwable dummyException=new Throwable();
            StackTraceElement locations[]=dummyException.getStackTrace();
            ...
        }
    }
```

This `log()` method above is the one that all log events go through before
they are passed to the `java.util.logging` framework. Each time a log event is
sent to an active logger, a stacktrace is unconditionally computed. This
expensive operation is something that logging framework generally tent to
avoid for performance reasons. Here JULI is forced to do so, in order to
comply with JUL requirements.


### What is different here?

The JULI-to-SLF4J bridge is smaller and easier to setup. It does not suffer
from complications, drawbacks or oddities of other solutions. For a detailed
discussion on alternatives, see [ALTERNATIVES.md](ALTERNATIVES.md).

To summarize this separate discussion, the conclusion is that JULI-to-SLF4J
just plugs into any default `tomcat-juli` (from bare Tomcat or [tc-server](http://tcserver.docs.pivotal.io/docs/))
and implements a direct _JULI→SLF4J_ connection, without heavy surgery,
and without silently discarding any logs.


### Limitations and Caveats

We only deal here with limitations related to using Logback as the SLF4J
implementation. We are keen on hearing your feedback about using other SLF4J
implementations, though.

#### Limitations in using Logback

Due to its early startup in Tomcat, the JULI-to-SLF4J bridge cannot use the
[JNDI context selector](http://logback.qos.ch/manual/loggingSeparation.html#ContextJNDISelector)
because JNDI is initialized at a later stage. Whether you add Logback on the
[System classpath](https://tomcat.apache.org/tomcat-8.0-doc/class-loader-howto.html#Class_Loader_Definitions)
or on Catalina classpath, it's the same.

Consequently, speaking of [Logging Separation](http://logback.qos.ch/manual/loggingSeparation.html),
you will have to stick to the [simple approach](http://logback.qos.ch/manual/loggingSeparation.html#easy).
Applications are highly recommended to ship with their own Logback library so
that they create a separated logging context for their own use.

Otherwise, if applications use the central SLF4J of Catalina, then they will
share their logging context. This means that when two different webapps both
use a logger with the same name, they will actually log to the very same
`Logger` object, with the exact same configuration. Consequently, hot-deploy
will also be compromized. Indeed, logging-related resources will not properly
be released when their related web application stops.

If one of your shared library uses static SLF4J loggers, then the “simple
approach” will result in your shared library using Catalina's SLF4J. The
logging configuration for your shared library will take place in the
`conf/logback- catalina.xml`. Depending on your organization and use cases,
this can be an upside or a downside.

Anyway, the “simple approach” is definitely simpler than using the
`ContextJNDISelector`, which leads to configuring [ContextDetachingSCL](http://logback.qos.ch/manual/loggingSeparation.html#hotDeploy)s,
[LoggerContextFilter](http://logback.qos.ch/manual/loggingSeparation.html#betterPerf)s
(for each web application or in the default `web.xml`) and dealing with all
the [crazy stuff](http://logback.qos.ch/manual/loggingSeparation.html#tamingStaticRefs)
described in the [Logging Separation](http://logback.qos.ch/manual/loggingSeparation.html)
chapter. Your shared library will not be able to output differentiated logs,
though. Differentiating the logs produced by different applications using this
shared library will not be straightforward.

#### Caveats

The JULI-to-SLF4J bridge relies on an undocumented extension mechanism of the
default [tomcat-juli](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.tomcat%22%20AND%20a%3A%22tomcat-juli%22)
that allows to plug-in any other logging system very simply, without resorting
to a complete Jakarta Commons Logging library.

The `juli-to-slf4j.jar` must appear before the `tomcat-juli.jar` on the
`$CLASSPATH` as detailed below. Indeed, the default `org.slf4j.LoggerFactory`
is not suppressed from the original `slf4j-api.jar`. It is just masked by the
one provided by `juli-to-slf4j.jar`. Any reason for a non- guraanteed
classpath order would result in a non-working system.

Running Tomcat with a security manager requires some more setup in
`conf/catalina.policy`.


### Can this be run in production?

Well, not without appropriate testing. We are keen on hearing your feedback
about running the JULI-to-SLF4J bridge in production.


Setup
-----

1. Build the JULI-to-SLF4J Maven project, running `mvn clean package` in the
   base directory of this project. (If you don't have
   [Maven](https://maven.apache.org/) on your system, you first need to
   `brew install maven` (or anything similar with `apt-get` or `yum`) or
   [install it manually](https://maven.apache.org/install.html).)

2. Copy the resulting `target/juli-to-slf4j-1.1.0.jar` to your
   `$CATALINA_BASE/bin/` directory. (For details about the `$CATALINA_BASE`
   directory, go read the “[Directories and Files](https://tomcat.apache.org/tomcat-8.0-doc/introduction.html#Directories_and_Files)”
   section of the Tomcat documentation, and the
   “[Differences Between the Separate and Combined Layouts](http://tcserver.docs.pivotal.io/docs-tcserver/topics/postinstall-getting-started.html#postinstall-create-instance-layout)”
   listed the tc-server documentation.)

3. Download the latest jars for
   [slf4j-api](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.slf4j%22%20AND%20a%3A%22slf4j-api%22),
   [logback-core](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22ch.qos.logback%22%20AND%20a%3A%22logback-core%22)
   and [logback-classic](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22ch.qos.logback%22%20AND%20a%3A%22logback-classic%22)
   into your `$CATALINA_BASE/bin/` directory.

4. Create a `$CATALINA_BASE/bin/setenv.sh` file in which you'll customize
   some environment variables as shown below.

```bash
# Logging configuration
# Because the juli config needs to be set early at bootstrap time

# Deactivate standard JULI config
LOGGING_CONFIG="-Dnop"

# Add bridge to the class path
CLASSPATH="${CLASSPATH:+$CLASSPATH:}$CATALINA_BASE"/bin/juli-to-slf4j-1.1.0.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/slf4j-api-1.7.12.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/logback-core-1.1.3.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/logback-classic-1.1.3.jar

# Control verbosity of the JULI-to-SLF4J bridge (ERROR=40, WARN=30 the default, INFO=20, DEBUG=10, TRACE=0)
CATALINA_OPTS="$CATALINA_OPTS -Dorg.apache.juli.logging.impl.SLF4JDelegatingLog.diagnostics=30"

# Print status messages (especially logging configuration) to the standard output
# See also: <http://logback.qos.ch/manual/configuration.html#statusListener>
CATALINA_OPTS="$CATALINA_OPTS -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener"

# Logback configuration for the 'catalina' logging context
CATALINA_OPTS="$CATALINA_OPTS -Djuli.logback.configurationFile=$CATALINA_BASE/conf/logback-catalina.xml"
```

The environment variables you customize in `setenv.sh` are then used by
the `catalina.sh` startup script in order to properly configure Tomcat
startup. (For more details about the `setenv.sh` file, read the Tomcat
[RUNNING.txt](https://tomcat.apache.org/tomcat-8.0-doc/RUNNING.txt),
as referenced in the [Tomcat setup](https://tomcat.apache.org/tomcat-8.0-doc/setup.html#Introduction)
documentation.)

In the `setenv.sh` snippet above, please note that setting
`logback.ContextSelector` is optional. This system property can also be
customized in `$CATALINA_BASE/conf/catalina.properties`, which creates
system properties at static initialization of catalina startup classes. This
is early, but all other system properties above must be in `setenv.sh`, so
that they get properly created even before. Indeed, the static loggers defined
in catalina startup classes are initialized right before `catalina.properties`
is parsed.

Then you can customize your logging config with such a
`$CATALINA_BASE/conf/logback-catalina.xml`:

```xml
<configuration scan="true" scanPeriod="10 seconds">
    <!-- The logging context name.
         See: <http://logback.qos.ch/manual/configuration.html#contextName> -->
    <contextName>catalina</contextName>

    <!-- We add a delay of 1.5s to ensure everything is flushed to the disk.
         See: <http://logback.qos.ch/manual/configuration.html#shutdownHook> -->
    <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook">
        <!-- This value should be greater than the 'maxFlushTime' of any
             AsyncAppender below. -->
        <delay>1500</delay>
    </shutdownHook>

    <!-- Propagate onto the java.util.logging (JUL) framework
         the changes made to the level of any logback-classic logger.

         JUL is disabled though when we activate our JULI-to-SLF4J bridge.
         This is useful in case we start using jul-to-slf4j.

         See: <http://logback.qos.ch/manual/configuration.html#LevelChangePropagator> -->
    <contextListener class="ch.qos.logback.classic.jul.LevelChangePropagator"/>

    <!-- We raise the default "compressed" class name from 36 (logback
         default) up to 50. -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%date [%thread] %-5level %logger{50} - %msg%n%rootException</pattern>
        </encoder>
    </appender>

    <!-- We activate asynchronous logging. -->
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="STDOUT" />
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <!-- 'maxFlushTime' should be smaller than the 'delay' of the
             'shutdownHook' above. The default is 1000 ms -->
        <maxFlushTime>1200</maxFlushTime>
    </appender>

    <!-- We can here lower the threshold from INFO down to DEBUG. This can be
         done during runtime because of the scan="true" attribute above. -->
    <root level="INFO">
        <appender-ref ref="ASYNC" />
    </root>
</configuration>
```


### Customizing Logback config for 3rd party web applications

When you deploy an external war on your Tomcat, and the application ships with
SLF4J, Logback, and a default logging configuration,  you might be interested
in customizing this configuration without modifying the war content.

For this you have two options.

1. If you have only one webapp using Logback, you can use the global
   `logback.configurationFile` system property. This property is for the use
   of webapps only. For Catalina's Logback, another
   `juli.logback.configurationFile` system property is used by the
   JULI-to-SLF4J bridge. For example, if _steve-vai_ is the name of your
   webapp, put this in your `$CATALINA_BASE/conf/catalina.properties`:

    logback.configurationFile=${catalina.base}/conf/steve-vai/logback.xml

2. If you have more than one webapps using logback, you can't use the global
   system property like above. As an alternative, you may inject some external
   logback configuration into the webapp classpath with [pre-resources](https://tomcat.apache.org/tomcat-8.0-doc/config/resources.html).
   For example, if _steve-vai_ is the name of your webapp, in your
   `$CATALINA_BASE/conf/Catalina/localhost/steve-vai.xml` context file, you
   can put this:

```xml
<Context ...>
    ...
    <!-- Tomcat 7 uses VirtualWebappLoader -->
    <!-- <Loader className="org.apache.catalina.loader.VirtualWebappLoader"
            virtualClasspath="${catalina.base}/steve-vai/logback.xml"/> -->
    <!-- Tomcat 8 uses WebResourceRoot, see: <http://stackoverflow.com/a/23152546> -->
    <Resources className="org.apache.catalina.webresources.StandardRoot">
        <PreResources base="${catalina.base}/conf/steve-vai/logback.xml"
                      className="org.apache.catalina.webresources.FileResourceSet"
                      readOnly="true"
                      webAppMount="/WEB-INF/classes/logback.xml" />
        ...
    </Resources>
</Context>
```

Be careful if your web application uses OSGi. Tomcat pre-resources are
customizations made to Tomcat's webapps class loaders. OSGi systems redefine
their own class loaders. There is little chance that those custom class
loaders are aware of any pre-resources configured in Tomcat.


### Alternate setup of Logback, on the Catalina's classpath

The support for this alternate setup was originally added to solve classpath
issues that prevented Logback's JNDI-related classes from properly loading (we
give more details about that below). But the experiment concluded in the JNDI
setup not being possible anyway, because Logback still initializes too early.

Anyway, we document this alternative because it may help you in using Logback
helper classes that require being on the same classpath as Tomcat libs.

In this alternate setup, the only difference with the above is that you put
`logback-core` and `logback-classic` in the `$CATALINA_BASE/lib/` directory.
Then the `setenv.sh` needs not adding them to the [System classpath](https://tomcat.apache.org/tomcat-8.0-doc/class-loader-howto.html#Class_Loader_Definitions).

```bash
# Logging configuration
# Because the juli config needs to be set early at bootstrap time

# Deactivate standard JULI config
LOGGING_CONFIG="-Dnop"

# Add bridge to the class path
CLASSPATH="${CLASSPATH:+$CLASSPATH:}$CATALINA_BASE"/bin/juli-to-slf4j-1.1.0.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/slf4j-api-1.7.12.jar

# Control verbosity of the JULI-to-SLF4J bridge (ERROR=40, WARN=30 the default, INFO=20, DEBUG=10, TRACE=0)
CATALINA_OPTS="$CATALINA_OPTS -Dorg.apache.juli.logging.impl.SLF4JDelegatingLog.diagnostics=30"

# Print status messages (especially logging configuration) to the standard output
# See also: <http://logback.qos.ch/manual/configuration.html#statusListener>
CATALINA_OPTS="$CATALINA_OPTS -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener"

# Logback configuration for the 'catalina' logging context
CATALINA_OPTS="$CATALINA_OPTS -Djuli.logback.configurationFile=$CATALINA_BASE/conf/logback-catalina.xml"
```

What happens then is that the JULI-to-SLF4J bridge will defer (by ≈200ms and
≈64 log messages) the actual initialization of Logback, until the Catalina
class loader is detected. This happens very fast because creating this class
loader is the very first step of Catalina's startup.

What happens then is that the JULI-to-SLF4J bridge will retain in memory the
few early log messages, until the Catalina class loader is ready. When this
class loader is ready, the bridge intializes Logback and flushes the early
messages into its appenders. The result is that the early log messages arrive
just after Logback initialization, with a ≈200ms delay, resulting in a non
continuous timeline in those startup logs.

One caveat is that you will not be able to control the log level for messages
that are logged before Logback initializes. All of them will show up in your
appenders. We believe this is still better than appending the startup logs to
an unexpected junk `juli.YYYY-MM-DD.log` files, or just silently discard them.
See the discussion on [ALTERNATIVES](ALTERNATIVES.md) for more details.


#### Why put Logback on Catalina's classpath?

If you are not familiar with class loaders, we encourage you to first read the
two chapters below, written by the JCL guys. It properly lays out all the
concepts and rules you need to know. Fasten your seatbelt.

 - [A Short Introduction to Class Loading and Class Loaders](http://commons.apache.org/proper/commons-logging/tech.html#A_Short_Introduction_to_Class_Loading_and_Class_Loaders)
 - [A Short Guide To Hierarchical Class Loading](http://commons.apache.org/proper/commons-logging/tech.html#A_Short_Guide_To_Hierarchical_Class_Loading)

When Logback is on the [System classpath](https://tomcat.apache.org/tomcat-8.0-doc/class-loader-howto.html#Class_Loader_Definitions),
then any `ContextDetachingSCL` declared in a `web.xml` (for a waeapp that
doesn't ship with Logback) will be loaded by the System class loader. That's
the rule. The child class loader used when parsing `web.xml` (and creating the
web application) will delegate to its parent.

So `ContextDetachingSCL` will use its own class loader (the System class
loader) to load the `ServletContextListener` interface that it implements.
Right. But that one is _not_ on the System classpath! It belongs to Tomcat
jars, so it's on Catalina's classpath instead. As a result, the
`ServletContextListener` interface can only be reached by the (child) Catalina
class loader, or any of its children.

That's why the JULI-to-SLF4J supports providing Logback on the Catalina
classpath. All in all, `ContextDetachingSCL` and `LoggerContextFilter` are
useless without the `ContextJNDISelector`, that cannot be used with JULI-to-
SLF4J because Logback intializes before JNDI does. Anyway, the support for the
“Catalina classpath” alternative is kept in JULI-to-SLF4J for users that might
need Logback on that classpath.


Author and License
------------------

Copyright © 2015, Benjamin Gandon

Like Tomcat, the JULI-to-SLF4J library is released under the terms of the
[Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0).

<!--
# Local Variables:
# indent-tabs-mode: nil
# End:
-->
