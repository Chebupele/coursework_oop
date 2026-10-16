package ru.oop.aggregator.cli;

import ru.oop.aggregator.model.OutputFormat;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record Options(List<String> apis, OutputFormat format, Path file, boolean append,
                      int threads, Duration interval, int rounds, boolean sequential,
                      boolean readOnly, String source, Path config, boolean help) {
    private static final Set<String> VALUES = Set.of("--apis", "--format", "--file", "--mode",
            "--threads", "--interval", "--rounds", "--execution", "--source", "--config");

    public static Options parse(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (values.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate argument: " + key);
            }
            if (key.equals("--read") || key.equals("--help")) {
                values.put(key, "true");
            } else if (VALUES.contains(key) && i + 1 < args.length && !args[i + 1].startsWith("--")) {
                values.put(key, args[++i]);
            } else {
                throw new IllegalArgumentException("Unknown argument or missing value: " + key);
            }
        }
        OutputFormat format = OutputFormat.parse(values.getOrDefault("--format", "json"));
        List<String> apis = values.containsKey("--apis")
                ? Arrays.stream(values.get("--apis").split(",", -1)).map(String::trim).distinct().toList()
                : List.of();
        if (apis.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("API name must not be empty");
        }
        String mode = values.getOrDefault("--mode", "overwrite");
        String execution = values.getOrDefault("--execution", "parallel");
        if (!Set.of("overwrite", "append").contains(mode)) {
            throw new IllegalArgumentException("File mode must be overwrite or append");
        }
        if (!Set.of("parallel", "sequential").contains(execution)) {
            throw new IllegalArgumentException("Execution mode must be parallel or sequential");
        }
        int threads = integer(values.getOrDefault("--threads", "3"), "--threads", 1);
        int interval = integer(values.getOrDefault("--interval", "60"), "--interval", 1);
        int rounds = integer(values.getOrDefault("--rounds", "1"), "--rounds", 0);
        boolean read = values.containsKey("--read");
        if (values.containsKey("--source") && !read) {
            throw new IllegalArgumentException("--source requires --read");
        }
        if (execution.equals("sequential") && rounds != 1) {
            throw new IllegalArgumentException("Sequential mode performs one round: --rounds 1");
        }
        return new Options(apis, format,
                Path.of(values.getOrDefault("--file", "data/records." + format.name().toLowerCase(java.util.Locale.ROOT))),
                mode.equals("append"), threads, Duration.ofSeconds(interval), rounds,
                execution.equals("sequential"), read, values.get("--source"),
                values.containsKey("--config") ? Path.of(values.get("--config")) : null,
                values.containsKey("--help"));
    }

    private static int integer(String text, String name, int minimum) {
        try {
            int value = Integer.parseInt(text);
            if (value >= minimum) {
                return value;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": expected an integer greater than or equal to " + minimum);
        }
        throw new IllegalArgumentException(name + ": value must be greater than or equal to " + minimum);
    }
}
