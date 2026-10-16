package ru.oop.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.oop.aggregator.api.ApiClient;
import ru.oop.aggregator.model.ApiSource;
import ru.oop.aggregator.model.DataRecord;
import ru.oop.aggregator.service.AggregationService;
import ru.oop.aggregator.service.PollingService;
import ru.oop.aggregator.storage.RecordStore;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PollingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiSource source = new ApiSource("test", URI.create("https://example.org/?q=1"));

    static class MemoryStore implements RecordStore {
        final List<DataRecord> records = Collections.synchronizedList(new ArrayList<>());
        public void clear() { records.clear(); }
        public void append(DataRecord record) { records.add(record); }
        public List<DataRecord> read() { return List.copyOf(records); }
    }

    @Test
    void collectsAndPropagatesFailures() throws Exception {
        MemoryStore store = new MemoryStore();
        var service = new AggregationService(api -> mapper.createObjectNode().put("x", 1), store);
        DataRecord record = service.collect(source);
        assertEquals("test", record.source());
        assertEquals(List.of(record), store.read());
        assertThrows(IOException.class, () -> new AggregationService(api -> {
            throw new IOException("offline");
        }, store).collect(source));
        assertThrows(InterruptedException.class, () -> new AggregationService(api -> {
            throw new InterruptedException();
        }, store).collect(source));
    }

    @Test
    @Timeout(10)
    void limitsConcurrencyAndWaitsAfterRequestCompletion() throws Exception {
        List<ApiSource> sources = List.of(source,
                new ApiSource("second", URI.create("https://example.org/?q=2")),
                new ApiSource("third", URI.create("https://example.org/?q=3")));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        Map<String, List<Long>> starts = new ConcurrentHashMap<>();
        Map<String, List<Long>> ends = new ConcurrentHashMap<>();
        MemoryStore store = new MemoryStore();
        ApiClient client = api -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            starts.computeIfAbsent(api.name(), key -> new ArrayList<>()).add(System.nanoTime());
            try {
                Thread.sleep(25);
                return mapper.createObjectNode();
            } finally {
                ends.computeIfAbsent(api.name(), key -> new ArrayList<>()).add(System.nanoTime());
                active.decrementAndGet();
            }
        };
        try (var polling = new PollingService(new AggregationService(client, store), message -> { },
                sources, 2, Duration.ofMillis(40), 2)) {
            polling.awaitCompletion();
        }
        assertEquals(2, maximum.get());
        assertEquals(6, store.read().size());
        for (ApiSource api : sources) {
            assertEquals(2, starts.get(api.name()).size());
            long delay = starts.get(api.name()).get(1) - ends.get(api.name()).get(0);
            assertTrue(delay >= Duration.ofMillis(40).toNanos(), "Polling repeated too early: " + api.name());
        }
    }

    @Test
    @Timeout(5)
    void failuresDoNotPreventOtherRoundsOrSources() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> messages = Collections.synchronizedList(new ArrayList<>());
        ApiClient client = api -> { calls.incrementAndGet(); throw new IOException("offline"); };
        try (var polling = new PollingService(new AggregationService(client, new MemoryStore()), messages::add,
                List.of(source), 1, Duration.ofMillis(5), 2)) {
            polling.awaitCompletion();
        }
        assertEquals(2, calls.get());
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("offline"));
    }

    @Test
    @Timeout(5)
    void stopInterruptsRunningRequestAndReleasesWaiter() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ApiClient client = api -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw e;
            }
            return mapper.createObjectNode();
        };
        PollingService polling = new PollingService(new AggregationService(client, new MemoryStore()), message -> { },
                List.of(source), 1, Duration.ofMillis(10), 0);
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS));
        } finally {
            polling.close();
        }
        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
        polling.awaitCompletion();
        polling.close();
    }

    @Test
    void validatesPollingParameters() {
        var service = new AggregationService(api -> mapper.createObjectNode(), new MemoryStore());
        assertThrows(IllegalArgumentException.class, () -> new PollingService(service, s -> { }, List.of(), 1, Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new PollingService(service, s -> { }, List.of(source), 0, Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new PollingService(service, s -> { }, List.of(source), 1, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new PollingService(service, s -> { }, List.of(source), 1, Duration.ofNanos(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new PollingService(service, s -> { }, List.of(source), 1, Duration.ofSeconds(1), -1));
    }
}
