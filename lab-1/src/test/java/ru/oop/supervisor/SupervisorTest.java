package ru.oop.supervisor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SupervisorTest {
    @Test
    void keepsEveryTransitionInOrder() throws InterruptedException {
        AbstractProgram program = new AbstractProgram(1000);
        List<ProgramState> expected = new ArrayList<>(List.of(ProgramState.UNKNOWN));
        for (int i = 0; i < 100; i++) {
            ProgramState state = i % 2 == 0 ? ProgramState.RUNNING : ProgramState.STOPPING;
            program.changeState(state);
            expected.add(state);
        }
        program.changeState(ProgramState.FATAL_ERROR);
        expected.add(ProgramState.FATAL_ERROR);
        program.changeState(ProgramState.RUNNING);
        for (ProgramState state : expected) {
            assertEquals(state, program.awaitState());
        }
        assertEquals(ProgramState.FATAL_ERROR, program.getState());
        program.terminate();
        assertNull(program.awaitState());
    }

    @Test
    @Timeout(5)
    void restartsAndFinishesAfterFatalError() throws InterruptedException {
        AtomicInteger steps = new AtomicInteger();
        AbstractProgram program = new AbstractProgram(10,
                () -> steps.getAndIncrement() == 0 ? ProgramState.STOPPING : ProgramState.FATAL_ERROR);
        Supervisor supervisor = new Supervisor(program);
        supervisor.start();
        try {
            supervisor.join(2000);
            assertFalse(supervisor.isAlive());
            assertTrue(program.isTerminated());
            assertEquals(ProgramState.FATAL_ERROR, program.getState());
            program.awaitTermination();
        } finally {
            program.terminate();
            supervisor.interrupt();
        }
    }

    @Test
    void validatesIntervalAndInitialState() {
        assertThrows(IllegalArgumentException.class, () -> new AbstractProgram(0));
        AbstractProgram program = new AbstractProgram(1000);
        assertThrows(IllegalArgumentException.class, () -> program.changeState(ProgramState.UNKNOWN));
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            program.terminate();
            assertEquals(ProgramState.UNKNOWN, program.awaitState());
            assertNull(program.awaitState());
        });
    }
}
