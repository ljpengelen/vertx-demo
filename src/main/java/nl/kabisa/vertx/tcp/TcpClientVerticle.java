package nl.kabisa.vertx.tcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.net.NetClient;

public class TcpClientVerticle extends AbstractVerticle {

    public static final String REQUEST_ADDRESS = "tcp.client.request";

    private static final Logger LOGGER = LogManager.getLogger();

    private EventBus eventBus;
    private NetClient authClient;
    private NetClient echoClient;

    private void handleEvent(Message<Buffer> event) {
        authClient.connect(3001, "localhost", asyncAuthSocket -> {
            if (asyncAuthSocket.succeeded()) {
                var authSocket = asyncAuthSocket.result();
                authSocket.handler(authBuffer -> {
                    if (authBuffer.getByte(0) == 0) {
                        event.fail(0, "Invalid credentials");
                    } else if (authBuffer.getByte(0) == 2) {
                        event.fail(0, "Unexpected error");
                    } else if (authBuffer.getByte(0) == 1) {
                        var id = authBuffer.getBytes(1, authBuffer.length());

                        echoClient.connect(3002, "localhost", asyncEchoSocket -> {
                            if (asyncEchoSocket.succeeded()) {
                                var echoSocket = asyncEchoSocket.result();
                                echoSocket.handler(echoBuffer -> {
                                    if (echoBuffer.getByte(0) == 0) {
                                        event.fail(500, "Unauthenticated");
                                    } else if (echoBuffer.getByte(0) == 1) {
                                        event.reply(echoBuffer.getBuffer(1, echoBuffer.length()));
                                    } else {
                                        event.fail(500, "Unexpected response from echo service");
                                    }
                                });
                                echoSocket.write(Buffer.buffer(Bytes.concat(id, event.body().getBytes())));
                            } else {
                                String errorMessage = "Unable to obtain socket for echo service";
                                LOGGER.error(errorMessage, asyncEchoSocket.cause());
                                event.fail(500, errorMessage);
                            }
                        });
                    } else {
                        event.fail(500, "Unexpected response from authentication service");
                    }
                });
                authSocket.write(Buffer.buffer(new byte[] { 1, 2, 3, 4 }));
            } else {
                String errorMessage = "Unable to obtain socket for authentication service";
                LOGGER.error(errorMessage, asyncAuthSocket.cause());
                event.fail(500, errorMessage);
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
