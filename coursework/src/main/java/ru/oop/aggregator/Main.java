package ru.oop.aggregator;

import java.io.PrintStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.oop.aggregator.api.ApiCatalog;
import ru.oop.aggregator.api.HttpApiClient;
import ru.oop.aggregator.cli.ConsoleInterface;
import ru.oop.aggregator.cli.Options;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class Main {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        try {
            Options options = Options.parse(args);
            if (options.help()) {
                System.out.print(ConsoleInterface.help());
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            ApiCatalog catalog = ApiCatalog.load(options.config(), mapper);
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            ConsoleInterface console = new ConsoleInterface(catalog, new HttpApiClient(http, mapper), mapper,
                    new InputStreamReader(System.in, StandardCharsets.UTF_8), new PrintWriter(System.out, true, StandardCharsets.UTF_8));
            if (args.length == 0) {
                console.interactive();
            } else {
                console.automatic(options);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
