package ru.oop.supervisor;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Usage: lab-1 [interval_ms]");
            }
            long interval = args.length == 0 ? 1000 : Long.parseLong(args[0]);
            AbstractProgram program = new AbstractProgram(interval);
            Supervisor supervisor = new Supervisor(program);
            Thread shutdown = new Thread(() -> {
                program.terminate();
                supervisor.interrupt();
            }, "supervisor-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                supervisor.start();
                supervisor.join();
                program.awaitTermination();
            } finally {
                program.terminate();
                Runtime.getRuntime().removeShutdownHook(shutdown);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
