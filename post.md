# Reactive Java using the Vert.x toolkit

[Vert.x](https://vertx.io/) is a toolkit for developing reactive applications on the JVM.
Although it's possible to use Vert.x with many different languages (Java, JavaScript, Groovy, Ruby, Ceylon, Scala *and* Kotlin), this post will use plain old Java.

The [Reactive Manifesto](https://www.reactivemanifesto.org/) states that reactive systems are:
* responsive,
* resilient,
* elastic, and
* message driven.

Before we consider what that means in the context of Vert.x, let's look at one of the simplest possible applications using Vert.x:

```java
package nl.kabisa.vertx;

import io.vertx.core.*;
import io.vertx.core.http.HttpServerOptions;

public class Application {

    private static class HelloWorldVerticle extends AbstractVerticle {

        @Override
        public void start(Future<Void> startFuture) {
            var options = new HttpServerOptions().setPort(8080);
            vertx.createHttpServer(options)
                    .requestHandler(request -> request.response().end("Hello world"))
                    .listen();
        }
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new HelloWorldVerticle());
    }
}
```

When running this application, a single verticle is deployed when the statement `Vertx.vertx().deployVerticle(new HelloWorldVerticle());` is executed.
This verticle is an instance of the class`HelloWorldVerticle`.
Each verticle has a `start` and a `stop` method.
The `start` method is called when the verticle is deployed,
and the `stop` method is called when the verticle is undeployed.
In this example, we only provide an implementation for the `start` method and reuse the (empty) `stop` method of the class `AbstractVerticle`.
When an instance of `HelloworldVerticle` is deployed, an HTTP server is created, which listens for incoming requests of port `8080`.
Each request is answered with the plain-text response "Hello world".

