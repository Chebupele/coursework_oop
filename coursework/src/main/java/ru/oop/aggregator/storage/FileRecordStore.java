package ru.oop.aggregator.storage;

import ru.oop.aggregator.model.DataRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileRecordStore implements RecordStore {
    private final Path file;
    private final RecordCodec codec;

    public FileRecordStore(Path file, RecordCodec codec) {
        this.file = file.toAbsolutePath();
        this.codec = codec;
    }

    @Override
    public synchronized void clear() throws IOException {
        save(List.of());
    }

    @Override
    public synchronized void append(DataRecord record) throws IOException {
        List<DataRecord> records = new ArrayList<>(read());
        records.add(record);
        save(records);
    }

    @Override
    public synchronized List<DataRecord> read() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        return List.copyOf(codec.decode(Files.readString(file, StandardCharsets.UTF_8)));
    }

    private void save(List<DataRecord> records) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "records-", ".tmp");
        try {
            Files.writeString(temporary, codec.encode(records), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
