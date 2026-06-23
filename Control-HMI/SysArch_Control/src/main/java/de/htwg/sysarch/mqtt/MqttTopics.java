package de.htwg.sysarch.mqtt;

/**
 * The MQTT topic scheme shared by the control system and the HMI — the only
 * thing both sides agree on. Given a base (e.g. {@code "elevator/e"}):
 *
 * <ul>
 *   <li>{@code <base>/status} — control → HMI, retained {@link StatusMessage} (read-only state)</li>
 *   <li>{@code <base>/event}  — control → HMI, {@link EventMessage} stream (arrivals, doors, alarms)</li>
 *   <li>{@code <base>/cmd}    — HMI → control, {@link CommandMessage} (calls, emergency, reset)</li>
 * </ul>
 *
 * <p>This mirrors the one-directional interface rule (assignment §1.3): state
 * flows out on {@code status}/{@code event}, commands flow in on {@code cmd};
 * the two never share a topic.
 */
public final class MqttTopics {

    private final String base;

    public MqttTopics(String base) {
        String b = base.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (b.isEmpty()) {
            throw new IllegalArgumentException("base topic must not be empty");
        }
        this.base = b;
    }

    public String base() {
        return base;
    }

    /** Retained elevator state, control → HMI. */
    public String status() {
        return base + "/status";
    }

    /** Event stream, control → HMI. */
    public String event() {
        return base + "/event";
    }

    /** Command channel, HMI → control. */
    public String command() {
        return base + "/cmd";
    }
}
