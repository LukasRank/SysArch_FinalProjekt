package de.htwg.sysarch.elevator.application.port.in;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.Level;

/**
 * Driving port: the HMI operates the elevator through this interface (assignment §1.7 (1)).
 * Write-only into the control system — state is read back via the {@code HmiGateway} /
 * {@code ElevatorStatus} (assignment §1.3, one-directional interface variables).
 */
public interface OperatorPanel {

    /** Cabin panel: choose a target level (§1.6 (7)). */
    void pressCabinButton(Level target);

    /** Outside up/down panel: request travel in a direction from a level (§1.6 (6)). */
    void pressHallButton(Level level, Direction direction);

    /** Cabin emergency-stop button (§1.6 (12)). */
    void engageEmergencyStop();

    /** Supervisory: clear the emergency state (§1.6 (12)). */
    void resetEmergencyStop();

    /** Supervisory: pulse the simulation/error reset (POreset, §4.1). */
    void resetSimulation();
}
