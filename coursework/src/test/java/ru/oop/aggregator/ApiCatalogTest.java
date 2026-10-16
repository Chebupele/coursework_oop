package ru.oop.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.oop.aggregator.api.ApiCatalog;
import ru.oop.aggregator.model.ApiSource;
import ru.oop.aggregator.model.DataRecord;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApiCatalogTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsDefaultSourcesWithoutNetwork() throws Exception {
        ApiCatalog catalog = ApiCatalog.load(null, mapper);
        assertEquals(3, catalog.all().size());
        assertEquals(List.of("weather", "books", "products"), catalog.all().stream().map(ApiSource::name).toList());
        assertEquals(1, catalog.select(List.of("weather", "weather")).size());
        assertThrows(IllegalArgumentException.class, () -> catalog.select(List.of("unknown")));
    }

    @Test
    void loadsCustomConfiguration() throws Exception {
        Path config = directory.resolve("apis.json");
        Files.writeString(config, "[{\"name\":\"test\",\"url\":\"https://example.org/data?q=java\"}]");
        assertEquals("test", ApiCatalog.load(config, mapper).all().get(0).name());
        Files.writeString(config, "broken");
        assertThrows(java.io.IOException.class, () -> ApiCatalog.load(config, mapper));
        assertThrows(java.io.IOException.class, () -> ApiCatalog.load(directory.resolve("missing"), mapper));
    }

    @Test
    void rejectsInvalidSourcesAndRecords() {
        ApiSource valid = new ApiSource("test", URI.create("https://example.org/?q=1"));
        assertThrows(IllegalArgumentException.class, () -> new ApiCatalog(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ApiCatalog(List.of(valid, valid)));
        assertThrows(IllegalArgumentException.class, () -> new ApiSource("", valid.url()));
        assertThrows(IllegalArgumentException.class, () -> new ApiSource(null, valid.url()));
        assertThrows(IllegalArgumentException.class, () -> new ApiSource("test", null));
        assertThrows(IllegalArgumentException.class, () -> new ApiSource("test", URI.create("http://example.org/?a=b")));
        assertThrows(IllegalArgumentException.class, () -> new ApiSource("test", URI.create("https://example.org/")));
        assertThrows(IllegalArgumentException.class, () -> new ApiSource("test", URI.create("https://example.org/?")));
        assertThrows(IllegalArgumentException.class, () -> new DataRecord("", "test", "bad", mapper.createObjectNode()));
        assertThrows(IllegalArgumentException.class, () -> new DataRecord("id", "", "bad", mapper.createObjectNode()));
        assertThrows(IllegalArgumentException.class, () -> new DataRecord("id", "test", "bad", null));
        assertThrows(java.time.format.DateTimeParseException.class,
                () -> new DataRecord("id", "test", "bad", mapper.createObjectNode()));
    }
}
