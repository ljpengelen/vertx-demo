package nl.kabisa.vertx.tcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

public class TcpClientVerticle extends AbstractVerticle {

    public static final String REQUEST_ADDRESS = "tcp.client.request";

    private static final Logger LOGGER = LogManager.getLogger();

    private EventBus eventBus;
    private NetClient authClient;
    private NetClient echoClient;

    private Future<NetSocket> connectToAuthService() {
        Future<NetSocket> future = Future.future();

        authClient.connect(3001, "localhost", future);

        return future;
    }

    private Future<Buffer> authenticate(NetSocket authSocket) {
        Future<Buffer> future = Future.future();

        authSocket.handler(authBuffer -> {
            if (authBuffer.getByte(0) == 0) {
                future.fail("Invalid credentials");
            } else if (authBuffer.getByte(0) == 2) {
                future.fail("Unexpected error");
            } else if (authBuffer.getByte(0) == 1) {
                future.complete(authBuffer.getBuffer(1, authBuffer.length()));
            } else {
                future.fail("Unexpected response from authentication service");
            }
        });

        authSocket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));

        return future;
    }

    private Future<NetSocket> connectToEchoClient() {
        Future<NetSocket> future = Future.future();

        echoClient.connect(3002, "localhost", future);

        return future;
    }

    private Future<Buffer> forwardToEchoClient(NetSocket echoSocket, Buffer token, String input) {
        Future<Buffer> future = Future.future();

        echoSocket.handler(echoBuffer -> {
            if (echoBuffer.getByte(0) == 0) {
                future.fail("Unauthenticated");
            } else if (echoBuffer.getByte(0) == 1) {
                future.complete(echoBuffer.getBuffer(1, echoBuffer.length()));
            } else {
                future.fail("Unexpected response from echo service");
            }
        });
        echoSocket.write(Buffer.buffer(Bytes.concat(token.getBytes(), input.getBytes())));

        return future;
    }

    private void handleEvent(Message<JsonObject> event) {
        connectToAuthService()
                .compose(this::authenticate)
                .compose(token -> connectToEchoClient()
                        .compose(socket -> forwardToEchoClient(socket, token, event.body().getString("body"))))
                .setHandler(asyncBuffer -> {
                    if (asyncBuffer.succeeded()) {
                        event.reply(asyncBuffer.result());
                    } else {
                        event.fail(500, asyncBuffer.cause().getMessage());
                    }
                });
    }

    @Override
    public void start() {
        LOGGER.info("Starting");

        eventBus = vertx.eventBus();
        authClient = vertx.createNetClient();
        echoClient = vertx.createNetClient();

        eventBus.consumer(REQUEST_ADDRESS, this::handleEvent);
    }
}
