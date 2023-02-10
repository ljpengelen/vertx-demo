# Guided Tour of Vert.x

This app demonstrates part of the [Vert.x framework](https://vertx.io/).
It listens for incoming connections on port 8080 and echos the request body in all caps.
Run the app, and execute `curl localhost:8080 -d "no touching"` to see it in action.

## Prerequisites

For development, you need [Java 17](https://openjdk.org/projects/jdk/17/).

## Running tests

Execute `./mvnw test` to run the tests.

## Running the app

Execute `./mvnw package -Dmaven.test.skip` to build a JAR.
Run the app by executing `java -jar target/<NAME_OF_JAR>.jar`.

## Creating a native image

Execute the following command to create a native image:

```
native-image -jar target/<NAME_OF_JAR>.jar --no-fallback \
--initialize-at-run-time=io.netty.handler.codec.compression.ZstdOptions \
--initialize-at-build-time=org.slf4j \
--initialize-at-build-time=ch.qos.logback \
-H:ReflectionConfigurationFiles=reflect-config.json
```
