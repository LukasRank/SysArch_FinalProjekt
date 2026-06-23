package de.htwg.sysarch.elevator.infrastructure.mqtt;

import de.htwg.sysarch.elevator.application.port.out.HmiGateway;
import de.htwg.sysarch.elevator.domain.control.ElevatorEvent;
import de.htwg.sysarch.elevator.domain.control.ElevatorStatus;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.mqtt.EventMessage;
import de.htwg.sysarch.mqtt.JsonCodec;
import de.htwg.sysarch.mqtt.MqttTopics;
import de.htwg.sysarch.mqtt.StatusMessage;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Driven-port adapter (control → HMI): maps the domain {@link ElevatorStatus} /
 * {@link ElevatorEvent} onto the shared MQTT contract DTOs and publishes them.
 *
 * <p>State is published <em>retained</em> on the status topic so a late-joining HMI
 * immediately gets the current state; events are a plain stream. Publishing is
 * de-duplicated (only on change) to avoid flooding the broker every ~100 ms.
 * A broker failure is logged and swallowed — the control loop must never block on
 * the HMI link.
 */
public final class MqttHmiGateway implements HmiGateway {

    private static final Logger LOG = Logger.getLogger(MqttHmiGateway.class.getName());

    private final MqttConnection mqtt;
    private final MqttTopics topics;
    private ElevatorStatus lastPublished;

    public MqttHmiGateway(MqttConnection mqtt, MqttTopics topics) {
        this.mqtt = mqtt;
        this.topics = topics;
    }

    @Override
    public void publish(ElevatorStatus status) {
        if (Objects.equals(status, lastPublished)) {
            return;
        }
        lastPublished = status;
        StatusMessage message = new StatusMessage(
                status.currentLevel().number(),
                status.travelDirection().name(),
                status.phase().name(),
                status.door().name(),
                status.velocityCmPerS(),
                status.emergencyActive(),
                status.motorError(),
                numbers(status.cabinCalls()),
                numbers(status.hallUpCalls()),
                numbers(status.hallDownCalls()));
        send(topics.status(), JsonCodec.toJson(message), true);
    }

    @Override
    public void log(ElevatorEvent event) {
        EventMessage message = new EventMessage(
                event.type().name(), event.level().number(), event.detail());
        send(topics.event(), JsonCodec.toJson(message), false);
    }

    private void send(String topic, String payload, boolean retained) {
        try {
            mqtt.publish(topic, payload, retained);
        } catch (Exception e) {
            LOG.warning(() -> "MQTT publish to " + topic + " failed: " + e.getMessage());
        }
    }

    private static List<Integer> numbers(Set<Level> levels) {
        return levels.stream().map(Level::number).sorted().collect(Collectors.toList());
    }
}
