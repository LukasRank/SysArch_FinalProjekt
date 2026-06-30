package de.htwg.sysarch.mqtt;

/**
 * Wire DTO for a discrete event published on {@code <base>/event} (control → HMI).
 * {@code type} is the control system's event name (ARRIVAL, DOOR_OPENED, ALARM, …),
 * {@code level} is the 1..4 level number, {@code detail} an optional free-text note.
 *
 * <p>Field names are the JSON keys (consumed by Gson); keep them stable across both modules.
 */
public final class EventMessage {

    private String type;
    private int level;
    private String detail;

    public EventMessage(String type, int level, String detail) {
        this.type = type;
        this.level = level;
        this.detail = detail;
    }

    public String type() { return type; }
    public int level() { return level; }
    public String detail() { return detail; }
}
