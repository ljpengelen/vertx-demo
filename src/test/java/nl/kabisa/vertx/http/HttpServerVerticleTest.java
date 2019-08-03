package nl.kabisa.vertx.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HttpServerVerticleTest {

    private final HttpServerVerticle httpServerVerticle = new HttpServerVerticle();

    private WebClient webClient;

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        webClient = WebClient.create(vertx);

        vertx.deployVerticle(httpServerVerticle, vertxTestContext.completing());
    }

    @Test
    @DisplayName("Responds with failure code as status code in case of errors")
    public void respondsWith500GivenError(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.eventBus().consumer("tcp.client.request", reply -> {
            vertxTestContext.checkpoint();
            reply.fail(1234, "Something went wrong");
        });

        webClient.post(8080, "localhost", "/").send(ar -> {
            vertxTestContext.verify(() -> assertThat(ar.result().statusCode()).isEqualTo(1234));
            vertxTestContext.completeNow();
        });
    }

    @Test
    @DisplayName("Responds with body of reply from TCP client")
    public void respondsWithBodyOfReply(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.eventBus().consumer("tcp.client.request", reply -> {
            vertxTestContext.checkpoint();
            reply.reply("👍");
        });

        webClient.post(8080, "localhost", "/").send(ar -> {
            vertxTestContext.verify(() -> {
                assertThat(ar.result().statusCode()).isEqualTo(200);
                assertThat(ar.result().bodyAsString()).isEqualTo("👍");
            });
            vertxTestContext.completeNow();
        });
    }
}
