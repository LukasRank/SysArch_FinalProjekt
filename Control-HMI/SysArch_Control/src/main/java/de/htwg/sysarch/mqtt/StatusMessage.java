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

    private int level;
    private String direction;
    private String phase;
    private String door;
    private int velocity;
    private boolean emergencyActive;
    private boolean motorError;
    private List<Integer> cabinCalls;
    private List<Integer> hallUpCalls;
    private List<Integer> hallDownCalls;
    private int positionMm;

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
            List<Integer> hallDownCalls,
            int positionMm) {
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
        this.positionMm = positionMm;
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
    public int positionMm() { return positionMm; }
}
