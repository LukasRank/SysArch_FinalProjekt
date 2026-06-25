package de.htwg.sysarch.elevator.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Runtime configuration, loaded from {@code application.properties} (CLAUDE.md §7). */
public record ElevatorConfig(
        String modbusHost, int modbusPort, long cycleMillis, String group,
        String mqttHost, int mqttPort, String mqttBaseTopic,
        String mqttUsername, String mqttPassword) {

    private static final String RESOURCE = "application.properties";

    public static ElevatorConfig load() {
        Properties p = new Properties();
        try (InputStream in = ElevatorConfig.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot load " + RESOURCE, e);
        }
        String group = p.getProperty("elevator.group", "e");
        return new ElevatorConfig(
                resolve(p, "modbus.host", "MODBUS_HOST", "ea-pc111.ei.htwg-konstanz.de"),
                Integer.parseInt(resolve(p, "modbus.port", "MODBUS_PORT", "506")),
                Long.parseLong(resolve(p, "control.cycleMillis", "CONTROL_CYCLE_MILLIS", "100")),
                group,
                resolve(p, "mqtt.host", "MQTT_HOST", "localhost"),
                Integer.parseInt(resolve(p, "mqtt.port", "MQTT_PORT", "1883")),
                resolve(p, "mqtt.baseTopic", "MQTT_BASE_TOPIC", "elevator/" + group),
                resolve(p, "mqtt.username", "MQTT_USERNAME", ""),
                resolve(p, "mqtt.password", "MQTT_PASSWORD", ""));
    }

    /**
     * Resolve a config value with override precedence: JVM system property
     * ({@code -Dkey=...}) &gt; environment variable &gt; {@code application.properties} &gt;
     * built-in default. Keeps secrets (broker password) out of the repository — set them
     * via {@code -Dmqtt.password=...} or the {@code MQTT_PASSWORD} env var at runtime.
     */
    private static String resolve(Properties p, String key, String envKey, String def) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return p.getProperty(key, def);
    }

    /** Broker URL for the Paho client, e.g. {@code tcp://localhost:1883}. */
    public String mqttUrl() {
        return "tcp://" + mqttHost + ":" + mqttPort;
    }
}
