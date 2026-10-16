package ru.oop.aggregator.service;

import ru.oop.aggregator.model.ApiSource;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PollingService implements AutoCloseable {
    private final AggregationService aggregation;
    private final Consumer<String> output;
    private final ScheduledExecutorService executor;
    private final CountDownLatch completed;
    private final long intervalMillis;
    private final int rounds;

    public PollingService(AggregationService aggregation, Consumer<String> output,
                          List<ApiSource> sources, int threads, Duration interval, int rounds) {
        if (sources.isEmpty() || threads <= 0 || interval.toMillis() <= 0 || rounds < 0) {
            throw new IllegalArgumentException("Invalid polling parameters");
        }
        this.aggregation = aggregation;
        this.output = output;
        this.intervalMillis = interval.toMillis();
        this.rounds = rounds;
        this.completed = new CountDownLatch(sources.size());
        AtomicInteger counter = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(threads,
                task -> new Thread(task, "api-poller-" + counter.incrementAndGet()));
        for (ApiSource source : sources) {
            AtomicInteger attempts = new AtomicInteger();
            executor.scheduleWithFixedDelay(() -> poll(source, attempts), 0, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void poll(ApiSource source, AtomicInteger attempts) {
        if (rounds > 0 && attempts.get() >= rounds) {
            return;
        }
        try {
            aggregation.collect(source);
            output.accept("Saved: " + source.name());
        } catch (IOException | RuntimeException e) {
            output.accept("Error " + source.name() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (attempts.incrementAndGet() == rounds) {
                completed.countDown();
            }
        }
    }

    public void awaitCompletion() throws InterruptedException {
        completed.await();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(25, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Polling threads did not terminate");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            while (completed.getCount() > 0) {
                completed.countDown();
            }
        }
    }
}
