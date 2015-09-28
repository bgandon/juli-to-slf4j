Tomcat JULI-to-SLF4J  adapter library
=====================================

by Benjamin Gandon © 2015


Overview
--------

This library allows you to have Tomcat internal logs go to the
[Simple Logging Facade for Java](http://www.slf4j.org/) (SLF4J) and then
[any logging framework of your choice](http://www.slf4j.org/manual.html#swapping),
among which stands the [modern](http://logback.qos.ch/reasonsToSwitch.html)
[Logback](http://logback.qos.ch/) backend.


Why use SLF4J instead of JULI?
------------------------------

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


What is different here?
-----------------------

On one hand, our solution is similar (in terms of features) to some solutions
described in several blog posts out there, yet it implies less library code.

- Some like “[Manage logging with Logback and Apache Tomcat](https://deviantony.wordpress.com/2014/04/09/manage-logging-with-logback-and-apache-tomcat/)”
  bridge JUL to SLF4J so that you get a _JULI→JUL→SLF4J_ setup. But if you
  don't properly configure the [LevelChangePropagator](http://logback.qos.ch/manual/configuration.html#LevelChangePropagator),
  the impact on performances can be outrageous. Plus, the _JULI→JUL_ step used
  has performance issues (see the code excerpt above).

- Some others like [Configuring Tomcat with Logback (FR)](http://michael-schneider.developpez.com/java/tutoriels/conf-tomcat-logback)
  compose a _JCL→log4j-over-slf4j→SLF4J_ setup which should perform better,
  though it is quite complicated and unnecessarily involves a full
  [Apache Commons Logging](http://commons.apache.org/logging) (JCL) library.

On the other hand, our solution  supports less use cases than the very
complete [tomcat-slf4j-logback](https://github.com/grgrzybek/tomcat-slf4j-logback),
which builds _JULI/jcl-over-slf4j→SLF4J_ integration. It implies a high deal
of surgery made to JULI, in order to merge the thin `jcl-over-slf4j` into it,
and just keep what's necessary. But the result is a correct and comprehensive
SLF4J adaptation layer along with a default Logback backend.

All in all, our solution just tends to prove that the _JCL_ or
_jcl-over-slf4j_ adaptation layers can be replaced by something much simpler
which doesn't require heavy surgery, leading a direct _JULI→SLF4J_ connection.


Is it sufficient?
-----------------

Well it _can_ be. If you are in control of both the container and application,
and if you run only one application per Tomcat instance. But it does not
support all the use cases that the default JULI does.

We discuss the implications below in more details.

 - Web applications will see Tomcat's version of SLF4J and Logback on their
   classpath. This is okay if you are in control of both web applications and
   the container, and want to mutualize libraries.

   But if you implement a 3rd paty web application that ships with a version
   of SLF4J & Logback, you'll have these libraries duplicated on applications
   classpaths. This is unsually not an issue (because web applications
   classpaths take precedence over the container classpath) but it's not ideal
   and Logback will complain about it.

 - In the basic setup, you'll specify one central configuration for Logback,
   that will result in one single logging context that all web applications
   will use. Web applications will not be able to specify their own logging
   configuration.

   This is pertinent if you meet these three conditions:

   1. You are in control of both the Tomcat container and web applications.
   2. You have one single web application per Tomcat instance.
   3. You want to centralize the logging configuration.

 - If you adopt the [JNDI logging context](http://logback.qos.ch/manual/loggingSeparation.html#ContextJNDISelector)
   option, web applications will have an opportunity for separate logging
   contexts. They will be able to specify their own logging configuration, or
   delegate that to the container.

   But web applications will all construct their own logging context on top of
   the default one from the main logging config file you'll have specified for
   Tomcat (because there is only one system property for that in the JVM).
   As a consequence, you won't reach high levels of separation between the
   conatainer and web applications. You'll also have to go through the
   [Logging Separation](http://logback.qos.ch/manual/loggingSeparation.html)
   chapter of the Logback documentation, because the `ContextDetachingSCL` and
   `LoggerContextFilter` might be worth implementing. Implications on setups
   with [shared libraries](http://logback.qos.ch/manual/loggingSeparation.html#tamingStaticRefs)
   are worth reading too.


Can this be run in production?
------------------------------

Well, not without appropriate testing. We are keen on hearing about your
experience in running this JULI-to-SLF4J in production.


What do I need to setup?
------------------------

1. Build the `juli-to-slf4j` jar, running `mvn clean package` in the base
   directory of this project. (If you don't have maven installed already,
   run `brew install maven` before.)

2. Copy the resulting `target/juli-to-slf4j-*.jar` to your
   `${catalina.base}/bin/` directory. (For details about the `${catalina.base}`
   directory, go read the “[Directories and Files](https://tomcat.apache.org/tomcat-8.0-doc/introduction.html#Directories_and_Files)”
   section of the Tomcat documentation, and the
   “[Differences Between the Separate and Combined Layouts](http://tcserver.docs.pivotal.io/docs-tcserver/topics/postinstall-getting-started.html#postinstall-create-instance-layout)”
   listed the tc Server documentation.)

3. Download the latest jars for
   [slf4j-api](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.slf4j%22%20AND%20a%3A%22slf4j-api%22),
   [logback-core](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22ch.qos.logback%22%20AND%20a%3A%22logback-core%22)
   and [logback-classic](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22ch.qos.logback%22%20AND%20a%3A%22logback-classic%22)
   into your `${catalina.base}/bin/` directory.

4. Create a `${catalina.base}/bin/setenv.sh` file in which you'll customize
   some environment variables as shown below.

```bash
# Logging configuration
# Because the juli config needs to be set early at bootstrap time

# Deactivate standard JULI config
LOGGING_CONFIG="-Dnop"

# Add bridge to the class path
CLASSPATH="${CLASSPATH:+$CLASSPATH:}$CATALINA_BASE"/bin/juli-to-slf4j.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/slf4j-api-1.7.12.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/logback-core-1.1.3.jar
CLASSPATH="$CLASSPATH:$CATALINA_BASE"/bin/logback-classic-1.1.3.jar

# Print status messages (especially logging configuration) to the standard output
# See also: <http://logback.qos.ch/manual/configuration.html#statusListener>
CATALINA_OPTS="$CATALINA_OPTS -Dlogback.statusListenerClass=ch.qos.logback.core.status.OnConsoleStatusListener"

# Logback configuration for the 'default' logging context, which is primarily
# the logging context dedicated to catalina
CATALINA_OPTS="$CATALINA_OPTS -Dlogback.configurationFile=$CATALINA_BASE/conf/logback-catalina.xml"

# For more details about the logging separation across different webapps,
# see: <http://logback.qos.ch/manual/loggingSeparation.html#ContextJNDISelector>
#CATALINA_OPTS="$CATALINA_OPTS -Dlogback.ContextSelector=JNDI"
```

The environment variables you customize in `setenv.sh` are then used by
the `catalina.sh` startup script in order to properly configure Tomcat
startup. (For more details about the `setenv.sh` file, read the Tomcat
[RUNNING.txt](https://tomcat.apache.org/tomcat-8.0-doc/RUNNING.txt),
as referenced in the [Tomcat setup](https://tomcat.apache.org/tomcat-8.0-doc/setup.html#Introduction)
documentation.)

In the `setenv.sh` snippet above, please note that setting
`logback.ContextSelector` is optional. This system property can also be
customized in `${catalina.base}/conf/catalina.properties`, which creates
system properties at static initialization of catalina startup classes. This
is early, but all other system properties above must be in `setenv.sh`, so
that they get properly created even before. Indeed, the static loggers defined
in catalina startup classes are initialized right before `catalina.properties`
is parsed.


Author and License
------------------

Copyright © 2015, Benjamin Gandon

Like Tomcat, the `juli-to-slf4j` library is released under the terms of the
[Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0).

<!--
# Local Variables:
# indent-tabs-mode: nil
# End:
-->
