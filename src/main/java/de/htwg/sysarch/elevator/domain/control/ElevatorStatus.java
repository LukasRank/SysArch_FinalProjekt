package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.DoorState;
import de.htwg.sysarch.elevator.domain.model.Level;

import java.util.Set;

/**
 * Immutable, HMI-facing view of the elevator state (assignment §1.7 (6)).
 * Read-only for the HMI; the HMI never writes state, it issues commands via the
 * {@code OperatorPanel} driving port (assignment §1.3 — one-directional interface).
 */
public record ElevatorStatus(
        Level currentLevel,
        Direction travelDirection,
        Phase phase,
        DoorState door,
        int velocityCmPerS,
        boolean emergencyActive,
        boolean motorError,
        Set<Level> cabinCalls,
        Set<Level> hallUpCalls,
        Set<Level> hallDownCalls) {
}
