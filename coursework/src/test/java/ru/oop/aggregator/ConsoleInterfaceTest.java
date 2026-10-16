package ru.oop.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import ru.oop.aggregator.api.ApiCatalog;
import ru.oop.aggregator.api.ApiClient;
import ru.oop.aggregator.cli.ConsoleInterface;
import ru.oop.aggregator.cli.Options;
import ru.oop.aggregator.service.ApplicationSession;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ru.oop.aggregator.model.ApiSource;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleInterfaceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiCatalog catalog = new ApiCatalog(List.of(
            new ApiSource("test", URI.create("https://example.org/?q=1")),
            new ApiSource("second", URI.create("https://example.org/?q=2"))));
    private final AtomicInteger calls = new AtomicInteger();
    private final ApiClient client = api -> mapper.createObjectNode().put("value", calls.incrementAndGet());

    private Options options(String... extra) {
        var args = new java.util.ArrayList<>(List.of("--file", directory.resolve("data.json").toString()));
        args.addAll(List.of(extra));
        return Options.parse(args.toArray(String[]::new));
    }

    private ConsoleInterface console(String input, StringWriter output, ApiClient api) {
        return new ConsoleInterface(catalog, api, mapper, new StringReader(input), new PrintWriter(output, true));
    }

    @Test
    @Timeout(5)
    void automaticSequentialParallelAndReadDoNotMixResponsibilities() throws Exception {
        StringWriter output = new StringWriter();
        var console = console("", output, client);
        console.automatic(options("--execution", "sequential"));
        assertEquals(2, calls.get());
        console.automatic(options("--mode", "append", "--apis", "test"));
        assertEquals(3, calls.get());
        console.automatic(options("--read", "--source", "test"));
        assertEquals(3, calls.get());
        assertTrue(output.toString().contains("value"));
        try (ApplicationSession session = new ApplicationSession(options("--read"), catalog, client, mapper, s -> { })) {
            assertEquals(3, session.read(null).size());
            assertEquals(2, session.read("test").size());
            assertTrue(session.read("missing").isEmpty());
        }
    }

    @Test
    void sessionSupportsCsvAndFailures() throws Exception {
        Options csv = Options.parse(new String[]{"--file", directory.resolve("data.csv").toString(), "--format", "csv"});
        try (ApplicationSession session = new ApplicationSession(csv, catalog, client, mapper, s -> { })) {
            session.collectOnce();
            assertEquals(2, session.read(null).size());
            assertThrows(IllegalStateException.class, session::awaitCompletion);
            session.stop();
        }
        StringWriter output = new StringWriter();
        console("", output, api -> { throw new java.io.IOException("offline"); })
                .automatic(options("--execution", "sequential"));
        assertTrue(output.toString().contains("offline"));
    }

    @Test
    @Timeout(5)
    void interactiveMenuHandlesCommandsStartStopAndRead() throws Exception {
        String input = "test\njson\n" + directory.resolve("menu.json")
                + "\noverwrite\n1\n1\n1\n4\n5\ntest\n5\nbad\nunknown\n2\n2\n1\n3\n3\n0\n";
        StringWriter output = new StringWriter();
        console(input, output, client).interactive();
        String text = output.toString();
        assertTrue(text.contains("Polling started"));
        assertTrue(text.contains("Polling is already running"));
        assertTrue(text.contains("Stop periodic polling"));
        assertTrue(text.contains("Polling stopped"));
        assertTrue(text.contains("Unknown command"));
        assertTrue(text.contains("Unknown API"));
        assertTrue(text.contains("value"));
    }

    @Test
    void retriesBadConfigurationAndClosesOnEndOfInput() throws Exception {
        String file = directory.resolve("retry.json").toString();
        String input = "bad\njson\n" + file + "\noverwrite\n1\n1\n"
                + "\njson\n" + file + "\noverwrite\n1\n1\n";
        StringWriter output = new StringWriter();
        console(input, output, client).interactive();
        assertTrue(output.toString().contains("Input error"));
        assertTrue(output.toString().contains("End of input"));
        output = new StringWriter();
        console("", output, client).interactive();
        assertTrue(output.toString().contains("End of input"));
    }

    @Test
    @Timeout(5)
    void sessionCanStopAndStartAgain() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        ApiClient blocking = api -> {
            entered.countDown();
            new CountDownLatch(1).await();
            return mapper.createObjectNode();
        };
        try (var session = new ApplicationSession(options("--apis", "test"), catalog, blocking, mapper, s -> { })) {
            session.start(0);
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            session.stop();
            session.start(0);
            session.stop();
        }
    }
}
