package nl.kabisa.vertx.tcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.primitives.Bytes;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TcpClientVerticleTest {

    private static final String REQUEST_ADDRESS = "tcp.client.request";
    private static final JsonObject INPUT_OBJECT = new JsonObject().put("body", "input");

    private final TcpClientVerticle tcpClientVerticle = new TcpClientVerticle();

    private NetServer authService;
    private NetServer echoService;

    @BeforeEach
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        authService = vertx.createNetServer();
        echoService = vertx.createNetServer();

        vertx.deployVerticle(tcpClientVerticle, vertxTestContext.completing());
    }

    @Test
    @DisplayName("Authenticates with authentication service")
    public void authenticatesWithAuthService(Vertx vertx, VertxTestContext vertxTestContext) {
        authService.connectHandler(socket ->
                socket.handler(buffer -> {
                    vertxTestContext.completeNow();
                }));
        authService.listen(3001, "localhost");

        vertx.eventBus().send(REQUEST_ADDRESS, INPUT_OBJECT);
    }

    @Test
    @DisplayName("Replies with failure if authentication fails")
    public void repliesWithFailure(Vertx vertx, VertxTestContext vertxTestContext) {
        authService.connectHandler(socket ->
                socket.handler(buffer -> {
                    vertxTestContext.checkpoint();
                    socket.write(Buffer.buffer(new byte[] { 0 }));
                }));
        authService.listen(3001, "localhost");

        vertx.eventBus().send(REQUEST_ADDRESS, INPUT_OBJECT, reply -> {
            vertxTestContext.verify(() -> assertThat(reply.failed()).isTrue());
            vertxTestContext.completeNow();
        });
    }

    @Test
    @DisplayName("Forwards to echo service")
    public void forwardsToEchoService(Vertx vertx, VertxTestContext vertxTestContext) {
        authService.connectHandler(socket ->
                socket.handler(buffer -> {
                    vertxTestContext.checkpoint();
                    socket.write(Buffer.buffer(new byte[] { 1, 0 }));
                }));
        authService.listen(3001, "localhost");

        echoService.connectHandler(socket ->
                socket.handler(buffer -> {
                    vertxTestContext.verify(() -> assertThat(buffer.getString(1, buffer.length())).isEqualTo("input"));
                    vertxTestContext.completeNow();
                }));
        echoService.listen(3002, "localhost");

        vertx.eventBus().send(REQUEST_ADDRESS, INPUT_OBJECT);
    }

    @Test
    @DisplayName("Returns result of echo service")
    public void returnsResultOfEchoService(Vertx vertx, VertxTestContext vertxTestContext) {
        authService.connectHandler(socket ->
                socket.handler(buffer -> {
                    vertxTestContext.checkpoint();
                    socket.write(Buffer.buffer(new byte[] { 1, 0 }));
                }));
        authService.listen(3001, "localhost");

        echoService.connectHandler(socket ->
                socket.handler(buffer -> {
                    vertxTestContext.checkpoint();
                    socket.write(Buffer.buffer(Bytes.concat(new byte[] { 1 }, "output".getBytes())));
                }));
        echoService.listen(3002, "localhost");

        vertx.eventBus().send(REQUEST_ADDRESS, INPUT_OBJECT, reply -> {
            vertxTestContext.verify(() -> {
                assertThat(reply.succeeded()).isTrue();
                assertThat(reply.result().body()).isEqualTo(Buffer.buffer("output"));
            });
            vertxTestContext.completeNow();
        });
    }
}
