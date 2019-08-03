package nl.kabisa.vertx.tcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ScreamingEchoServiceVerticleTest {

    private static final String IDENTIFIER = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final ScreamingEchoServiceVerticle screamingEchoServiceVerticle = new ScreamingEchoServiceVerticle();

    private NetClient netClient;

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        netClient = vertx.createNetClient();

        vertx.deployVerticle(screamingEchoServiceVerticle, vertxTestContext.completing());
    }

    @Test
    @DisplayName("Returns NOK given unknown identifier")
    public void returnsNokGivenUnknownId(VertxTestContext vertxTestContext) {
        netClient.connect(3002, "localhost", asyncSocket -> {
            vertxTestContext.verify(() -> asyncSocket.succeeded());
            var socket = asyncSocket.result();
            socket.handler(buffer -> {
                vertxTestContext.verify(() -> {
                    assertThat(buffer.length()).isEqualTo(1);
                    assertThat(buffer.getByte(0)).isEqualTo((byte) 0);
                });
                vertxTestContext.completeNow();
            });
            socket.write(Buffer.buffer(IDENTIFIER + "input"));
        });
    }

    @Test
    @DisplayName("Returns OK given authorized identifier")
    public void returnsOkGivenAuthorizedIdentifier(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.sharedData().getAsyncMap(AuthServiceVerticle.AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
            vertxTestContext.verify(() -> assertThat(asyncMap.succeeded()).isTrue());
            var map = asyncMap.result();
            map.put(IDENTIFIER, true, asyncVoid -> vertxTestContext.verify(() -> assertThat(asyncVoid.succeeded()).isTrue()));
        });

        netClient.connect(3002, "localhost", asyncSocket -> {
            vertxTestContext.verify(() -> assertThat(asyncSocket.succeeded()).isTrue());
            var socket = asyncSocket.result();
            socket.handler(buffer -> {
                vertxTestContext.verify(() -> assertThat(buffer.getByte(0)).isEqualTo((byte) 1));
                vertxTestContext.completeNow();
            });
            socket.write(Buffer.buffer(IDENTIFIER + "input"));
        });
    }

    @Test
    @DisplayName("Echos input in all caps")
    public void echosInputInAllCaps(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.sharedData().getAsyncMap(AuthServiceVerticle.AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
            vertxTestContext.verify(() -> assertThat(asyncMap.succeeded()).isTrue());
            var map = asyncMap.result();
            map.put(IDENTIFIER, true, asyncVoid -> vertxTestContext.verify(() -> assertThat(asyncVoid.succeeded()).isTrue()));
        });
        netClient.connect(3002, "localhost", asyncSocket -> {
            vertxTestContext.verify(() -> assertThat(asyncSocket.succeeded()).isTrue());
            var socket = asyncSocket.result();
            socket.handler(buffer -> {
                vertxTestContext.verify(() -> assertThat(buffer.getString(1, buffer.length())).isEqualTo("INPUT"));
                vertxTestContext.completeNow();
            });
            socket.write(Buffer.buffer(IDENTIFIER + "input"));
        });
    }
}
