package de.htwg.sysarch.elevator.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Runtime configuration, loaded from {@code application.properties} (CLAUDE.md §7). */
public record ElevatorConfig(
        String modbusHost, int modbusPort, long cycleMillis, String group,
        String mqttHost, int mqttPort, String mqttBaseTopic) {

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
                p.getProperty("modbus.host", "ea-pc111.ei.htwg-konstanz.de"),
                Integer.parseInt(p.getProperty("modbus.port", "506")),
                Long.parseLong(p.getProperty("control.cycleMillis", "100")),
                group,
                p.getProperty("mqtt.host", "localhost"),
                Integer.parseInt(p.getProperty("mqtt.port", "1883")),
                p.getProperty("mqtt.baseTopic", "elevator/" + group));
    }

    /** Broker URL for the Paho client, e.g. {@code tcp://localhost:1883}. */
    public String mqttUrl() {
        return "tcp://" + mqttHost + ":" + mqttPort;
    }
}
