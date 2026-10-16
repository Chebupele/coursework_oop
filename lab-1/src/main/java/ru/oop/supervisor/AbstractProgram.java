package ru.oop.supervisor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;
import java.util.function.Supplier;

public class AbstractProgram implements Runnable {
    private final Queue<ProgramState> changes = new ArrayDeque<>();
    private final long intervalMillis;
    private final Supplier<ProgramState> randomState;
    private ProgramState state = ProgramState.UNKNOWN;
    private boolean terminated;
    private Thread worker;
    private Thread stateDaemon;

    public AbstractProgram(long intervalMillis) {
        this(intervalMillis, () -> {
            ProgramState[] states = {ProgramState.RUNNING, ProgramState.STOPPING, ProgramState.FATAL_ERROR};
            return states[new Random().nextInt(states.length)];
        });
    }

    AbstractProgram(long intervalMillis, Supplier<ProgramState> randomState) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        this.intervalMillis = intervalMillis;
        this.randomState = randomState;
        changes.add(state);
        System.out.println("Initial state: " + state);
    }

    public synchronized void launch() {
        if (worker != null || terminated) {
            throw new IllegalStateException("Program threads have already been started");
        }
        worker = new Thread(this, "abstract-program");
        stateDaemon = new Thread(this::changeRandomly, "random-state-daemon");
        stateDaemon.setDaemon(true);
        worker.start();
        stateDaemon.start();
    }

    public synchronized void changeState(ProgramState next) {
        if (terminated || state == ProgramState.FATAL_ERROR) {
            return;
        }
        if (next == ProgramState.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN is only allowed before the first start");
        }
        if (state != next) {
            System.out.println("State: " + state + " -> " + next);
            state = next;
            changes.add(next);
            notifyAll();
        }
    }

    public synchronized ProgramState awaitState() throws InterruptedException {
        while (changes.isEmpty() && !terminated) {
            wait();
        }
        return changes.poll();
    }

    public synchronized ProgramState getState() {
        return state;
    }

    public synchronized boolean isTerminated() {
        return terminated;
    }

    public synchronized void terminate() {
        terminated = true;
        notifyAll();
        if (worker != null) {
            worker.interrupt();
            stateDaemon.interrupt();
        }
    }

    public void awaitTermination() throws InterruptedException {
        if (worker != null) {
            worker.join();
            stateDaemon.join();
        }
    }

    @Override
    public void run() {
        try {
            synchronized (this) {
                while (!terminated) {
                    while (!terminated && state != ProgramState.RUNNING) {
                        wait();
                    }
                    if (!terminated) {
                        wait(intervalMillis);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void changeRandomly() {
        try {
            while (!isTerminated()) {
                Thread.sleep(intervalMillis);
                changeState(randomState.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
