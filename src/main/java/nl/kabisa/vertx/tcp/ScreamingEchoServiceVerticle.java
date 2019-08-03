package nl.kabisa.vertx.tcp;

import static nl.kabisa.vertx.tcp.AuthServiceVerticle.AUTHENTICATED_CLIENTS_MAP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.primitives.Bytes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServerOptions;

public class ScreamingEchoServiceVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Buffer NOK = Buffer.buffer(new byte[] { 0 });
    private static final byte[] OK = { 1 };
    private static final Buffer FAILURE = Buffer.buffer(new byte[] { 0 });

    @Override
    public void start(Future<Void> startFuture) {
        LOGGER.info("Starting");

        var options = new NetServerOptions().setPort(3002);
        var netServer = vertx.createNetServer(options);

        netServer.connectHandler(socket ->
                socket.handler(buffer -> {
                    LOGGER.info("Received buffer: {}", buffer);
                    var id = buffer.getString(0, 36);
                    vertx.sharedData().<String, Boolean> getAsyncMap(AUTHENTICATED_CLIENTS_MAP, asyncMap -> {
                        if (asyncMap.succeeded()) {
                            var map = asyncMap.result();
                            map.get(id, asyncAuthenticated -> {
                                if (asyncAuthenticated.succeeded()) {
                                    var authenticated = asyncAuthenticated.result();
                                    if (Boolean.TRUE.equals(authenticated)) {
                                        socket.write(Buffer.buffer(Bytes.concat(OK, buffer.getString(36, buffer.length()).toUpperCase().getBytes())));
                                    } else {
                                        socket.write(NOK);
                                    }
                                } else {
                                    LOGGER.error("Unable to get value for {} from map {}", id, AUTHENTICATED_CLIENTS_MAP, asyncAuthenticated.cause());
                                    socket.write(FAILURE);
                                }
                            });
                        } else {
                            LOGGER.error("Unable to get map {}", AUTHENTICATED_CLIENTS_MAP, asyncMap.cause());
                            socket.write(FAILURE);
                        }
                    });
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
