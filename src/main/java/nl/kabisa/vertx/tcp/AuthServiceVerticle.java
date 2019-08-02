package nl.kabisa.vertx.tcp;

import java.util.Arrays;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServerOptions;

public class AuthServiceVerticle extends AbstractVerticle {

    public static final String AUTHENTICATED_CLIENTS_MAP = "AUTHENTICATED_CLIENTS_MAP";

    private static final Logger LOGGER = LogManager.getLogger();

    private static final byte[] SECRET_PASSWORD = { 1, 2, 3, 4 };
    private static final byte[] OK = new byte[] { 1 };
    private static final Buffer NOK = Buffer.buffer(new byte[] { 0 });
    private static final Buffer FAIL = Buffer.buffer(new byte[] { 2 });

    private String nextId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public void start(Future<Void> startFuture) {
        LOGGER.info("Starting");

        var options = new NetServerOptions().setPort(3001);
        var netServer = vertx.createNetServer(options);

        netServer.connectHandler(socket ->
                socket.handler(buffer -> {
                    LOGGER.info("Received buffer: {}", buffer);

                    if (Arrays.equals(buffer.getBytes(0, 4), SECRET_PASSWORD)) {
                        vertx.sharedData().<String, Boolean> getAsyncMap(AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
                            if (asyncMap.succeeded()) {
                                var map = asyncMap.result();
                                var id = nextId();
                                map.put(id, true, putResult -> {
                                    if (putResult.succeeded()) {
                                        socket.write(Buffer.buffer(Bytes.concat(OK, id.getBytes())));
                                    } else {
                                        LOGGER.error("Failed to store ({}, {}}) in map {}", id, true, AUTHENTICATED_CLIENTS_MAP, putResult.cause());
                                        socket.write(FAIL);
                                    }
                                });
                            } else {
                                LOGGER.error("Failed to get async map {}", AUTHENTICATED_CLIENTS_MAP, asyncMap.cause());
                                socket.write(FAIL);
                            }
                        });
                    } else {
                        socket.write(NOK);
                    }
                }));

        netServer.listen(ar -> {
            if (ar.succeeded()) {
                LOGGER.debug("Listening for connections on port {}", netServer.actualPort());
                startFuture.complete();
            } else {
                LOGGER.error("Failed to listen for connections", ar.cause());
                startFuture.fail(ar.cause());
            }
        });
    }
}
