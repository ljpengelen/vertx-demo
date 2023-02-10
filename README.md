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
