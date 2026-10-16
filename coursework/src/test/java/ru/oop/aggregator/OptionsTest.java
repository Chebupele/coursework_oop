package ru.oop.aggregator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.oop.aggregator.cli.Options;
import ru.oop.aggregator.cli.ConsoleInterface;
import ru.oop.aggregator.model.OutputFormat;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptionsTest {
    @Test
    void parsesDefaults() {
        Options options = Options.parse(new String[0]);
        assertEquals(List.of(), options.apis());
        assertEquals(OutputFormat.JSON, options.format());
        assertEquals(Path.of("data/records.json"), options.file());
        assertEquals(3, options.threads());
        assertEquals(Duration.ofSeconds(60), options.interval());
        assertEquals(1, options.rounds());
        assertFalse(options.append());
        assertFalse(options.sequential());
        assertFalse(options.readOnly());
    }

    @Test
    void parsesAllParameters() {
        Options options = Options.parse(new String[]{"--apis", "weather, books,weather", "--format", "CSV",
                "--file", "output.csv", "--mode", "append", "--threads", "2", "--interval", "4",
                "--rounds", "0", "--execution", "parallel", "--read", "--source", "books", "--config", "api.json"});
        assertEquals(List.of("weather", "books"), options.apis());
        assertEquals(OutputFormat.CSV, options.format());
        assertEquals(Path.of("output.csv"), options.file());
        assertTrue(options.append());
        assertTrue(options.readOnly());
        assertEquals(2, options.threads());
        assertEquals(Duration.ofSeconds(4), options.interval());
        assertEquals(0, options.rounds());
        assertEquals("books", options.source());
        assertEquals(Path.of("api.json"), options.config());
        assertTrue(Options.parse(new String[]{"--help"}).help());
        assertTrue(ConsoleInterface.help().contains("--execution"));
        assertTrue(Options.parse(new String[]{"--execution", "sequential"}).sequential());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--bad", "--threads", "--threads x", "--threads 0", "--threads -1",
            "--interval 0", "--rounds -1", "--format xml", "--mode bad", "--execution bad",
            "--apis a,,b", "--source weather", "--threads 1 --threads 2", "--read --read",
            "--execution sequential --rounds 2", "--file --read"})
    void rejectsInvalidArguments(String line) {
        assertThrows(IllegalArgumentException.class, () -> Options.parse(line.split(" ")));
    }
}
