package de.htwg.sysarch.elevator;

import de.htwg.sysarch.elevator.application.ControlLoop;
import de.htwg.sysarch.elevator.application.ElevatorControlService;
import de.htwg.sysarch.elevator.application.port.out.HmiGateway;
import de.htwg.sysarch.elevator.application.port.out.PlcGateway;
import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.elevator.infrastructure.config.ElevatorConfig;
import de.htwg.sysarch.elevator.infrastructure.console.InteractiveConsole;
import de.htwg.sysarch.elevator.infrastructure.hmi.LoggingHmiGateway;
import de.htwg.sysarch.elevator.infrastructure.mqtt.MqttCommandRouter;
import de.htwg.sysarch.elevator.infrastructure.mqtt.MqttConnection;
import de.htwg.sysarch.elevator.infrastructure.mqtt.MqttHmiGateway;
import de.htwg.sysarch.elevator.infrastructure.mqtt.PahoMqttConnection;
import de.htwg.sysarch.elevator.infrastructure.simulation.SimulatedPlcGateway;
import de.htwg.sysarch.mqtt.MqttTopics;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Composition root: wires configuration, the chosen PLC adapter, the HMI transport and the
 * control loop. Default mode is the in-memory simulation; pass {@code --modbus} to run
 * against the real lab PLC (requires the easymodbus wiring, CLAUDE.md §9). Pass {@code --mqtt}
 * to expose the HMI over MQTT (CLAUDE.md §8) instead of the console-logging placeholder.
 */
public final class ElevatorControlApplication {

    private static final Logger LOG = Logger.getLogger(ElevatorControlApplication.class.getName());

    private ElevatorControlApplication() {
    }

    public static void main(String[] args) throws Exception {
        ElevatorConfig config = ElevatorConfig.load();
        List<String> flags = Arrays.asList(args);
        boolean useModbus = flags.contains("--modbus");
        boolean interactive = flags.contains("--interactive");
        boolean useMqtt = flags.contains("--mqtt");

        PlcGateway plc = createGateway(useModbus, config);
        plc.connect();

        // HMI transport: MQTT adapter if requested and reachable, else console logging.
        // The control system stays up regardless of HMI/broker availability.
        MqttConnection mqtt = useMqtt ? tryConnectMqtt(config) : null;
        MqttTopics topics = new MqttTopics(config.mqttBaseTopic());
        HmiGateway hmi = (mqtt != null) ? new MqttHmiGateway(mqtt, topics) : new LoggingHmiGateway();

        ElevatorControlService service = new ElevatorControlService(plc, hmi);

        // Command channel HMI → control (only with a live MQTT link).
        if (mqtt != null) {
            mqtt.subscribe(topics.command(), new MqttCommandRouter(service)::onMessage);
        }

        if (interactive) {
            Thread console = new Thread(new InteractiveConsole(service), "console");
            console.setDaemon(true);
            console.start();
        } else if (!useModbus && mqtt == null) {
            // Drive the demo from code only when nothing external can (no HMI, no modbus).
            seedDemoRequests(service);
        }

        ControlLoop loop = new ControlLoop(service, config.cycleMillis());
        final MqttConnection mqttToClose = mqtt;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            loop.stop();
            plc.close();
            if (mqttToClose != null) {
                mqttToClose.close();
            }
        }));
        String hmiMode = (mqtt != null) ? "mqtt " + config.mqttUrl() + " (" + config.mqttBaseTopic() + ")" : "console-log";
        LOG.info(() -> "Control loop started (cycle=" + config.cycleMillis() + "ms, plc="
                + (useModbus ? "modbus " + config.modbusHost() + ":" + config.modbusPort() : "simulation")
                + ", hmi=" + hmiMode + ")");
        loop.run();
    }

    /** Connect the MQTT HMI transport; on failure log and return null so the loop still runs. */
    private static MqttConnection tryConnectMqtt(ElevatorConfig config) {
        MqttConnection conn = new PahoMqttConnection(config.mqttUrl(), "elevator-control-" + System.nanoTime(),
                config.mqttUsername(), config.mqttPassword());
        try {
            conn.connect();
            return conn;
        } catch (Exception e) {
            conn.close();
            LOG.warning("MQTT broker not reachable at " + config.mqttUrl() + " (" + e.getMessage()
                    + ") — falling back to console HMI logging. Start a broker and restart for MQTT.");
            return null;
        }
    }

    private static PlcGateway createGateway(boolean useModbus, ElevatorConfig config) {
        if (!useModbus) {
            LOG.info("Using in-memory simulation (pass --modbus for the real PLC).");
            return new SimulatedPlcGateway();
        }
        // To enable: add the easymodbus dependency, implement ModbusConnection over it,
        // then return new ModbusPlcGateway(connection). See CLAUDE.md §9.
        throw new UnsupportedOperationException(
                "Modbus mode is not wired yet: add the easymodbus dependency and a ModbusConnection "
                        + "implementation, then construct ModbusPlcGateway. Target: "
                        + config.modbusHost() + ":" + config.modbusPort() + " (CLAUDE.md §9).");
    }

    /** Seed a couple of calls so the simulation demonstrates the SCAN behaviour. */
    private static void seedDemoRequests(ElevatorControlService service) {
        service.pressHallButton(Level.LEVEL_2, Direction.UP);
        service.pressCabinButton(Level.LEVEL_4);
    }
}
