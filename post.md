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

By default, Vert.x creates two threads per CPU core to deploy verticles like the one above.
Each verticle is assigned to a specific thread, and all handlers of that verticle are executed on that thread sequentially.
For the example above, this means that the handler `request -> request.response().end("Hello world")` is always executed on the same thread.
 
Because the handlers for a given verticle are never executed concurrently, you don't have to worry about locking or the atomicity of actions relevant for a single verticle.
Multiple instances of the same verticle, however, *can* have their handlers executed at the same time.
In fact, this holds for any two verticles.
This means that if two verticles share a resource, you might still have to worry about concurrent access to that resource.

It's your responsibility as a developer to ensure that a handler cannot occupy its assigned thread for too long.
If you block a thread for too long, Vert.x will log a warning.
The Vert.x developers took at it as their responsibility to ensure that no Vert.x API call will block a thread.
As a result, a well-designed Vert.x application can handle a large amount of events using only a few threads,
ultimately making such an application *responsive*.

## Message driven and resilient

The example below shows an application consisting of two verticles.
It illustrates Vert.x's event bus.
The event bus allows you to broadcast messages to any number of interested receivers as well as send messages to a single receiver.
The broadcasted messages end up at each of the receivers registered for an address,
whereas the messages sent directly end up at a single receiver.

In the example below, instances of the `WorldVerticle` are registered as consumers on the address `WORLD`.
Instances of the `HelloVerticle` send messages to this address.
If we would deploy multiple `WordVerticles`, each of them would receive messages in turn.

It's possible to send messages in a number of different forms, including strings, booleans, JSON objects, and JSON arrays.
Vert.x best-effort delivery, which means that message can get lost, but are never thrown away intentionally. 

```java
package nl.kabisa.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;

public class Application {

    private static class HelloVerticle extends AbstractVerticle {

        @Override
        public void start() {
            var options = new HttpServerOptions().setPort(8080);
            vertx.createHttpServer(options)
                    .requestHandler(request ->
                            vertx.eventBus().send("WORLD", "Hello", ar -> {
                                if (ar.succeeded()) {
                                    request.response().end((String) ar.result().body());
                                } else {
                                    request.response().setStatusCode(500).end(ar.cause().getMessage());
                                }
                            }))
                    .listen();
        }
    }

    private static class WorldVerticle extends AbstractVerticle {

        @Override
        public void start() {
            vertx.eventBus().consumer("WORLD", event -> event.reply(event.body() + " world"));
        }
    }

    public static void main(String[] args) {
        var vertx = Vertx.vertx();
        vertx.deployVerticle(new WorldVerticle());
        vertx.deployVerticle(new HelloVerticle());
    }
}
```

The example shows that the sender of a message can specify an optional reply handler.
The reply is provided to the handler in the form of an asynchronous result, which can either be succeeded or failed.
If it succeeded, the actual reply message is available (`ar.result()`, as shown in the example).
Otherwise, a throwable is available that indicates what went wrong (`ar.cause()`, also shown in the example).

I probably don't need to tell you that this covers the *message driven* part of the Reactive Manifesto.
Clearly, verticles can communicate via asynchronous message passing.

In a way, the example also illustrates *resilience*.
If we would deploy multiple `WorldVerticles` and one of them would fail, the others would just keep on doing their jobs on their own thread.
Additionally, the example shows how Vert.x reminds you to think about gracefully handling failure when implementing a handler.
Many handlers receive their input in the form of an asynchronous result, which can always be succeeded or failed, as discussed above.
Finally, and perhaps paradoxically, because of the best-effort delivery of messages via the event bus, you're also forced to consciously deal with failure related to lost messages.
If it's paramount that a given type of message is always processed, you need to implement acknowledgements and retries.

## Elasticity

As mentioned above, Vert.x creates two threads per available CPU core to deploy verticles like the ones shown above.
If you need to handle more events (such as HTTP requests, for example), you can run your app on a machine with more CPU cores and reap the benefits of more concurrency, without any additional programming or configuration changes.
Additionally, it's possible to scale individual components of your application by simply deploying more or fewer verticles of a certain type.
That sounds pretty elastic to me.

## Let's go overboard 🚢

If you have experience with callback-based asynchronous programming, you've probably also heard of callback hell.
Callback hell is the term used to describe the type of programs that slowly but surely move to the right-hand side of your screen,
where you're dealing with callbacks inside callbacks, inside callbacks, inside callbacks, etc.

Take the following TCP client for example:

