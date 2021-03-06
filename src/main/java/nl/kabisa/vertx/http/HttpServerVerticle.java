package nl.kabisa.vertx.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import nl.kabisa.vertx.tcp.TcpClientVerticle;

public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger();

    private JsonObject requestObject(Buffer buffer) {
        var requestObject = new JsonObject();
        if (buffer.length() == 0) {
            requestObject.put("body", "Hello world!");
        } else {
            requestObject.put("body", buffer.toString());
        }
        return requestObject;
    }

    private Future<String> forwardRequest(JsonObject requestObject) {
        Future<String> future = Future.future();

        vertx.eventBus().send(TcpClientVerticle.REQUEST_ADDRESS, requestObject, reply -> {
            if (reply.succeeded()) {
                future.complete(reply.result().body().toString());
            } else {
                var cause = reply.cause();
                LOGGER.error("Unable to receive response from TCP client", cause);
                future.fail(cause);
            }
        });

        return future;
    }

    private void handleRequest(HttpServerRequest request) {
        LOGGER.info("Incoming request for path: {}", request.path());

        request.bodyHandler(buffer -> {
            var requestObject = requestObject(buffer);

            forwardRequest(requestObject).setHandler(asyncResponse -> {
                if (asyncResponse.succeeded()) {
                    request.response().end(asyncResponse.result());
                } else {
                    request.response().setStatusCode(((ReplyException) asyncResponse.cause()).failureCode()).end();
                }
            });
        });
    }

    @Override
    public void start(Future<Void> startFuture) {
        LOGGER.info("Starting");

        var options = new HttpServerOptions().setPort(8080);
        var server = vertx.createHttpServer(options);

        server.requestHandler(this::handleRequest);

        server.listen(ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}
