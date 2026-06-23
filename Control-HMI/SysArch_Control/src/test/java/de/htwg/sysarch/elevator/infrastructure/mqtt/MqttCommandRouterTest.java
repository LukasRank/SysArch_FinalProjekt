package de.htwg.sysarch.elevator.infrastructure.mqtt;

import de.htwg.sysarch.elevator.application.port.in.OperatorPanel;
import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.mqtt.CommandMessage;
import de.htwg.sysarch.mqtt.JsonCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HMI→control adapter parses contract JSON into OperatorPanel calls — verified without a broker. */
class MqttCommandRouterTest {

    private final RecordingPanel panel = new RecordingPanel();
    private final MqttCommandRouter router = new MqttCommandRouter(panel);

    @Test
    void routesCabinCall() {
        router.onMessage("elevator/e/cmd", JsonCodec.toJson(CommandMessage.cabin(3)));
        assertEquals(List.of("cabin:LEVEL_3"), panel.calls);
    }

    @Test
    void routesHallCall() {
        router.onMessage("elevator/e/cmd", JsonCodec.toJson(CommandMessage.hall(2, "UP")));
        assertEquals(List.of("hall:LEVEL_2:UP"), panel.calls);
    }

    @Test
    void routesEmergencyEngageAndClear() {
        router.onMessage("elevator/e/cmd", JsonCodec.toJson(CommandMessage.emergencyEngage()));
        router.onMessage("elevator/e/cmd", JsonCodec.toJson(CommandMessage.emergencyClear()));
        assertEquals(List.of("emergency", "emergencyReset"), panel.calls);
    }

    @Test
    void routesReset() {
        router.onMessage("elevator/e/cmd", JsonCodec.toJson(CommandMessage.reset()));
        assertEquals(List.of("resetSim"), panel.calls);
    }

    @Test
    void dropsMalformedJsonWithoutCrashing() {
        router.onMessage("elevator/e/cmd", "not json {");
        router.onMessage("elevator/e/cmd", "{\"type\":\"cabin\"}"); // missing level
        assertTrue(panel.calls.isEmpty(), "bad commands must be ignored, not applied");
    }

    // ------------------------------------------------------------- fake

    private static final class RecordingPanel implements OperatorPanel {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void pressCabinButton(Level target) {
            calls.add("cabin:" + target);
        }

        @Override
        public void pressHallButton(Level level, Direction direction) {
            calls.add("hall:" + level + ":" + direction);
        }

        @Override
        public void engageEmergencyStop() {
            calls.add("emergency");
        }

        @Override
        public void resetEmergencyStop() {
            calls.add("emergencyReset");
        }

        @Override
        public void resetSimulation() {
            calls.add("resetSim");
        }
    }
}
