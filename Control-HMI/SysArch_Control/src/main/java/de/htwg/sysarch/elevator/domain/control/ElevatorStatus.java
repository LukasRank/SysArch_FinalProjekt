package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.DoorState;
import de.htwg.sysarch.elevator.domain.model.Level;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable, HMI-facing view of the elevator state (assignment §1.7 (6)).
 * Read-only for the HMI; the HMI never writes state, it issues commands via the
 * {@code OperatorPanel} driving port (assignment §1.3 — one-directional interface).
 */
public final class ElevatorStatus {

    private final Level currentLevel;
    private final Direction travelDirection;
    private final Phase phase;
    private final DoorState door;
    private final int velocityCmPerS;
    private final boolean emergencyActive;
    private final boolean motorError;
    private final Set<Level> cabinCalls;
    private final Set<Level> hallUpCalls;
    private final Set<Level> hallDownCalls;

    public ElevatorStatus(
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
        this.currentLevel = currentLevel;
        this.travelDirection = travelDirection;
        this.phase = phase;
        this.door = door;
        this.velocityCmPerS = velocityCmPerS;
        this.emergencyActive = emergencyActive;
        this.motorError = motorError;
        this.cabinCalls = cabinCalls;
        this.hallUpCalls = hallUpCalls;
        this.hallDownCalls = hallDownCalls;
    }

    public Level currentLevel() { return currentLevel; }
    public Direction travelDirection() { return travelDirection; }
    public Phase phase() { return phase; }
    public DoorState door() { return door; }
    public int velocityCmPerS() { return velocityCmPerS; }
    public boolean emergencyActive() { return emergencyActive; }
    public boolean motorError() { return motorError; }
    public Set<Level> cabinCalls() { return cabinCalls; }
    public Set<Level> hallUpCalls() { return hallUpCalls; }
    public Set<Level> hallDownCalls() { return hallDownCalls; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElevatorStatus)) {
            return false;
        }
        ElevatorStatus that = (ElevatorStatus) o;
        return velocityCmPerS == that.velocityCmPerS && emergencyActive == that.emergencyActive
                && motorError == that.motorError && currentLevel == that.currentLevel
                && travelDirection == that.travelDirection && phase == that.phase && door == that.door
                && Objects.equals(cabinCalls, that.cabinCalls)
                && Objects.equals(hallUpCalls, that.hallUpCalls)
                && Objects.equals(hallDownCalls, that.hallDownCalls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentLevel, travelDirection, phase, door, velocityCmPerS,
                emergencyActive, motorError, cabinCalls, hallUpCalls, hallDownCalls);
    }

    @Override
    public String toString() {
        return "ElevatorStatus[currentLevel=" + currentLevel + ", travelDirection=" + travelDirection
                + ", phase=" + phase + ", door=" + door + ", velocityCmPerS=" + velocityCmPerS
                + ", emergencyActive=" + emergencyActive + ", motorError=" + motorError
                + ", cabinCalls=" + cabinCalls + ", hallUpCalls=" + hallUpCalls
                + ", hallDownCalls=" + hallDownCalls + "]";
    }
}
