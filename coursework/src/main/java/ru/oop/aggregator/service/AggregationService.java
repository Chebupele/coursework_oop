package ru.oop.aggregator.service;

import ru.oop.aggregator.api.ApiClient;
import ru.oop.aggregator.model.ApiSource;
import ru.oop.aggregator.model.DataRecord;
import ru.oop.aggregator.storage.RecordStore;
import java.io.IOException;

public class AggregationService {
    private final ApiClient client;
    private final RecordStore store;

    public AggregationService(ApiClient client, RecordStore store) {
        this.client = client;
        this.store = store;
    }

    public DataRecord collect(ApiSource source) throws IOException, InterruptedException {
        DataRecord record = DataRecord.create(source.name(), client.fetch(source));
        store.append(record);
        return record;
    }
}
