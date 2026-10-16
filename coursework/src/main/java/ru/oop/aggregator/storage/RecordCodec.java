package ru.oop.aggregator.storage;

import ru.oop.aggregator.model.DataRecord;
import java.io.IOException;
import java.util.List;

public interface RecordCodec {
    String encode(List<DataRecord> records) throws IOException;
    List<DataRecord> decode(String text) throws IOException;
}
