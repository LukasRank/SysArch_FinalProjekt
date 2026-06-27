package de.htwg.sysarch.mqtt;

import java.util.List;

/**
 * Wire DTO for the elevator state published on {@code <base>/status} (control → HMI).
 *
 * <p>Deliberately built from primitives/strings only: the HMI depends on this
 * contract package alone and never on the control system's domain types, so the
 * two sides stay decoupled across the MQTT boundary. Levels are 1..4 numbers,
 * {@code direction}/{@code phase}/{@code door} are the enum names from the
 * control system (UP|DOWN|NONE, IDLE|MOVING|…, OPEN|CLOSED|MOVING|UNKNOWN).
 *
 * <p>Field names are the JSON keys (consumed by Gson); keep them stable across both modules.
 */
public final class StatusMessage {

    private final int level;
    private final String direction;
    private final String phase;
    private final String door;
    private final int velocity;
    private final boolean emergencyActive;
    private final boolean motorError;
    private final List<Integer> cabinCalls;
    private final List<Integer> hallUpCalls;
    private final List<Integer> hallDownCalls;

    public StatusMessage(
            int level,
            String direction,
            String phase,
            String door,
            int velocity,
            boolean emergencyActive,
            boolean motorError,
            List<Integer> cabinCalls,
            List<Integer> hallUpCalls,
            List<Integer> hallDownCalls) {
        this.level = level;
        this.direction = direction;
        this.phase = phase;
        this.door = door;
        this.velocity = velocity;
        this.emergencyActive = emergencyActive;
        this.motorError = motorError;
        this.cabinCalls = cabinCalls;
        this.hallUpCalls = hallUpCalls;
        this.hallDownCalls = hallDownCalls;
    }

    public int level() { return level; }
    public String direction() { return direction; }
    public String phase() { return phase; }
    public String door() { return door; }
    public int velocity() { return velocity; }
    public boolean emergencyActive() { return emergencyActive; }
    public boolean motorError() { return motorError; }
    public List<Integer> cabinCalls() { return cabinCalls; }
    public List<Integer> hallUpCalls() { return hallUpCalls; }
    public List<Integer> hallDownCalls() { return hallDownCalls; }
}
