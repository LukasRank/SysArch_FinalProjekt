package de.htwg.sysarch.mqtt;

/**
 * Wire DTO for a discrete event published on {@code <base>/event} (control → HMI).
 * {@code type} is the control system's event name (ARRIVAL, DOOR_OPENED, ALARM, …),
 * {@code level} is the 1..4 level number, {@code detail} an optional free-text note.
 */
public record EventMessage(String type, int level, String detail) {
}
