![Logo](https://github.com/openrewrite/rewrite/raw/master/doc/logo-oss.png)
### Migrates off of old logging frameworks. Automatically.

[![Build Status](https://circleci.com/gh/openrewrite/rewrite-logging.svg?style=shield)](https://circleci.com/gh/openrewrite/rewrite-logging)
[![Apache 2.0](https://img.shields.io/github/license/openrewrite/rewrite-logging.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.openrewrite.plan/rewrite-logging.svg)](https://mvnrepository.com/artifact/org.openrewrite.plan/rewrite-logging)

### What is this?

This project implements a [Rewrite module](https://github.com/openrewrite/rewrite) that migrates Java code off of Log4J, Apache Commons Logging, and JUL and on to SLF4J.

### How do I use it?

Download the release from Maven Central and in the root of a directory containing any number of projects with Java code to migrate run:

```java
java -jar rewrite-logging.jar
```

Alternatively, run `./gradlew fixSourceLint` with Rewrite Logging enabled in the [Gradle plugin](https://github.com/openrewrite/rewrite-gradle-plugin) or `./mvnw org.openrewrite.maven:maven-rewrite-plugin:fixSourceLint` in the [Maven plugin](https://github.com/openrewrite/rewrite-maven-plugin).