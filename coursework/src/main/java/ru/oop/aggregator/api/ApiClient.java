package ru.oop.aggregator.api;

import com.fasterxml.jackson.databind.JsonNode;
import ru.oop.aggregator.model.ApiSource;
import java.io.IOException;

@FunctionalInterface
public interface ApiClient {
    JsonNode fetch(ApiSource source) throws IOException, InterruptedException;
}
