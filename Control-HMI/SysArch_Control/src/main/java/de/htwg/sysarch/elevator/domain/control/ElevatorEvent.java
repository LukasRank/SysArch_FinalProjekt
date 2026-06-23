package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Level;

/**
 * A discrete event emitted by the controller, for HMI logging
 * (assignment §1.7 (2),(3),(4): stops, door states, alarms, modes).
 */
public record ElevatorEvent(Type type, Level level, String detail) {

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

    public static ElevatorEvent of(Type type, Level level) {
        return new ElevatorEvent(type, level, "");
    }

    public static ElevatorEvent of(Type type, Level level, String detail) {
        return new ElevatorEvent(type, level, detail);
    }
}
