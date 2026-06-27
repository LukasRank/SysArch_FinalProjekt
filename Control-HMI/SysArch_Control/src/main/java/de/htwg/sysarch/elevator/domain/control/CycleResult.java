package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.PlcOutputs;

import java.util.List;

/** Result of one control cycle: outputs to write, the resulting status, and emitted events. */
public final class CycleResult {

    private final PlcOutputs outputs;
    private final ElevatorStatus status;
    private final List<ElevatorEvent> events;

    public CycleResult(PlcOutputs outputs, ElevatorStatus status, List<ElevatorEvent> events) {
        this.outputs = outputs;
        this.status = status;
        this.events = events;
    }

    public PlcOutputs outputs() { return outputs; }
    public ElevatorStatus status() { return status; }
    public List<ElevatorEvent> events() { return events; }
}
