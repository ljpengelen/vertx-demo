package nl.kabisa.vertx.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
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
                var requestBuffer = buffer;
                if (buffer.length() == 0) {
                    requestBuffer = Buffer.buffer("Hello world!");
                }
                vertx.eventBus().send(TcpClientVerticle.REQUEST_ADDRESS, requestBuffer, reply -> {
                    if (reply.succeeded()) {
                        request.response().end(reply.result().body().toString());
                    } else {
                        LOGGER.error("Unable to receive response from TCP client", reply.cause());
                        request.response().setStatusCode(500).end();
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
