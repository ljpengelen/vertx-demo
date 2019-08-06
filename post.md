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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;

public class Application {

    private static class HelloWorldVerticle extends AbstractVerticle {

        @Override
        public void start() {
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
When an instance of `HelloworldVerticle` is deployed, an HTTP server is created, which listens for incoming requests on port `8080`.
Each request is answered with the plain-text response "Hello world".

## Responsive

By default, Vert.x creates two threads per CPU core for verticles like the one above.
Each verticle is assigned to a specific thread, and all handlers of that verticle are executed on that thread sequentially.
For the example above, this means that the handler `request -> request.response().end("Hello world")` is always executed on the same thread.
 
Because the handlers for a given verticle are never executed concurrently, you don't have to worry about locking or atomicity of actions.
Multiple instances of the same verticle, however, *can* have their handlers executed at the same time.
In fact, this holds for any two verticles.
This means that if two verticles share a resource, you might still have to worry about concurrent access to that resource.

It's your responsibility as a developer to ensure that a handler cannot occupy its assigned thread for too long.
If you block a thread for too long, Vert.x will log a warning.
The Vert.x developers took at it as their responsibility to ensure that no Vert.x API call will block a thread.
As a result, a well-designed Vert.x application can handle a large amount of events using only a few threads,
ultimately making such an application *responsive*.

