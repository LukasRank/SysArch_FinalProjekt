package de.htwg.sysarch.elevator.domain.model;

/** Observed door state, derived from the door sensors (PIs_dopened / PIs_dclosed). */
public enum DoorState {
    OPEN,
    CLOSED,
    MOVING,
    UNKNOWN;

    public static DoorState from(boolean opened, boolean closed) {
        if (opened && closed) {
            return UNKNOWN; // inconsistent sensor reading
        }
        if (opened) {
            return OPEN;
        }
        if (closed) {
            return CLOSED;
        }
        return MOVING;
    }
}
