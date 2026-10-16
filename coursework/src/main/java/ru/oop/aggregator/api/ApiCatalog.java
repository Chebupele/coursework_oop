package ru.oop.aggregator.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.oop.aggregator.model.ApiSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiCatalog {
    private final Map<String, ApiSource> sources = new LinkedHashMap<>();

    public ApiCatalog(List<ApiSource> sources) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("API list is empty");
        }
        for (ApiSource source : sources) {
            if (this.sources.putIfAbsent(source.name(), source) != null) {
                throw new IllegalArgumentException("Duplicate API: " + source.name());
            }
        }
    }

    public static ApiCatalog load(Path config, ObjectMapper mapper) throws IOException {
        try (InputStream input = config == null
                ? ApiCatalog.class.getResourceAsStream("/apis.json") : Files.newInputStream(config)) {
            if (input == null) {
                throw new IOException("Resource apis.json was not found");
            }
            return new ApiCatalog(mapper.readValue(input, new TypeReference<List<ApiSource>>() { }));
        }
    }

    public List<ApiSource> select(List<String> names) {
        return names.stream().map(name -> {
            ApiSource source = sources.get(name);
            if (source == null) {
                throw new IllegalArgumentException("Unknown API: " + name + ". Available: " + sources.keySet());
            }
            return source;
        }).distinct().toList();
    }

    public List<ApiSource> all() {
        return List.copyOf(sources.values());
    }
}
