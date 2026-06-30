package de.htwg.sysarch.elevator.infrastructure.mqtt;

import de.htwg.sysarch.elevator.domain.control.ElevatorEvent;
import de.htwg.sysarch.elevator.domain.control.ElevatorStatus;
import de.htwg.sysarch.elevator.domain.control.Phase;
import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.DoorState;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.mqtt.EventMessage;
import de.htwg.sysarch.mqtt.JsonCodec;
import de.htwg.sysarch.mqtt.MqttTopics;
import de.htwg.sysarch.mqtt.StatusMessage;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The control→HMI adapter maps domain state onto the MQTT contract — verified without a broker. */
class MqttHmiGatewayTest {

    private final RecordingConnection conn = new RecordingConnection();
    private final MqttTopics topics = new MqttTopics("elevator/e");
    private final MqttHmiGateway gateway = new MqttHmiGateway(conn, topics);

    @Test
    void publishesStatusAsRetainedJsonOnStatusTopic() {
        ElevatorStatus status = new ElevatorStatus(
                Level.LEVEL_2, Direction.UP, Phase.MOVING, DoorState.CLOSED, 100,
                false, false,
                EnumSet.of(Level.LEVEL_4), EnumSet.of(Level.LEVEL_2), EnumSet.noneOf(Level.class), 3500);

        gateway.publish(status);

        assertEquals(1, conn.published.size());
        Published p = conn.published.get(0);
        assertEquals("elevator/e/status", p.topic);
        assertTrue(p.retained, "status should be retained for late-joining HMIs");

        StatusMessage msg = JsonCodec.fromJson(p.payload, StatusMessage.class);
        assertEquals(2, msg.level());
        assertEquals("UP", msg.direction());
        assertEquals("MOVING", msg.phase());
        assertEquals("CLOSED", msg.door());
        assertEquals(100, msg.velocity());
        assertEquals(List.of(4), msg.cabinCalls());
        assertEquals(List.of(2), msg.hallUpCalls());
        assertTrue(msg.hallDownCalls().isEmpty());
    }

    @Test
    void doesNotRepublishUnchangedStatus() {
        ElevatorStatus status = new ElevatorStatus(
                Level.LEVEL_1, Direction.NONE, Phase.IDLE, DoorState.CLOSED, 0,
                false, false, EnumSet.noneOf(Level.class), EnumSet.noneOf(Level.class), EnumSet.noneOf(Level.class), 0);

        gateway.publish(status);
        gateway.publish(status);

        assertEquals(1, conn.published.size(), "unchanged status must not flood the broker");
    }

    @Test
    void publishesEventAsNonRetainedJsonOnEventTopic() {
        gateway.log(ElevatorEvent.of(ElevatorEvent.Type.ARRIVAL, Level.LEVEL_3));

        Published p = conn.published.get(0);
        assertEquals("elevator/e/event", p.topic);
        assertFalse(p.retained, "events are a stream, not retained");
        EventMessage msg = JsonCodec.fromJson(p.payload, EventMessage.class);
        assertEquals("ARRIVAL", msg.type());
        assertEquals(3, msg.level());
    }

    // ------------------------------------------------------------- fakes

    private static final class Published {
        final String topic;
        final String payload;
        final boolean retained;

        Published(String topic, String payload, boolean retained) {
            this.topic = topic;
            this.payload = payload;
            this.retained = retained;
        }
    }

    private static final class RecordingConnection implements MqttConnection {
        private final java.util.List<Published> published = new java.util.ArrayList<>();

        @Override
        public void connect() {
        }

        @Override
        public void publish(String topic, String payload, boolean retained) {
            published.add(new Published(topic, payload, retained));
        }

        @Override
        public void subscribe(String topicFilter, MessageHandler handler) {
        }

        @Override
        public void close() {
        }
    }
}
