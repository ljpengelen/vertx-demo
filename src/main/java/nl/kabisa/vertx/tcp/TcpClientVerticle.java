package nl.kabisa.vertx.tcp;

import io.vertx.core.Promise;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClientVerticle extends AbstractVerticle {

    public static final String REQUEST_ADDRESS = "tcp.client.request";

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpClientVerticle.class);

    private EventBus eventBus;
    private NetClient authClient;
    private NetClient echoClient;

    private Future<NetSocket> connectToAuthService() {
        var promise = Promise.<NetSocket>promise();

        authClient.connect(3001, "localhost", promise);

        return promise.future();
    }

    private Future<Buffer> authenticate(NetSocket authSocket) {
        var promise = Promise.<Buffer>promise();

        authSocket.handler(authBuffer -> {
            if (authBuffer.getByte(0) == 0) {
                promise.fail("Invalid credentials");
            } else if (authBuffer.getByte(0) == 2) {
                promise.fail("Unexpected error");
            } else if (authBuffer.getByte(0) == 1) {
                promise.complete(authBuffer.getBuffer(1, authBuffer.length()));
            } else {
                promise.fail("Unexpected response from authentication service");
            }
        });

        authSocket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));

        return promise.future();
    }

    private Future<NetSocket> connectToEchoClient() {
        var promise = Promise.<NetSocket>promise();

        echoClient.connect(3002, "localhost", promise);

        return promise.future();
    }

    private Future<Buffer> forwardToEchoClient(NetSocket echoSocket, Buffer token, String input) {
        var promise = Promise.<Buffer>promise();

        echoSocket.handler(echoBuffer -> {
            if (echoBuffer.getByte(0) == 0) {
                promise.fail("Unauthenticated");
            } else if (echoBuffer.getByte(0) == 1) {
                promise.complete(echoBuffer.getBuffer(1, echoBuffer.length()));
            } else {
                promise.fail("Unexpected response from echo service");
            }
        });
        echoSocket.write(Buffer.buffer(Bytes.concat(token.getBytes(), input.getBytes())));

        return promise.future();
    }

    private void handleEvent(Message<JsonObject> event) {
        connectToAuthService()
                .compose(this::authenticate)
                .compose(token -> connectToEchoClient()
                        .compose(socket -> forwardToEchoClient(socket, token, event.body().getString("body"))))
                .andThen(asyncBuffer -> {
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
