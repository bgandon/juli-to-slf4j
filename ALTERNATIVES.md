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

Alternatives to the JULI-to-SLF4J bridge
========================================

We discuss here the major alternatives to [JULI-to-SLF4J](https://github.com/bgandon/juli-to-slf4j),
and develop their advantages, complications, drawbacks or oddities.

If you are new to the many acronyms we use here, we provide you with a
[glossary](#glossary) below.


Compared to using _jul-to-slf4j_
--------------------------------

Some like “[Manage logging with Logback and Apache Tomcat](https://deviantony.wordpress.com/2014/04/09/manage-logging-with-logback-and-apache-tomcat/)”
bridge JUL to SLF4J so that you get a _JULI(mini)→JUL→jul-to-slf4j→SLF4J_
setup. But this blog post above suffers from many issues.

1. **Performance issues**

   It doesn't advise you to properly configure a [LevelChangePropagator](http://logback.qos.ch/manual/configuration.html#LevelChangePropagator).
   Without it, the impact of the _jul-to-slf4j_ bridge on performances can be
   [outrageous](http://www.slf4j.org/legacy.html#jul-to-slf4j).

   Plus, the _JULI(mini)→JUL_ step has performance issues (see the code
   excerpt in [README](README.md)).

2. **Classpath issues**

   The given recipe avises you to add the logging jars to `lib/` _and_ the
   System `$CLASSPATH`. This results in having those libs duplicated on both
   System and Catalina classpath, which is unnecessary because of class
   loading delegation rules. Indeed, the parent System classpath always
   shadows the child Catalina's classpath. As a convention in Tomcat, the libs
   should actually be in `bin/` in such cases where they are added to the
   System `$CLASSPATH` customized in `setenv.sh`.

3. **Config oddities**

   The described config uses `$CATALINA_HOME` all over the place where
   `$CATALINA_BASE` is actually expected for a
   [separate layout](http://tcserver.docs.pivotal.io/docs-tcserver/topics/postinstall-getting-started.html#postinstall-create-instance-layout)
   to work.

   Adding the `conf/logback` directory to the System classpath is a mixture of
   code & config that could definitely be misleading for Tomcat admins.


Compared to using _log4j-over-slf4j_
------------------------------------

Some like [Configuring Tomcat with Logback (FR)](http://michael-schneider.developpez.com/java/tutoriels/conf-tomcat-logback)
compose a _JULI(JCL)→juli-adapters→log4j-over-slf4j→SLF4J_ setup. It builds on
top of [Using Log4j](http://tomcat.apache.org/tomcat-8.0-doc/logging.html#Using_Log4j)
in Tomcat documentation, or the very similar [Configuring log4j](http://tcserver.docs.pivotal.io/docs-tcserver/topics/manual.html#manual-config-log4j)
in tc-server documentation.

As a first step, the solution installs a full [Jakarta Commons Logging (JCL)](http://commons.apache.org/logging)
library that ships with Tomcat “extras”. JCL is known for being a complicated
logging solution, as regards to the pretty scary (though interesting)
[classloader knownledge](http://commons.apache.org/proper/commons-logging/tech.html)
it requires.

As a second step, the solution relies on the multi-backend feature of JCL to
send the logs to the `log4j-over-slf4j` facade, as if it were actually using
[LOG4J](https://logging.apache.org/log4j/1.2/).

What's notiecable in this second step, is that the `log4j-over-slf4j` is
loaded with tha Catalina classloader. So what happens to the Tomcat logs that
might happen before the Catalina classloader is created? The response is that
they flow into a junk `juli.YYYY-MM-DD.log` file.

You can deactivate the creation of this file with a `LOGGING_CONFIG="-Dnop"`
config in `setenv.sh`, or deleting the `conf/logging.properties`. Both ways
deactivate the default JULI logging. In which case the early logs that happen
in Tomcat before Catalina startup are always discard.

You may wonder not using `jcl-over-slf4j` in the 2nd step instead? We actually
discuss this alternative below.


Compared to using _jcl-over-slf4j_
----------------------------------

Without any specific setup, just dropping the jcl-over-slf4j and SLF4J/Logback
jars into `$CATALINA_BASE/lib/`, you can get very similar result as above,
without requiring `tomcat-extras-juli-adapters`, and obtaining a canonical
_JULI(JCL)→jcl-over-slf4j→SLF4J_ setup.

The observable difference is that the junk `juli.YYYY-MM-DD.log` file contains
more logs that with the LOG4J setup above. Again, you can diable its creation
with the two ways described above, and chose to always discard those early
pre-Catalina logs.


Compared to _juli-jcl-over-slf4j_
---------------------------------

The setup above can be further improved, adding jcl-over-slf4j and
SLF4J/Logback jars directly to `$CATALINA_BASE/bin/` and the System
`$CLASSPATH`. This is exactly what we do in [juli-jcl-over-slf4j](https://github.com/bgandon/juli-jcl-over-slf4j).

Then _tomcat-slf4j-logback_ just goes further in that direction as discussed
below.


Compared to _tomcat-slf4j-logback_
----------------------------------

On the other hand, our solution tries to support as many use cases as the very
complete [tomcat-slf4j-logback](https://github.com/grgrzybek/tomcat-slf4j-logback),
which builds _JULI/jcl-over-slf4j→SLF4J_ integration. But `tomcat-slf4j-logback`
implies a high deal of surgery made to JULI, in order to merge the thin
`jcl-over-slf4j` into it, and just keep what's necessary. The result is
correct, but needs to rebuild the `tomcat-juli.jar` every time you upgrade
Tomcat. And this cannot be done with [tc-server](http://tcserver.docs.pivotal.io/docs/)
without loosing the JXM capabilities that the Pivotal guys have added into the
`tomcat-juli.jar` that they ship.


Glossary
--------

 - _JULI(mini)_ is the default logging system in Tomcat, that ships in
   `$CATALINA_HOME/bin/tomcat-juli.jar`.
   See: [org.apache.tomcat:tomcat-juli](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.apache.tomcat%22%20AND%20a%3A%22tomcat-juli%22)
   in Maven Central.

 - _JULI(JCL)_ is the full [Jakarta Commons Logging (JCL)](http://commons.apache.org/logging)
   implementation that is distributed with Tomcat “extras” (in
   `$CATALINA_HOME/bin/extras/tomcat-juli.jar`) as an optional drop-in
   replacement for _JULI(mini)_ for extended features.
   See [org.apache.tomcat.extras:tomcat-extras-juli](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.apache.tomcat.extras%22%20AND%20a%3A%22tomcat-extras-juli%22)
   in Maven Central.

 - _JUL_ is the `java.util.logging` framework that ships with Java
   implementations by default.

 - [LOG4J](https://logging.apache.org/log4j/1.2/) (LOGging for Java) was a
   very popular logging system, because at the time it brought better
   performance compared to JUL. This was until SLF4J & Logback came out. Now
   LOG4J 2.0 tries to come back in competition. But SLF4J has become a
   standard.

 - [SLF4J](http://www.slf4j.org/) (Simple Logging Facade for Java) and
   [Logback](http://logback.qos.ch/) (LOGging BACKend) started as a rewrite
   of LOG4J and brought separation of concerns and multi-backend _à la_ JCL,
   but without going into the multi-class-loaders (complicated) use cases.

 - _jul-to-slf4j_ is a bridge library that provides a JUL appender to use in
   your config so that the logs that flow through JUL loggers then go into
   SLF4J loggers and appenders.

 - _log4j-over-slf4j_ is a bridge library that provides a [LOG4J](https://logging.apache.org/log4j/1.2/)
   facade with LOG4J loggers that actually are SLF4J loggers behind the scene.

 - _jcl-over-slf4j_ is a more elaborated adapter library that leverages the
   multi-backend design of JCL. It provides a JCL implementation that sends
   JCL logs into the SLF4J system.

<!--
# Local Variables:
# indent-tabs-mode: nil
# End:
-->
