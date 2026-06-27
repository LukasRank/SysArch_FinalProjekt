package de.htwg.sysarch.elevator.infrastructure.console;

import de.htwg.sysarch.elevator.application.port.in.OperatorPanel;
import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 * Simple stdin command interface to drive the elevator interactively during a
 * simulation run (developer/demo tool). Runs on its own thread; commands are
 * forwarded to the thread-safe {@link OperatorPanel}.
 */
public final class InteractiveConsole implements Runnable {

    private static final Logger LOG = Logger.getLogger("CONSOLE");

    private final OperatorPanel panel;
    private volatile boolean running = true;

    public InteractiveConsole(OperatorPanel panel) {
        this.panel = panel;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        printHelp();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                handle(line.trim());
            }
        } catch (Exception e) {
            LOG.warning("console closed: " + e.getMessage());
        }
    }

    private void handle(String line) {
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+");
        try {
            switch (parts[0].toLowerCase()) {
                case "c":
                    panel.pressCabinButton(level(parts[1]));
                    break;
                case "u":
                    panel.pressHallButton(level(parts[1]), Direction.UP);
                    break;
                case "d":
                    panel.pressHallButton(level(parts[1]), Direction.DOWN);
                    break;
                case "e":
                    panel.engageEmergencyStop();
                    break;
                case "r":
                    panel.resetEmergencyStop();
                    break;
                case "x":
                    panel.resetSimulation();
                    break;
                case "h":
                case "?":
                    printHelp();
                    break;
                case "q":
                    running = false;
                    System.exit(0);
                    break;
                default:
                    LOG.info("unknown command: '" + line + "'  (type h for help)");
            }
        } catch (RuntimeException ex) {
            LOG.info("invalid command: '" + line + "'  (" + ex.getMessage() + ")");
        }
    }

    private static Level level(String token) {
        return Level.ofNumber(Integer.parseInt(token));
    }

    private void printHelp() {
        LOG.info("Interactive commands:\n"
                + "  c <1-4>  cabin call to level\n"
                + "  u <1-4>  hall call UP at level\n"
                + "  d <1-4>  hall call DOWN at level\n"
                + "  e        emergency stop\n"
                + "  r        reset emergency\n"
                + "  x        reset simulation (POreset)\n"
                + "  h        help\n"
                + "  q        quit");
    }
}
