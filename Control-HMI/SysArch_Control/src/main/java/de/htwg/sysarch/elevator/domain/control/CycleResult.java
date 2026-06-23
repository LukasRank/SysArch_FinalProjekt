package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.PlcOutputs;

import java.util.List;

/** Result of one control cycle: outputs to write, the resulting status, and emitted events. */
public record CycleResult(PlcOutputs outputs, ElevatorStatus status, List<ElevatorEvent> events) {
}
