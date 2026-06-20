package de.htwg.sysarch.elevator.domain.model;

/** Immutable snapshot of all PLC outputs written in one control cycle (assignment §4.1). */
public record PlcOutputs(
        boolean upV2,
        boolean upV1,
        boolean downV1,
        boolean downV2,
        boolean doorOpen,
        boolean doorClose,
        int crawlCmPerS,
        boolean reset) {

    public static final PlcOutputs IDLE =
            new PlcOutputs(false, false, false, false, false, false, 0, false);

    /** Map a high-level motor + door command into the raw output coils/register. */
    public static PlcOutputs of(MotorCommand motor, DoorCommand door, boolean reset) {
        Drive d = motor.drive();
        return new PlcOutputs(
                d == Drive.UP_V2,
                d == Drive.UP_V1,
                d == Drive.DOWN_V1,
                d == Drive.DOWN_V2,
                door == DoorCommand.OPEN,
                door == DoorCommand.CLOSE,
                motor.crawlCmPerS(),
                reset);
    }

    public PlcOutputs withReset(boolean value) {
        return new PlcOutputs(upV2, upV1, downV1, downV2, doorOpen, doorClose, crawlCmPerS, value);
    }
}
