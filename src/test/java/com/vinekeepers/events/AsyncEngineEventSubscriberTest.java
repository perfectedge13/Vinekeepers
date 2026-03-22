package com.vinekeepers.events;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncEngineEventSubscriberTest {

    @Test
    void delegateRunsOnExecutorThreadNotCaller() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> workerThread = new AtomicReference<>();
        EventSubscriber delegate =
                event -> {
                    workerThread.set(Thread.currentThread().getName());
                    latch.countDown();
                };
        ExecutorService ex =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "test-async-engine-worker");
                            t.setDaemon(true);
                            return t;
                        });
        try {
            new AsyncEngineEventSubscriber(delegate, ex)
                    .onEvent(new Event("discord", "message", Map.of()));
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread().getName(), workerThread.get());
            assertTrue(workerThread.get().contains("test-async-engine-worker"));
        } finally {
            ex.shutdown();
            assertTrue(ex.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
