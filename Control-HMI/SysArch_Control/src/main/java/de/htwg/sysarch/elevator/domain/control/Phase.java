package de.htwg.sysarch.elevator.domain.control;

/** Operating phase of the elevator control state machine. */
public enum Phase {
    /** Stopped, doors closed, no active target. */
    IDLE,
    /** Travelling at v2 toward a target. */
    MOVING,
    /** Approach sensor reached → travelling at v1. */
    DECELERATING,
    /** Safety sensor reached → crawling to the door-safe position. */
    CRAWLING,
    /** Door opening at a stop. */
    DOOR_OPENING,
    /** Door open, minimum dwell time running. */
    DOOR_OPEN,
    /** Door closing before departure. */
    DOOR_CLOSING,
    /** Emergency stop engaged. */
    EMERGENCY
}
