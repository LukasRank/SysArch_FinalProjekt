package de.htwg.sysarch.elevator.infrastructure.hmi;

import de.htwg.sysarch.elevator.application.port.out.HmiGateway;
import de.htwg.sysarch.elevator.domain.control.ElevatorEvent;
import de.htwg.sysarch.elevator.domain.control.ElevatorStatus;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Placeholder HMI sink: logs status changes and events to the console. Replace with
 * the agreed transport adapter (MQTT/DB/…) once the HMI interface is fixed (CLAUDE.md §8).
 */
public final class LoggingHmiGateway implements HmiGateway {

    private static final Logger LOG = Logger.getLogger("HMI");

    private ElevatorStatus lastPublished;

    @Override
    public void publish(ElevatorStatus status) {
        if (!Objects.equals(status, lastPublished)) {
            LOG.info(() -> "STATUS level=" + status.currentLevel().number()
                    + " phase=" + status.phase()
                    + " dir=" + status.travelDirection()
                    + " door=" + status.door()
                    + " v=" + status.velocityCmPerS() + "cm/s"
                    + " cabin=" + status.cabinCalls()
                    + " up=" + status.hallUpCalls()
                    + " down=" + status.hallDownCalls());
            lastPublished = status;
        }
    }

    @Override
    public void log(ElevatorEvent event) {
        LOG.info(() -> "EVENT " + event.type() + " @level" + event.level().number()
                + (event.detail().isEmpty() ? "" : " (" + event.detail() + ")"));
    }
}
