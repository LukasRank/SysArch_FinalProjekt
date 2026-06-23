package de.htwg.sysarch.elevator.infrastructure.mqtt;

/**
 * Thin abstraction over an MQTT client (mirrors {@code ModbusConnection}): keeps the
 * concrete library — Eclipse Paho — swappable and makes the gateway/router unit-testable
 * with a fake, so no broker is needed for tests.
 */
public interface MqttConnection extends AutoCloseable {

    void connect() throws Exception;

    /** Publish a payload; {@code retained} keeps it as the topic's last-known value. */
    void publish(String topic, String payload, boolean retained) throws Exception;

    void subscribe(String topicFilter, MessageHandler handler) throws Exception;

    @Override
    void close();

    /** Receives messages for a subscribed filter. */
    @FunctionalInterface
    interface MessageHandler {
        void handle(String topic, String payload);
    }
}
