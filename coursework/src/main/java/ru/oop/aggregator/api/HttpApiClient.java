package ru.oop.aggregator.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.oop.aggregator.model.ApiSource;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpApiClient implements ApiClient {
    private final HttpClient client;
    private final ObjectMapper mapper;

    public HttpApiClient(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public JsonNode fetch(ApiSource source) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(source.url())
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", "OopCoursework/1.0 (educational console application)")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(source.name() + ": HTTP " + response.statusCode());
        }
        JsonNode data = mapper.readTree(response.body());
        if (data == null || (!data.isObject() && !data.isArray())) {
            throw new IOException(source.name() + ": expected a JSON object or array");
        }
        return data;
    }
}
