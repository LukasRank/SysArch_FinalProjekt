package de.htwg.sysarch.elevator.application;

import de.htwg.sysarch.elevator.application.port.in.OperatorPanel;
import de.htwg.sysarch.elevator.application.port.out.HmiGateway;
import de.htwg.sysarch.elevator.application.port.out.PlcGateway;
import de.htwg.sysarch.elevator.domain.control.CycleResult;
import de.htwg.sysarch.elevator.domain.control.ElevatorController;
import de.htwg.sysarch.elevator.domain.control.RequestStore;
import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.HallCall;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.elevator.domain.model.PlcInputs;
import de.htwg.sysarch.elevator.domain.model.PlcOutputs;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Orchestrates one control cycle and implements the {@link OperatorPanel} driving port.
 * Operator commands arrive from the HMI thread and are queued, then applied at the start
 * of the next cycle so the controller is only ever touched by the control-loop thread.
 */
public final class ElevatorControlService implements OperatorPanel {

    private final PlcGateway plc;
    private final HmiGateway hmi;
    private final ElevatorController controller = new ElevatorController();
    private final RequestStore requests = new RequestStore();
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private volatile boolean resetPending;

    public ElevatorControlService(PlcGateway plc, HmiGateway hmi) {
        this.plc = plc;
        this.hmi = hmi;
    }

    /** One control cycle: apply queued commands, read PLC, step controller, write outputs, publish. */
    public void cycle(long now) throws Exception {
        Runnable command;
        while ((command = commands.poll()) != null) {
            command.run();
        }

        PlcInputs inputs = plc.read();
        CycleResult result = controller.step(inputs, requests, now);

        PlcOutputs outputs = result.outputs();
        if (resetPending) {
            outputs = outputs.withReset(true);
            resetPending = false;
        }
        plc.write(outputs);

        result.events().forEach(hmi::log);
        hmi.publish(result.status());
    }

    // ---------------------------------------------- OperatorPanel (HMI thread)

    @Override
    public void pressCabinButton(Level target) {
        commands.add(() -> requests.addCabinCall(target));
    }

    @Override
    public void pressHallButton(Level level, Direction direction) {
        commands.add(() -> requests.addHallCall(new HallCall(level, direction)));
    }

    @Override
    public void engageEmergencyStop() {
        commands.add(controller::engageEmergency);
    }

    @Override
    public void resetEmergencyStop() {
        commands.add(controller::resetEmergency);
    }

    @Override
    public void resetSimulation() {
        resetPending = true;
    }
}