```java
package nl.kabisa.vertx.tcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;

public class TcpClientVerticle extends AbstractVerticle {

    public static final String REQUEST_ADDRESS = "tcp.client.request";

    private static final Logger LOGGER = LogManager.getLogger();

    private EventBus eventBus;
    private NetClient authClient;
    private NetClient echoClient;

    private void handleEvent(Message<JsonObject> event) {
        authClient.connect(3001, "localhost", asyncAuthSocket -> {
            if (asyncAuthSocket.succeeded()) {
                var authSocket = asyncAuthSocket.result();
                authSocket.handler(authBuffer -> {
                    if (authBuffer.getByte(0) == 0) {
                        event.fail(0, "Invalid credentials");
                    } else if (authBuffer.getByte(0) == 2) {
                        event.fail(0, "Unexpected error");
                    } else if (authBuffer.getByte(0) == 1) {
                        var id = authBuffer.getBytes(1, authBuffer.length());

                        echoClient.connect(3002, "localhost", asyncEchoSocket -> {
                            if (asyncEchoSocket.succeeded()) {
                                var echoSocket = asyncEchoSocket.result();
                                echoSocket.handler(echoBuffer -> {
                                    if (echoBuffer.getByte(0) == 0) {
                                        event.fail(500, "Unauthenticated");
                                    } else if (echoBuffer.getByte(0) == 1) {
                                        event.reply(echoBuffer.getBuffer(1, echoBuffer.length()));
                                    } else {
                                        event.fail(500, "Unexpected response from echo service");
                                    }
                                });
                                echoSocket.write(Buffer.buffer(Bytes.concat(id, event.body().getString("body").getBytes())));
                            } else {
                                String errorMessage = "Unable to obtain socket for echo service";
                                LOGGER.error(errorMessage, asyncEchoSocket.cause());
                                event.fail(500, errorMessage);
                            }
                        });
                    } else {
                        event.fail(500, "Unexpected response from authentication service");
                    }
                });
                authSocket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));
            } else {
                String errorMessage = "Unable to obtain socket for authentication service";
                LOGGER.error(errorMessage, asyncAuthSocket.cause());
                event.fail(500, errorMessage);
            }
        });
    }

    @Override
    public void start() {
        LOGGER.info("Starting");

        eventBus = vertx.eventBus();
        authClient = vertx.createNetClient();
        echoClient = vertx.createNetClient();

        eventBus.consumer(REQUEST_ADDRESS, this::handleEvent);
    }
}
```

This verticle listens for messages on the address `tcp.client.request`.
Each time a message arrives, the verticle authenticates itself with some service listening on port 3001 by exchanging some bytes.
It uses the token it receives to communicate with some other service listening on port 3002.
In the end, it replies to the initial message with a buffer containing an array of bytes received from the service listening on port 3002.
You could argue that this isn't the most beautiful piece of code ever written, although beauty lies in the eyes of the beholder. 

