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
class AuthServiceVerticleTest {

    private static final String AUTHENTICATED_CLIENTS_MAP = "AUTHENTICATED_CLIENTS_MAP";
    private static final int PORT = 3001;

    private final AuthServiceVerticle authServiceVerticle = new AuthServiceVerticle();

    private NetClient netClient;

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        netClient = vertx.createNetClient();

        vertx.deployVerticle(authServiceVerticle, vertxTestContext.completing());
    }

    @Test
    @DisplayName("Returns OK given valid password")
    public void returnsOkGivenValidPassword(Vertx vertx, VertxTestContext vertxTestContext) {
        netClient.connect(PORT, "localhost", asyncSocket -> {
            vertxTestContext.verify(() -> assertThat(asyncSocket.succeeded()).isTrue());
            var socket = asyncSocket.result();
            socket.handler(buffer -> {
                vertxTestContext.verify(() -> assertThat(buffer.getByte(0)).isEqualTo((byte) 1));
                vertxTestContext.completeNow();
            });
            socket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));
        });
    }

    @Test
    @DisplayName("Returns NOK given invalid password")
    public void returnsNokGivenInvalidPassword(Vertx vertx, VertxTestContext vertxTestContext) {
        netClient.connect(PORT, "localhost", asyncSocket -> {
            vertxTestContext.verify(() -> assertThat(asyncSocket.succeeded()).isTrue());
            var socket = asyncSocket.result();
            socket.handler(buffer -> {
                vertxTestContext.verify(() -> assertThat(buffer.getByte(0)).isEqualTo((byte) 0));
                vertxTestContext.completeNow();
            });
            socket.write(Buffer.buffer(new byte[] { 1, 2, 3 }));
        });
    }

    @Test
    @DisplayName("Returns authenticated identifier")
    public void returnsAuthenticatedIdentifier(Vertx vertx, VertxTestContext vertxTestContext) {
        vertx.sharedData().getAsyncMap(AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
            vertxTestContext.verify(() -> assertThat(asyncMap.succeeded()).isTrue());
            var map = asyncMap.result();
            map.size(asyncSize ->
                    vertxTestContext.verify(() -> {
                        assertThat(asyncSize.succeeded()).isTrue();
                        assertThat(asyncSize.result()).isZero();
                    }));
        });
        netClient.connect(PORT, "localhost", asyncSocket -> {
            vertxTestContext.verify(() -> assertThat(asyncSocket.succeeded()).isTrue());
            var socket = asyncSocket.result();
            socket.handler(buffer -> {
                vertxTestContext.verify(() -> assertThat(buffer.getByte(0)).isEqualTo((byte) 1));

                var id = buffer.getString(1, buffer.length());
                vertx.sharedData().<String, Boolean> getAsyncMap(AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
                    vertxTestContext.verify(() -> assertThat(asyncMap.succeeded()).isTrue());
                    var map = asyncMap.result();
                    map.get(id, asyncResult -> {
                        vertxTestContext.verify(() -> {
                            assertThat(asyncResult.succeeded()).isTrue();
                            assertThat(asyncResult.result()).isTrue();
                        });
                        vertxTestContext.completeNow();
                    });
                });
            });
            socket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));
        });
    }
}
