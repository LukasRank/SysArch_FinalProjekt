package de.htwg.sysarch.elevator.application.port.out;

import de.htwg.sysarch.elevator.domain.model.PlcInputs;
import de.htwg.sysarch.elevator.domain.model.PlcOutputs;

/**
 * Driven port to the elevator PLC: read sensor inputs and write actuator outputs.
 * Implemented by the Modbus adapter (real PLC) or the in-memory simulation.
 */
public interface PlcGateway extends AutoCloseable {

    void connect() throws Exception;

    PlcInputs read() throws Exception;

    void write(PlcOutputs outputs) throws Exception;

    @Override
    void close();
}
