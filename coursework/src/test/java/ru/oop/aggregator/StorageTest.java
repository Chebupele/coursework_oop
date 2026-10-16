package ru.oop.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.oop.aggregator.model.DataRecord;
import ru.oop.aggregator.model.OutputFormat;
import ru.oop.aggregator.storage.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StorageTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    private RecordCodec codec(OutputFormat format) {
        return format == OutputFormat.JSON ? new JsonRecordCodec(mapper) : new CsvRecordCodec(mapper);
    }

    @ParameterizedTest
    @EnumSource(OutputFormat.class)
    void preservesNestedValuesAndSpecialCharacters(OutputFormat format) throws Exception {
        String data = """
                {"name":"Москва", "price":12.5, "active":true, "nothing":null,
                 "address":{"city":"Москва", "0":"numeric object key"},
                 "items":[{"id":1,"tags":["a","b"]},{"id":2}], "empty":[], "object":{},
                 "slash/key":{"~key":"value"}, "":{"":7}}
                """;
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(data);
        node.put("name", "Москва, \"центр\"\nулица");
        List<DataRecord> records = List.of(DataRecord.create("weather", node),
                DataRecord.create("products", mapper.readTree("[1,true,null,{\"name\":\"phone\"}]")));
        RecordCodec codec = codec(format);
        assertEquals(records, codec.decode(codec.encode(records)));
        assertEquals(List.of(), codec.decode(codec.encode(List.of())));
    }

    @ParameterizedTest
    @EnumSource(OutputFormat.class)
    void createsAppendsReopensAndClearsFile(OutputFormat format) throws Exception {
        Path file = directory.resolve("nested/data." + format);
        FileRecordStore store = new FileRecordStore(file, codec(format));
        assertEquals(List.of(), store.read());
        store.clear();
        DataRecord first = DataRecord.create("a", mapper.readTree("{\"a\":{\"x\":1}}"));
        DataRecord second = DataRecord.create("b", mapper.readTree("{\"a\":[2,3],\"new\":true}"));
        store.append(first);
        store.append(second);
        assertEquals(List.of(first, second), new FileRecordStore(file, codec(format)).read());
        store.clear();
        assertTrue(store.read().isEmpty());
        assertTrue(Files.list(file.getParent()).allMatch(path -> path.equals(file)));
    }

    @ParameterizedTest
    @EnumSource(OutputFormat.class)
    void serializesConcurrentWritesWithoutLostRecords(OutputFormat format) throws Exception {
        FileRecordStore store = new FileRecordStore(directory.resolve("parallel." + format), codec(format));
        var executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 30; i++) {
                futures.add(executor.submit(() -> {
                    store.append(DataRecord.create("test", mapper.createObjectNode().put("x", 1)));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertEquals(30, store.read().size());
            assertEquals(30, store.read().stream().map(DataRecord::id).distinct().count());
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @EnumSource(OutputFormat.class)
    void doesNotOverwriteCorruptedDataWhileAppending(OutputFormat format) throws Exception {
        Path file = directory.resolve("corrupt." + format);
        Files.writeString(file, "corrupted");
        FileRecordStore store = new FileRecordStore(file, codec(format));
        assertThrows(IOException.class, () -> store.append(DataRecord.create("x", mapper.createObjectNode())));
        assertEquals("corrupted", Files.readString(file));
    }

    @Test
    void rejectsMalformedSerializedRecords() {
        assertThrows(IOException.class, () -> new JsonRecordCodec(mapper).decode("null"));
        assertThrows(IOException.class, () -> new JsonRecordCodec(mapper).decode("{}"));
        CsvRecordCodec csv = new CsvRecordCodec(mapper);
        assertThrows(IOException.class, () -> csv.decode("id,source,timestamp,data\r\nx,test,bad,{}\r\n"));
        assertThrows(IOException.class, () -> csv.decode("id,source,timestamp\r\nx,test,2026-01-01T00:00:00Z\r\n"));
        assertThrows(IOException.class, () -> csv.decode("id,source,timestamp,data\r\nx,test\r\n"));
    }
}
