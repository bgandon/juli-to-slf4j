Requirements for a Tomcat internal logging system
=================================================

This document is a work-in-progress discussion on the logging requirements in
Tomcat and the implemented default solutions.


Requirements
------------

Internal logging in Tomcat is not an easy thing, essentially because of three
main requirements.

1. **Logging Separation**:
   Everyone (both the Tomcat container and web applications) should be able to
   have its own “logging space” without interferring with others.

1.1 When necessary, Tomcat internal logging should be able to stay completely
    separated from web applications, in terms of configuration, library code,
    and class loading.

1.2 The logging done by different web applications running in the same Tomcat
    container should _always_ be kept completely separated from oneanother, in
    terms of configuration, library code, and class loading.

2. **Logging Delegation**:
   When necessary, web applications should be able to delegate logging to the
   container. Either delegating configuration to the container, or shipping
   with their own.

   Containers also ought to provide the logging facility mandated by the
   Servlet Specification.

3. **Logging Flexibility**:
   Web applications should be able to leverage _any_ logging framework they
   like.


Discussion
----------

### Avoid library conflicts

Would Tomcat's internal logging leverage a logging library “X” that might be
used by a web application, then web applications failing at providing their
own copy of “X” (whatever the reason), would use the Tomcat internal system by
inadvertance.

This would be a Tomcat weakness, that the separation between the container and
web applications could be broken by inadvertance.

Moreover, the library “X” in Tomcat could be a version 1.0, whereas the web
application could require a version 2.0.

All in all, the logging separation leads you to avoid having Tomcat's logging
system in packages where web application expect their own logging systems to
be.

Then, the logging separation between webapps is ensured by classical classpath
separation.


### Solutions in JULI

The default internal logging systemin Tomcat (called JULI) supports all these
requirements. Plus, it's dynamically configurable through JMX. Definitely not
bad, indeeed. But the best point of JULI in meeting these requirements is that
it's so crappy (in terms of configuration and performance) that no web
applications will _ever_ use it for their own needs. This fact dramatically
lowers the risk for conflicts! This is definitely not the same for SLF4J and
Logback, that _every_ modern web applications will certainly use.

#### Logging Separation

JULI _relocates_ a stripped-down version of
[Jakarta Commons Logging](http://commons.apache.org/logging) to some Tomcat
private packages. The base package `org.apache.commons.logging` is just
changed to `org.apache.juli.logging`.

With JULI, web applications can freely ship the Jakarta Commons Logging
framework themselves, in the usual `org.apache.commons.logging` package.

#### Class loading separation

JULI also has separated log managers for the (necesarily separated) class
loaders of web applications.

#### Configuration separation

The logging configuration should be found preferabily on the application
classpath. Verigying that the default JULI can do that is left as an excercice
to the reader, though.

#### Logging delegation

Applications can opt in using the Jakarta Commons Logging provided in
`org.apache.juli.logging`. There is little chance that this specific package
is used by inadvertance.

#### Configuration delegation

The logging configuration can be delegated to the container.
