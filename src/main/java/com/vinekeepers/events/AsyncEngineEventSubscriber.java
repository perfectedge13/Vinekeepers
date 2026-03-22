package com.vinekeepers.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Dispatches {@link Event}s to a delegate on a dedicated executor so JDA listener threads return
 * immediately after {@link EventBus#publish(Event)} hands off.
 */
public final class AsyncEngineEventSubscriber implements EventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AsyncEngineEventSubscriber.class);

    private final EventSubscriber delegate;
    private final ExecutorService executor;

    public AsyncEngineEventSubscriber(EventSubscriber delegate, ExecutorService executor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void onEvent(Event event) {
        String ingressThread = Thread.currentThread().getName();
        String kind = event != null ? event.getKind() : "";
        String source = event != null ? event.getSourceId() : "";
        log.debug(
                "Enqueue engine event ingressThread={} sourceId={} kind={}",
                ingressThread,
                source,
                kind);
        try {
            executor.execute(
                    () -> {
                        String worker = Thread.currentThread().getName();
                        log.debug(
                                "Engine worker start thread={} sourceId={} kind={}",
                                worker,
                                source,
                                kind);
                        try {
                            delegate.onEvent(event);
                        } catch (Throwable t) {
                            log.error(
                                    "Engine delegate failed sourceId={} kind={} workerThread={}",
                                    source,
                                    kind,
                                    worker,
                                    t);
                        }
                    });
        } catch (RejectedExecutionException e) {
            log.error(
                    "Engine event rejected (queue full or shutdown) ingressThread={} sourceId={} kind={}",
                    ingressThread,
                    source,
                    kind,
                    e);
        }
    }
}
