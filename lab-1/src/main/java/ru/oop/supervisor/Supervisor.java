package ru.oop.supervisor;

public class Supervisor extends Thread {
    private final AbstractProgram program;

    public Supervisor(AbstractProgram program) {
        super("supervisor");
        this.program = program;
    }

    public void startProgram() {
        program.changeState(ProgramState.RUNNING);
    }

    public void stopProgram() {
        program.changeState(ProgramState.STOPPING);
    }

    @Override
    public void run() {
        try {
            program.launch();
            startProgram();
            ProgramState state;
            while ((state = program.awaitState()) != null) {
                System.out.println("Supervisor received: " + state);
                if (state == ProgramState.STOPPING) {
                    System.out.println("Supervisor is restarting the program");
                    startProgram();
                } else if (state == ProgramState.FATAL_ERROR) {
                    System.out.println("Supervisor is terminating the program after a fatal error");
                    break;
                }
            }
        } catch (InterruptedException e) {
            interrupt();
        } finally {
            program.terminate();
        }
    }
}
