package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Level;

import java.util.Objects;

/**
 * A discrete event emitted by the controller, for HMI logging
 * (assignment §1.7 (2),(3),(4): stops, door states, alarms, modes).
 */
public final class ElevatorEvent {

    public enum Type {
        ARRIVAL,
        DEPARTURE,
        DOOR_OPENING,
        DOOR_OPENED,
        DOOR_CLOSING,
        DOOR_CLOSED,
        MODE_CHANGE,
        ALARM,
        EMERGENCY_ENGAGED,
        EMERGENCY_RESET
    }

    private final Type type;
    private final Level level;
    private final String detail;

    public ElevatorEvent(Type type, Level level, String detail) {
        this.type = type;
        this.level = level;
        this.detail = detail;
    }

    public Type type() { return type; }
    public Level level() { return level; }
    public String detail() { return detail; }

    public static ElevatorEvent of(Type type, Level level) {
        return new ElevatorEvent(type, level, "");
    }

    public static ElevatorEvent of(Type type, Level level, String detail) {
        return new ElevatorEvent(type, level, detail);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElevatorEvent)) {
            return false;
        }
        ElevatorEvent that = (ElevatorEvent) o;
        return type == that.type && level == that.level && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, level, detail);
    }

    @Override
    public String toString() {
        return "ElevatorEvent[type=" + type + ", level=" + level + ", detail=" + detail + "]";
    }
}