(If you want to see the callback-based implementation of the rest of this application, by my guest: [https://github.com/ljpengelen/vertx-demo/tree/971e33e4475a18fb7239d716a8c6d05369442b8a](https://github.com/ljpengelen/vertx-demo/tree/971e33e4475a18fb7239d716a8c6d05369442b8a).)

## Futures

JavaScript's answer to callback hell were *promises*.
Vert.x's answer to callback hell are *futures*.
A future represents the result of some computation that is potentially available at some later stage.
A future can either succeed or fail.
When it succeed, its result will be available.
When it fails, a throwable representing the cause of failure will be available.
You can set a handler for a future, which will be called with the asynchronous result when the future has succeeded or failed.
There are different ways to combine futures into a single future, which we'll illustrate with an example.

Suppose you want to deploy a number of verticles, and some of these verticles should only be deployed once others have been deployed successfully.
Vert.x offers a deploy method with a callback, which is called when the deployment has finished.
Without the use of futures, you could end up with code like this:

```java
package nl.kabisa.vertx;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import nl.kabisa.vertx.http.HttpServerVerticle;
import nl.kabisa.vertx.tcp.*;

public class Application {

    private static final Logger LOGGER = LogManager.getLogger();

    private static Vertx vertx;

    public static void main(String[] args) {
        vertx = Vertx.vertx();

        vertx.deployVerticle(new AuthServiceVerticle(), authServiceDeployment -> {
            if (authServiceDeployment.succeeded()) {
                vertx.deployVerticle(new ScreamingEchoServiceVerticle(), ar -> {
                    if (ar.succeeded()) {
                        vertx.deployVerticle(new TcpClientVerticle(), tcpClientDeployment -> {
                            if (tcpClientDeployment.succeeded()) {
                                vertx.deployVerticle(new HttpServerVerticle(), httpServerDeployment ->
                                    LOGGER.info("All verticles started successfully"));
                            }
                        });
                    }
                });
            }
        });
    }
}
```

This isn't pretty at all, even without the additional code you need to deal with possible failures.
Also, we're deploying the verticles one at a time, where we actually only want to deploy the `HttpServerVerticle` once the others have been deployed successfully.

Rewriting this example using futures leads to the following:

```java
package nl.kabisa.vertx;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.*;
import nl.kabisa.vertx.http.HttpServerVerticle;
import nl.kabisa.vertx.tcp.*;

public class Application {

    private static final Logger LOGGER = LogManager.getLogger();

    private static Vertx vertx;

    private static Future<String> deploy(Vertx vertx, Verticle verticle) {
        Future<String> future = Future.future();
        vertx.deployVerticle(verticle, future);
        return future;
    }

    public static void main(String[] args) {
        LOGGER.info("Starting");

        vertx = Vertx.vertx();

        CompositeFuture.all(
                deploy(vertx, new AuthServiceVerticle()),
                deploy(vertx, new ScreamingEchoServiceVerticle()),
                deploy(vertx, new TcpClientVerticle()))
                .compose(s -> deploy(vertx, new HttpServerVerticle()))
                .setHandler(s -> {
                            if (s.succeeded()) {
                                LOGGER.info("All verticles started successfully");
                            } else {
                                LOGGER.error("Failed to deploy all verticles", s.cause());
                            }
                        }
                );
    }
}
```

Here, we deploy three verticles at the same time, and deploy the last one when the deployment of all the others succeeded.
Again, beauty lies in the eyes of the beholder, but this is good enough for me.

Do you still remember the TCP client you saw above?
Here's the same client implemented using futures:

```java
package nl.kabisa.vertx.tcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

public class TcpClientVerticle extends AbstractVerticle {

    public static final String REQUEST_ADDRESS = "tcp.client.request";

    private static final Logger LOGGER = LogManager.getLogger();

    private EventBus eventBus;
    private NetClient authClient;
    private NetClient echoClient;

    private Future<NetSocket> connectToAuthService() {
        Future<NetSocket> future = Future.future();

        authClient.connect(3001, "localhost", future);

        return future;
    }

    private Future<Buffer> authenticate(NetSocket authSocket) {
        Future<Buffer> future = Future.future();

        authSocket.handler(authBuffer -> {
            if (authBuffer.getByte(0) == 0) {
                future.fail("Invalid credentials");
            } else if (authBuffer.getByte(0) == 2) {
                future.fail("Unexpected error");
            } else if (authBuffer.getByte(0) == 1) {
                future.complete(authBuffer.getBuffer(1, authBuffer.length()));
            } else {
                future.fail("Unexpected response from authentication service");
            }
        });

        authSocket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));

        return future;
    }

    private Future<NetSocket> connectToEchoClient() {
        Future<NetSocket> future = Future.future();

        echoClient.connect(3002, "localhost", future);

        return future;
    }

    private Future<Buffer> forwardToEchoClient(NetSocket echoSocket, Buffer token, String input) {
        Future<Buffer> future = Future.future();

        echoSocket.handler(echoBuffer -> {
            if (echoBuffer.getByte(0) == 0) {
                future.fail("Unauthenticated");
            } else if (echoBuffer.getByte(0) == 1) {
                future.complete(echoBuffer.getBuffer(1, echoBuffer.length()));
            } else {
                future.fail("Unexpected response from echo service");
            }
        });
        echoSocket.write(Buffer.buffer(Bytes.concat(token.getBytes(), input.getBytes())));

        return future;
    }

    private void handleEvent(Message<JsonObject> event) {
        connectToAuthService()
                .compose(this::authenticate)
                .compose(token -> connectToEchoClient()
                        .compose(socket -> forwardToEchoClient(socket, token, event.body().getString("body"))))
                .setHandler(asyncBuffer -> {
                    if (asyncBuffer.succeeded()) {
                        event.reply(asyncBuffer.result());
                    } else {
                        event.fail(500, asyncBuffer.cause().getMessage());
                    }
                });
    }

    @Override
    public void start() {
        LOGGER.info("Starting");

        eventBus = vertx.eventBus();
        authClient = vertx.createNetClient();
        echoClient = vertx.createNetClient();

        eventBus.consumer(REQUEST_ADDRESS, this::handleEvent);
    }
}
```

Although I still have to look closely to see what the `handleEvent` method is doing exactly,
I hope we can agree that this is an improvement over the callback-based implementation.
In my opinion, it's clearer what each part of the implementation is responsible for and which parts are related.

## Conclusion

Once you get the hang of it, developing applications with Vert.x is quite enjoyable.
As with all forms of asynchronous programming, however, I sometimes find myself in slightly annoying situations where a synchronous approach would be much easier to implement and reason about.
The question is whether you're willing to put up with a little extra work to enjoy the potential benefits of reactive systems.

If you're interested in the toolkit, you should definitely play around with the example application available at [https://github.com/ljpengelen/vertx-demo/](https://github.com/ljpengelen/vertx-demo/).
Besides a few other verticles apart from those described here,
there are a number of tests that should give you an impression of what Vert.x has to offer.
