package nl.kabisa.vertx;

import io.vertx.core.*;
import nl.kabisa.vertx.http.HttpServerVerticle;
import nl.kabisa.vertx.tcp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private static Vertx vertx;

    public static void main(String[] args) {
        LOGGER.info("Starting");

        vertx = Vertx.vertx();

        CompositeFuture.all(vertx.deployVerticle(new AuthServiceVerticle()),
                        vertx.deployVerticle(new ScreamingEchoServiceVerticle()),
                        vertx.deployVerticle(new TcpClientVerticle()))
                .compose(s -> vertx.deployVerticle(new HttpServerVerticle()))
                .andThen(s -> {
                    if (s.succeeded()) {
                        LOGGER.info("All verticles started successfully");
                    } else {
                        LOGGER.error("Failed to deploy all verticles", s.cause());
                    }
                });
    }
}
