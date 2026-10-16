package ru.oop.aggregator.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.oop.aggregator.model.DataRecord;
import java.io.IOException;
import java.util.List;

public class JsonRecordCodec implements RecordCodec {
    private final ObjectMapper mapper;

    public JsonRecordCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String encode(List<DataRecord> records) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(records);
    }

    @Override
    public List<DataRecord> decode(String text) throws IOException {
        List<DataRecord> records = mapper.readValue(text, new TypeReference<List<DataRecord>>() { });
        if (records == null) {
            throw new IOException("Expected a JSON array of records");
        }
        return records;
    }
}
