package nl.kabisa.vertx.tcp;

import java.util.Arrays;
import java.util.UUID;

import io.vertx.core.Promise;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthServiceVerticle extends AbstractVerticle {

    public static final String AUTHENTICATED_CLIENTS_MAP = "AUTHENTICATED_CLIENTS_MAP";

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceVerticle.class);

    private static final byte[] SECRET_PASSWORD = { 1, 2, 3, 4 };
    private static final byte[] OK = new byte[] { 1 };
    private static final Buffer NOK = Buffer.buffer(new byte[] { 0 });
    private static final Buffer FAIL = Buffer.buffer(new byte[] { 2 });

    private String nextId() {
        return UUID.randomUUID().toString();
    }

    private Future<String> generateToken() {
        var promise = Promise.<String>promise();

        vertx.sharedData().<String, Boolean> getAsyncMap(AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
            if (asyncMap.succeeded()) {
                var map = asyncMap.result();
                var id = nextId();
                map.put(id, true, putResult -> {
                    if (putResult.succeeded()) {
                        promise.complete(id);
                    } else {
                        LOGGER.error("Failed to store ({}, {}}) in map {}", id, true, AUTHENTICATED_CLIENTS_MAP, putResult.cause());
                        promise.fail(putResult.cause());
                    }
                });
            } else {
                LOGGER.error("Failed to get async map {}", AUTHENTICATED_CLIENTS_MAP, asyncMap.cause());
                promise.fail(asyncMap.cause());
            }
        });

        return promise.future();
    }

    private void handleRequest(NetSocket socket, Buffer buffer) {
        LOGGER.info("Received buffer: {}", buffer);

        if (buffer.length() >= 4 && Arrays.equals(buffer.getBytes(0, 4), SECRET_PASSWORD)) {
            generateToken().andThen(asyncToken -> {
                if (asyncToken.succeeded()) {
                    socket.write(Buffer.buffer(Bytes.concat(OK, asyncToken.result().getBytes())));
                } else {
                    socket.write(FAIL);
                }
            });
        } else {
            socket.write(NOK);
        }
    }

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("Starting");

        var options = new NetServerOptions().setPort(3001);
        var netServer = vertx.createNetServer(options);

        netServer.connectHandler(socket ->
                socket.handler(buffer ->
                        handleRequest(socket, buffer)));

        netServer.listen(ar -> {
            if (ar.succeeded()) {
                LOGGER.debug("Listening for connections on port {}", netServer.actualPort());
                startPromise.complete();
            } else {
                LOGGER.error("Failed to listen for connections", ar.cause());
                startPromise.fail(ar.cause());
            }
        });
    }
}
