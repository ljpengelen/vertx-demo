package nl.kabisa.vertx.tcp;

import static nl.kabisa.vertx.tcp.AuthServiceVerticle.AUTHENTICATED_CLIENTS_MAP;

import io.vertx.core.Promise;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScreamingEchoServiceVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScreamingEchoServiceVerticle.class);

    private static final Buffer NOK = Buffer.buffer(new byte[] { 0 });
    private static final byte[] OK = { 1 };
    private static final Buffer FAILURE = Buffer.buffer(new byte[] { 0 });

    private Future<Boolean> validateToken(String token) {
        var promise = Promise.<Boolean>promise();

        vertx.sharedData().<String, Boolean> getAsyncMap(AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
            if (asyncMap.succeeded()) {
                var map = asyncMap.result();
                map.get(token, asyncAuthenticated -> {
                    if (asyncAuthenticated.succeeded()) {
                        promise.complete(asyncAuthenticated.result());
                    } else {
                        LOGGER.error("Unable to get value for {} from map {}", token, AUTHENTICATED_CLIENTS_MAP, asyncAuthenticated.cause());
                        promise.fail(asyncAuthenticated.cause());
                    }
                });
            } else {
                LOGGER.error("Unable to get map {}", AUTHENTICATED_CLIENTS_MAP, asyncMap.cause());
                promise.fail(asyncMap.cause());
            }
        });

        return promise.future();
    }

    private void handleRequest(NetSocket socket, Buffer buffer) {
        LOGGER.info("Received buffer: {}", buffer);

        var id = buffer.getString(0, 36);
        validateToken(id).andThen(asyncValidationResult -> {
            if (asyncValidationResult.succeeded()) {
                var isValid = asyncValidationResult.result();
                if (Boolean.TRUE.equals(isValid)) {
                    socket.write(Buffer.buffer(Bytes.concat(OK, buffer.getString(36, buffer.length()).toUpperCase().getBytes())));
                } else {
                    socket.write(NOK);
                }
            } else {
                socket.write(FAILURE);
            }
        });
    }

    @Override
    public void start(Promise<Void> startPromise) {
        LOGGER.info("Starting");

        var options = new NetServerOptions().setPort(3002);
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
