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
import de.htwg.sysarch.elevator.infrastructure.simulation.SimulatedPlcGateway;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Composition root: wires configuration, the chosen PLC adapter, the HMI sink and the
 * control loop. Default mode is the in-memory simulation; pass {@code --modbus} to run
 * against the real lab PLC (requires the easymodbus wiring, CLAUDE.md §9).
 */
public final class ElevatorControlApplication {

    private static final Logger LOG = Logger.getLogger(ElevatorControlApplication.class.getName());

    private ElevatorControlApplication() {
    }

    public static void main(String[] args) throws Exception {
        ElevatorConfig config = ElevatorConfig.load();
        boolean useModbus = Arrays.asList(args).contains("--modbus");
        boolean interactive = Arrays.asList(args).contains("--interactive");

        PlcGateway plc = createGateway(useModbus, config);
        HmiGateway hmi = new LoggingHmiGateway();

        plc.connect();
        ElevatorControlService service = new ElevatorControlService(plc, hmi);

        if (interactive) {
            Thread console = new Thread(new InteractiveConsole(service), "console");
            console.setDaemon(true);
            console.start();
        } else if (!useModbus) {
            seedDemoRequests(service);
        }

        ControlLoop loop = new ControlLoop(service, config.cycleMillis());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            loop.stop();
            plc.close();
        }));
        LOG.info(() -> "Control loop started (cycle=" + config.cycleMillis() + "ms, mode="
                + (useModbus ? "modbus " + config.modbusHost() + ":" + config.modbusPort() : "simulation") + ")");
        loop.run();
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
