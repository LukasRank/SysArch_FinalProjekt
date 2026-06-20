package de.htwg.sysarch.elevator.infrastructure.modbus;

import de.htwg.sysarch.elevator.application.port.out.PlcGateway;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.elevator.domain.model.LevelSensors;
import de.htwg.sysarch.elevator.domain.model.PlcInputs;
import de.htwg.sysarch.elevator.domain.model.PlcOutputs;

import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_DOOR_CLOSE;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_DOOR_OPEN;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_DOWN_V1;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_DOWN_V2;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_RESET;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_UP_V1;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.COIL_UP_V2;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.DI_DOOR_CLOSED;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.DI_DOOR_OPENED;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.DI_MOTOR_ERROR;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.DI_MOTOR_ON;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.DI_MOTOR_READY;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.REG_AUFZUG_ID;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.REG_CRAWL_SELECT;
import static de.htwg.sysarch.elevator.infrastructure.modbus.ModbusIoMap.REG_VELOCITY;

/**
 * {@link PlcGateway} implementation over Modbus/TCP. Translates between the typed
 * domain snapshots and the raw Modbus addresses defined in {@link ModbusIoMap}.
 * The underlying client is abstracted by {@link ModbusConnection}.
 */
public final class ModbusPlcGateway implements PlcGateway {

    private final ModbusConnection connection;

    public ModbusPlcGateway(ModbusConnection connection) {
        this.connection = connection;
    }

    @Override
    public void connect() throws Exception {
        connection.connect();
    }

    @Override
    public PlcInputs read() throws Exception {
        boolean[] di = connection.readDiscreteInputs(0, DI_MOTOR_ERROR + 1);
        int[] regs = connection.readInputRegisters(0, REG_VELOCITY + 1);

        PlcInputs.Builder b = PlcInputs.builder();
        for (Level l : Level.values()) {
            b.sensors(l, new LevelSensors(
                    bit(di, ModbusIoMap.approachLower(l)),
                    bit(di, ModbusIoMap.safetyLower(l)),
                    bit(di, ModbusIoMap.reached(l)),
                    bit(di, ModbusIoMap.safetyUpper(l)),
                    bit(di, ModbusIoMap.approachUpper(l))));
        }
        return b.doorOpened(bit(di, DI_DOOR_OPENED))
                .doorClosed(bit(di, DI_DOOR_CLOSED))
                .motorReady(bit(di, DI_MOTOR_READY))
                .motorOn(bit(di, DI_MOTOR_ON))
                .motorError(bit(di, DI_MOTOR_ERROR))
                .velocityCmPerS((short) reg(regs, REG_VELOCITY))
                .aufzugId(reg(regs, REG_AUFZUG_ID))
                .build();
    }

    @Override
    public void write(PlcOutputs o) throws Exception {
        connection.writeCoil(COIL_UP_V2, o.upV2());
        connection.writeCoil(COIL_UP_V1, o.upV1());
        connection.writeCoil(COIL_DOWN_V1, o.downV1());
        connection.writeCoil(COIL_DOWN_V2, o.downV2());
        connection.writeCoil(COIL_DOOR_OPEN, o.doorOpen());
        connection.writeCoil(COIL_DOOR_CLOSE, o.doorClose());
        connection.writeCoil(COIL_RESET, o.reset());
        connection.writeRegister(REG_CRAWL_SELECT, o.crawlCmPerS());
    }

    @Override
    public void close() {
        connection.close();
    }

    private static boolean bit(boolean[] values, int index) {
        return index >= 0 && index < values.length && values[index];
    }

    private static int reg(int[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0;
    }
}
