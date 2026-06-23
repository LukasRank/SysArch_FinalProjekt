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
 */
public record StatusMessage(
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
}
