package ru.oop.aggregator.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.oop.aggregator.api.ApiCatalog;
import ru.oop.aggregator.api.ApiClient;
import ru.oop.aggregator.service.ApplicationSession;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

public class ConsoleInterface {
    private final ApiCatalog catalog;
    private final ApiClient client;
    private final ObjectMapper mapper;
    private final BufferedReader input;
    private final PrintWriter output;

    public ConsoleInterface(ApiCatalog catalog, ApiClient client, ObjectMapper mapper, Reader input, PrintWriter output) {
        this.catalog = catalog;
        this.client = client;
        this.mapper = mapper;
        this.input = new BufferedReader(input);
        this.output = output;
    }

    public void automatic(Options options) throws IOException, InterruptedException {
        try (ApplicationSession session = session(options)) {
            if (options.readOnly()) {
                printRecords(session, options.source());
            } else if (options.sequential()) {
                session.collectOnce();
            } else {
                Thread shutdown = new Thread(session::close, "aggregator-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    session.start(options.rounds());
                    session.awaitCompletion();
                } finally {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                }
            }
        }
    }

    public void interactive() throws IOException, InterruptedException {
        output.println("Available APIs:");
        catalog.all().forEach(api -> output.println(api.name() + " - " + api.url()));
        try {
            Options options = askOptions();
            try (ApplicationSession session = session(options)) {
                Thread shutdown = new Thread(session::close, "interactive-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    menu(session);
                } finally {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                }
            }
        } catch (EOFException e) {
            output.println("End of input");
        }
        output.flush();
    }

    private Options askOptions() throws IOException {
        while (true) {
            String names = ask("APIs separated by commas (empty = all): ");
            String format = ask("Format (json/csv): ");
            String file = ask("File path: ");
            String mode = ask("File mode (overwrite/append): ");
            String threads = ask("Thread count n: ");
            String interval = ask("Polling interval t in seconds: ");
            try {
                String[] arguments = {"--format", format, "--file", file, "--mode", mode,
                        "--threads", threads, "--interval", interval};
                Options base = Options.parse(arguments);
                if (names.isEmpty()) {
                    return base;
                }
                catalog.select(java.util.Arrays.stream(names.split(",", -1)).map(String::trim).toList());
                return Options.parse(new String[]{"--apis", names, "--format", format, "--file", file,
                        "--mode", mode, "--threads", threads, "--interval", interval});
            } catch (IllegalArgumentException e) {
                output.println("Input error: " + e.getMessage());
            }
        }
    }

    private void menu(ApplicationSession session) throws IOException, InterruptedException {
        while (true) {
            output.println("1 - poll once sequentially; 2 - start periodic polling; 3 - stop polling;");
            output.println("4 - read all records; 5 - read records by API; 0 - exit");
            String command = ask("> ");
            try {
                switch (command) {
                    case "1" -> session.collectOnce();
                    case "2" -> {
                        session.start(0);
                        output.println("Polling started");
                    }
                    case "3" -> {
                        session.stop();
                        output.println("Polling stopped");
                    }
                    case "4" -> printRecords(session, null);
                    case "5" -> {
                        String source = ask("API name: ");
                        catalog.select(java.util.List.of(source));
                        printRecords(session, source);
                    }
                    case "0" -> { return; }
                    default -> output.println("Unknown command");
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                output.println("Error: " + e.getMessage());
            } catch (IOException e) {
                output.println("File read error: " + e.getMessage());
            }
        }
    }

    private ApplicationSession session(Options options) throws IOException {
        return new ApplicationSession(options, catalog, client, mapper, message -> {
            output.println(message);
            output.flush();
        });
    }

    private void printRecords(ApplicationSession session, String source) throws IOException {
        output.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(session.read(source)));
        output.flush();
    }

    private String ask(String prompt) throws IOException {
        output.print(prompt);
        output.flush();
        String line = input.readLine();
        if (line == null) {
            throw new EOFException();
        }
        return line.trim();
    }

    public static String help() {
        return """
                REST API aggregator. Run without arguments to open the interactive menu.
                --apis weather,books,products   APIs to query (default: all)
                --format json|csv              Output format (json)
                --file path                    Output file (data/records.json or .csv)
                --mode overwrite|append        Create a new file or append (overwrite)
                --threads n                    Maximum concurrent tasks (3)
                --interval t                   Delay after each request, in seconds (60)
                --rounds k                     Attempts per API (1); 0 = until Ctrl+C
                --execution sequential|parallel Execution mode (parallel)
                --read                         Read the file without querying APIs
                --source name                  Filter records by API when reading
                --config path                  JSON configuration with API names and HTTPS URLs
                --help                         Show this help
                """;
    }
}
