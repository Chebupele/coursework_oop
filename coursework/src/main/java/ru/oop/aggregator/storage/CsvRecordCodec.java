package ru.oop.aggregator.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import ru.oop.aggregator.model.DataRecord;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CsvRecordCodec implements RecordCodec {
    private final ObjectMapper mapper;

    public CsvRecordCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String encode(List<DataRecord> records) throws IOException {
        Set<String> columns = new LinkedHashSet<>(List.of("id", "source", "timestamp"));
        List<Map<String, String>> rows = new ArrayList<>();
        for (DataRecord record : records) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", record.id());
            row.put("source", record.source());
            row.put("timestamp", record.timestamp());
            flatten("data", record.data(), row);
            columns.addAll(row.keySet());
            rows.add(row);
        }
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader(columns.toArray(String[]::new)).get();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Map<String, String> row : rows) {
                printer.printRecord(columns.stream().map(column -> row.getOrDefault(column, "")).toList());
            }
        }
        return writer.toString();
    }

    private void flatten(String path, JsonNode node, Map<String, String> row) throws IOException {
        if (node.isObject()) {
            row.put(path, "{}");
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                flatten(path + "/" + escape(field.getKey()), field.getValue(), row);
            }
        } else if (node.isArray()) {
            row.put(path, "[]");
            for (int i = 0; i < node.size(); i++) {
                flatten(path + "/" + i, node.get(i), row);
            }
        } else {
            row.put(path, mapper.writeValueAsString(node));
        }
    }

    @Override
    public List<DataRecord> decode(String text) throws IOException {
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(text, format)) {
            if (!parser.getHeaderNames().containsAll(List.of("id", "source", "timestamp"))) {
                throw new IOException("CSV must contain id, source and timestamp columns");
            }
            List<DataRecord> result = new ArrayList<>();
            for (CSVRecord row : parser) {
                ObjectNode wrapper = mapper.createObjectNode();
                for (String header : parser.getHeaderNames().stream()
                        .sorted(java.util.Comparator.comparingInt(header -> header.split("/", -1).length)).toList()) {
                    if ((header.equals("data") || header.startsWith("data/")) && !row.get(header).isEmpty()) {
                        insert(wrapper, header.split("/", -1), 0, mapper.readTree(row.get(header)));
                    }
                }
                result.add(new DataRecord(row.get("id"), row.get("source"), row.get("timestamp"), wrapper.get("data")));
            }
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid CSV record", e);
        }
    }

    private void insert(JsonNode parent, String[] parts, int index, JsonNode value) {
        String key = unescape(parts[index]);
        boolean last = index == parts.length - 1;
        JsonNode child = parent.isArray() ? parent.get(Integer.parseInt(key)) : parent.get(key);
        if (last) {
            child = value;
        } else if (child == null || child.isNull()) {
            child = mapper.createObjectNode();
        }
        if (parent instanceof ArrayNode array) {
            int position = Integer.parseInt(key);
            while (array.size() <= position) {
                array.addNull();
            }
            array.set(position, child);
        } else {
            ((ObjectNode) parent).set(key, child);
        }
        if (!last) {
            insert(child, parts, index + 1, value);
        }
    }

    private String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    private String unescape(String key) {
        return key.replace("~1", "/").replace("~0", "~");
    }
}
