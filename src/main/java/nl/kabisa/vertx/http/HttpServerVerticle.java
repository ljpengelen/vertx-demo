package nl.kabisa.vertx.http;

import io.vertx.core.Promise;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import nl.kabisa.vertx.tcp.TcpClientVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

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
        var promise = Promise.<String>promise();

        vertx.eventBus().request(TcpClientVerticle.REQUEST_ADDRESS, requestObject).andThen(reply -> {
            if (reply.succeeded()) {
                promise.complete(reply.result().body().toString());
            } else {
                var cause = reply.cause();
                LOGGER.error("Unable to receive response from TCP client", cause);
                promise.fail(cause);
            }
        });

        return promise.future();
    }

    private void handleRequest(HttpServerRequest request) {
        LOGGER.info("Incoming request for path: {}", request.path());

        request.bodyHandler(buffer -> {
            var requestObject = requestObject(buffer);

            forwardRequest(requestObject).andThen(asyncResponse -> {
                if (asyncResponse.succeeded()) {
                    request.response().end(asyncResponse.result());
                } else {
                    request.response().setStatusCode(((ReplyException) asyncResponse.cause()).failureCode()).end();
                }
            });
        });
    }

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("Starting");

        var options = new HttpServerOptions().setPort(8080);
        var server = vertx.createHttpServer(options);

        server.requestHandler(this::handleRequest);

        server.listen(ar -> {
            if (ar.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(ar.cause());
            }
        });
    }
}
