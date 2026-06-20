package de.htwg.sysarch.elevator.application.port.out;

import de.htwg.sysarch.elevator.domain.control.ElevatorEvent;
import de.htwg.sysarch.elevator.domain.control.ElevatorStatus;

/**
 * Driven port to the HMI: publish the current elevator state and log events
 * (assignment §1.7 (2),(3),(4),(6)). The concrete transport (MQTT, DB, …) is an
 * adapter detail decided with the partner group (CLAUDE.md §8).
 */
public interface HmiGateway {

    void publish(ElevatorStatus status);

    void log(ElevatorEvent event);
}
