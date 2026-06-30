package de.htwg.sysarch.mqtt;

/**
 * Wire DTO for a command published by the HMI on {@code <base>/cmd} (HMI → control).
 * One JSON object per command; unused fields are omitted/null.
 *
 * <pre>
 *   {"type":"cabin","level":3}
 *   {"type":"hall","level":2,"direction":"UP"}
 *   {"type":"emergency","action":"engage"}   // or "action":"clear"
 *   {"type":"reset"}
 * </pre>
 *
 * The control side maps these onto its {@code OperatorPanel} driving port; the HMI
 * builds them with the factory methods below so neither side hard-codes JSON.
 *
 * <p>Field names are the JSON keys (consumed by Gson); keep them stable across both modules.
 */
public final class CommandMessage {

    public static final String CABIN = "cabin";
    public static final String HALL = "hall";
    public static final String EMERGENCY = "emergency";
    public static final String RESET = "reset";

    /** {@code action} values for {@link #EMERGENCY}. */
    public static final String ENGAGE = "engage";
    public static final String CLEAR = "clear";

    private String type;
    private Integer level;
    private String direction;
    private String action;

    public CommandMessage(String type, Integer level, String direction, String action) {
        this.type = type;
        this.level = level;
        this.direction = direction;
        this.action = action;
    }

    public String type() { return type; }
    public Integer level() { return level; }
    public String direction() { return direction; }
    public String action() { return action; }

    public static CommandMessage cabin(int level) {
        return new CommandMessage(CABIN, level, null, null);
    }

    /** {@code direction} is "UP" or "DOWN". */
    public static CommandMessage hall(int level, String direction) {
        return new CommandMessage(HALL, level, direction, null);
    }

    public static CommandMessage emergencyEngage() {
        return new CommandMessage(EMERGENCY, null, null, ENGAGE);
    }

    public static CommandMessage emergencyClear() {
        return new CommandMessage(EMERGENCY, null, null, CLEAR);
    }

    public static CommandMessage reset() {
        return new CommandMessage(RESET, null, null, null);
    }
}
