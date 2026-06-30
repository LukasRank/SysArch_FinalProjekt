package de.htwg.sysarch.elevator.infrastructure.modbus;

import de.re.easymodbus.modbusclient.ModbusClient;

/**
 * {@link ModbusConnection} implementation backed by the course-provided
 * easymodbus-maven library (gitlab.ei.htwg-konstanz.de, branch ei-main).
 *
 * The underlying {@link ModbusClient} is created once and reused across cycles.
 * All public methods may throw {@link Exception} as declared by the interface;
 * the caller ({@link ModbusPlcGateway}) propagates them to the control loop.
 */
public final class EasyModbusConnection implements ModbusConnection {

    private final String host;
    private final int port;
    private final ModbusClient client;

    public EasyModbusConnection(String host, int port) {
        this.host = host;
        this.port = port;
        this.client = new ModbusClient(host, port);
    }

    @Override
    public void connect() throws Exception {
        client.Connect();
    }

    @Override
    public boolean[] readDiscreteInputs(int startAddress, int count) throws Exception {
        return client.ReadDiscreteInputs(startAddress, count);
    }

    @Override
    public int[] readInputRegisters(int startAddress, int count) throws Exception {
        return client.ReadInputRegisters(startAddress, count);
    }

    @Override
    public void writeCoil(int address, boolean value) throws Exception {
        client.WriteSingleCoil(address, value);
    }

    @Override
    public void writeRegister(int address, int value) throws Exception {
        client.WriteSingleRegister(address, value);
    }

    @Override
    public void close() {
        try {
            if (client.isConnected()) {
                client.Disconnect();
            }
        } catch (Exception e) {
            // best-effort on shutdown
        }
    }

    @Override
    public String toString() {
        return "EasyModbusConnection{" + host + ":" + port + "}";
    }
}
