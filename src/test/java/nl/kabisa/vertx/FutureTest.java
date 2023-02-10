package nl.kabisa.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class FutureTest {

    @Test
    public void testSucceeded(VertxTestContext testContext) {
        Future.succeededFuture("one").andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("one", ar.result());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testFailed(VertxTestContext testContext) {
        Future.failedFuture("one").andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.failed());
                assertEquals("one", ar.cause().getMessage());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testComposeSucceeded(VertxTestContext testContext) {
        Future.succeededFuture("one").compose(r -> {
            testContext.verify(() -> assertEquals("one", r));
            return Future.succeededFuture("two");
        }).andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("two", ar.result());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testComposeFailed(VertxTestContext testContext) {
        Future.failedFuture("one").compose(r -> {
            testContext.failNow(new RuntimeException());
            return Future.succeededFuture("two");
        }).andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.failed());
                assertEquals("one", ar.cause().getMessage());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testOtherwiseSucceeded(VertxTestContext testContext) {
        Future.succeededFuture("one").otherwise(r -> "two").andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("one", ar.result());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testOtherwiseFailed(VertxTestContext testContext) {
        Future.failedFuture("one").otherwise(t -> "two").andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("two", ar.result());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testRecoverSucceeded(VertxTestContext testContext) {
        Future.succeededFuture("one").recover(t -> {
            testContext.failNow(new RuntimeException());
            return Future.succeededFuture("two");
        }).andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("one", ar.result());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testRecoverFailed(VertxTestContext testContext) {
        Future.failedFuture("one").recover(t -> {
            testContext.verify(() -> assertEquals("one", t.getMessage()));
            return Future.succeededFuture("two");
        }).andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("two", ar.result());
            });
            testContext.completeNow();
        });
    }

    @Test
    public void testMap(VertxTestContext testContext) {
        Future.succeededFuture("one").map(String::toUpperCase).andThen(ar -> {
            testContext.verify(() -> {
                assertTrue(ar.succeeded());
                assertEquals("ONE", ar.result());
            });
            testContext.completeNow();
        });
    }
}
