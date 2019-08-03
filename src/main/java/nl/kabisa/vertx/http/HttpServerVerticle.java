package nl.kabisa.vertx.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import nl.kabisa.vertx.tcp.TcpClientVerticle;

public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void start(Future<Void> startFuture) {
        LOGGER.info("Starting");

        var options = new HttpServerOptions().setPort(8080);
        var server = vertx.createHttpServer(options);

        server.requestHandler(request -> {
            LOGGER.info("Incoming request for path: {}", request.path());

            request.bodyHandler(buffer -> {
                var requestObject = new JsonObject();
                if (buffer.length() == 0) {
                    requestObject.put("body", "Hello world!");
                } else {
                    requestObject.put("body", buffer.toString());
                }

                vertx.eventBus().send(TcpClientVerticle.REQUEST_ADDRESS, requestObject, reply -> {
                    if (reply.succeeded()) {
                        request.response().end(reply.result().body().toString());
                    } else {
                        var cause = (ReplyException) reply.cause();
                        LOGGER.error("Unable to receive response from TCP client", cause);
                        request.response().setStatusCode(cause.failureCode()).end();
                    }
                });
            });
        });

        server.listen(ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}
