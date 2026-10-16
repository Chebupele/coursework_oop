package ru.oop.aggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.oop.aggregator.api.ApiCatalog;
import ru.oop.aggregator.api.ApiClient;
import ru.oop.aggregator.cli.Options;
import ru.oop.aggregator.model.ApiSource;
import ru.oop.aggregator.model.DataRecord;
import ru.oop.aggregator.model.OutputFormat;
import ru.oop.aggregator.storage.CsvRecordCodec;
import ru.oop.aggregator.storage.FileRecordStore;
import ru.oop.aggregator.storage.JsonRecordCodec;
import ru.oop.aggregator.storage.RecordStore;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class ApplicationSession implements AutoCloseable {
    private final Options options;
    private final List<ApiSource> sources;
    private final RecordStore store;
    private final AggregationService aggregation;
    private final Consumer<String> output;
    private volatile PollingService polling;

    public ApplicationSession(Options options, ApiCatalog catalog, ApiClient client,
                              ObjectMapper mapper, Consumer<String> output) throws IOException {
        this.options = options;
        this.output = output;
        sources = options.apis().isEmpty() ? catalog.all() : catalog.select(options.apis());
        store = new FileRecordStore(options.file(), options.format() == OutputFormat.JSON
                ? new JsonRecordCodec(mapper) : new CsvRecordCodec(mapper));
        aggregation = new AggregationService(client, store);
        if (!options.append() && !options.readOnly()) {
            store.clear();
        }
    }

    public void collectOnce() throws InterruptedException {
        if (polling != null) {
            throw new IllegalStateException("Stop periodic polling first");
        }
        for (ApiSource source : sources) {
            try {
                aggregation.collect(source);
                output.accept("Saved: " + source.name());
            } catch (IOException e) {
                output.accept("Error " + source.name() + ": " + e.getMessage());
            }
        }
    }

    public synchronized void start(int rounds) {
        if (polling != null) {
            throw new IllegalStateException("Polling is already running");
        }
        polling = new PollingService(aggregation, output, sources, options.threads(), options.interval(), rounds);
    }

    public void awaitCompletion() throws InterruptedException {
        if (polling == null) {
            throw new IllegalStateException("Polling has not been started");
        }
        polling.awaitCompletion();
    }

    public List<DataRecord> read(String source) throws IOException {
        return store.read().stream().filter(record -> source == null || record.source().equals(source)).toList();
    }

    public synchronized void stop() {
        if (polling != null) {
            polling.close();
            polling = null;
        }
    }

    @Override
    public void close() {
        stop();
    }
}
