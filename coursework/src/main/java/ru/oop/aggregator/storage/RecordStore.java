package ru.oop.aggregator.storage;

import ru.oop.aggregator.model.DataRecord;
import java.io.IOException;
import java.util.List;

public interface RecordStore {
    void clear() throws IOException;
    void append(DataRecord record) throws IOException;
    List<DataRecord> read() throws IOException;
}
