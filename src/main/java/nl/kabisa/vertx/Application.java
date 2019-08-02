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
                deploy(vertx, new TcpClientVerticle())).compose(
                s -> deploy(vertx, new HttpServerVerticle())).setHandler(
                s -> {
                    if (s.succeeded()) {
                        LOGGER.info("All verticles started successfully");
                    } else {
                        LOGGER.error("Failed to deploy all verticles", s.cause());
                    }
                }
        );
    }
}
