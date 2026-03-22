package com.vinekeepers.events;

import com.vinekeepers.env.Env;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the executor used to run {@link VinekeepersEngine} work off JDA / connector callback threads.
 */
public final class EngineEventExecutorFactory {

    private EngineEventExecutorFactory() {}

    /**
     * @return fixed pool; default 1 thread preserves global ordering of inbound events. Set
     *     {@code VINEKEEPERS_ENGINE_EVENT_THREADS} for parallelism (reordering risk across sessions).
     */
    public static ExecutorService create() {
        int threads = parsePositiveInt(Env.get("VINEKEEPERS_ENGINE_EVENT_THREADS", "1"), 1);
        int queueCapacity = parsePositiveInt(Env.get("VINEKEEPERS_ENGINE_EVENT_QUEUE_CAPACITY", "8000"), 8000);
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(queueCapacity);
        ThreadFactory tf =
                new ThreadFactory() {
                    private final AtomicInteger n = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "vinekeepers-engine-events-" + n.getAndIncrement());
                        t.setDaemon(false);
                        return t;
                    }
                };
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                queue,
                tf,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static int parsePositiveInt(String raw, int dflt) {
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : dflt;
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    public static void shutdownQuietly(ExecutorService ex) {
        if (ex == null) {
            return;
        }
        ex.shutdown();
        try {
            if (!ex.awaitTermination(15, TimeUnit.SECONDS)) {
                ex.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ex.shutdownNow();
        }
    }
}
