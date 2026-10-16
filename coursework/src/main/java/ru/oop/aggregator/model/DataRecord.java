package ru.oop.aggregator.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record DataRecord(String id, String source, String timestamp, JsonNode data) {
    public DataRecord {
        if (id == null || id.isBlank() || source == null || source.isBlank() || data == null) {
            throw new IllegalArgumentException("Record must contain id, source and data");
        }
        Instant.parse(timestamp);
    }

    public static DataRecord create(String source, JsonNode data) {
        return new DataRecord(UUID.randomUUID().toString(), source, Instant.now().toString(), data);
    }
}
