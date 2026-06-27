package de.htwg.sysarch.elevator.infrastructure.mqtt;

import de.htwg.sysarch.elevator.application.port.in.OperatorPanel;
import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.mqtt.CommandMessage;
import de.htwg.sysarch.mqtt.JsonCodec;

import java.util.logging.Logger;

/**
 * Driving-port adapter (HMI → control): parses {@link CommandMessage}s arriving on the
 * MQTT command topic and translates them into {@code OperatorPanel} calls. Invalid or
 * malformed commands are logged and dropped — a bad HMI message must never crash or
 * stall the control system.
 *
 * <p>Register with {@code connection.subscribe(topics.command(), router::onMessage)}.
 */
public final class MqttCommandRouter {

    private static final Logger LOG = Logger.getLogger(MqttCommandRouter.class.getName());

    private final OperatorPanel panel;

    public MqttCommandRouter(OperatorPanel panel) {
        this.panel = panel;
    }

    /** {@link MqttConnection.MessageHandler} entry point. */
    public void onMessage(String topic, String payload) {
        CommandMessage cmd;
        try {
            cmd = JsonCodec.fromJson(payload, CommandMessage.class);
        } catch (RuntimeException e) {
            LOG.warning("dropping invalid command JSON: " + payload);
            return;
        }
        if (cmd == null || cmd.type() == null) {
            LOG.warning("dropping command without type: " + payload);
            return;
        }
        try {
            dispatch(cmd);
        } catch (RuntimeException e) {
            LOG.warning("cannot apply command " + payload + ": " + e.getMessage());
        }
    }

    private void dispatch(CommandMessage cmd) {
        switch (cmd.type()) {
            case CommandMessage.CABIN:
                panel.pressCabinButton(Level.ofNumber(required(cmd.level(), "level")));
                break;
            case CommandMessage.HALL:
                panel.pressHallButton(
                        Level.ofNumber(required(cmd.level(), "level")),
                        Direction.valueOf(required(cmd.direction(), "direction")));
                break;
            case CommandMessage.EMERGENCY:
                if (CommandMessage.CLEAR.equals(cmd.action())) {
                    panel.resetEmergencyStop();
                } else {
                    panel.engageEmergencyStop();
                }
                break;
            case CommandMessage.RESET:
                panel.resetSimulation();
                break;
            default:
                LOG.warning("unknown command type: " + cmd.type());
        }
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("missing field: " + name);
        }
        return value;
    }
}
