package de.htwg.sysarch.elevator.domain.model;

import java.util.Objects;

/** Immutable snapshot of all PLC outputs written in one control cycle (assignment §4.1). */
public final class PlcOutputs {

    public static final PlcOutputs IDLE =
            new PlcOutputs(false, false, false, false, false, false, 0, false);

    private final boolean upV2;
    private final boolean upV1;
    private final boolean downV1;
    private final boolean downV2;
    private final boolean doorOpen;
    private final boolean doorClose;
    private final int crawlCmPerS;
    private final boolean reset;

    public PlcOutputs(
            boolean upV2,
            boolean upV1,
            boolean downV1,
            boolean downV2,
            boolean doorOpen,
            boolean doorClose,
            int crawlCmPerS,
            boolean reset) {
        this.upV2 = upV2;
        this.upV1 = upV1;
        this.downV1 = downV1;
        this.downV2 = downV2;
        this.doorOpen = doorOpen;
        this.doorClose = doorClose;
        this.crawlCmPerS = crawlCmPerS;
        this.reset = reset;
    }

    public boolean upV2() { return upV2; }
    public boolean upV1() { return upV1; }
    public boolean downV1() { return downV1; }
    public boolean downV2() { return downV2; }
    public boolean doorOpen() { return doorOpen; }
    public boolean doorClose() { return doorClose; }
    public int crawlCmPerS() { return crawlCmPerS; }
    public boolean reset() { return reset; }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlcOutputs)) {
            return false;
        }
        PlcOutputs that = (PlcOutputs) o;
        return upV2 == that.upV2 && upV1 == that.upV1 && downV1 == that.downV1 && downV2 == that.downV2
                && doorOpen == that.doorOpen && doorClose == that.doorClose
                && crawlCmPerS == that.crawlCmPerS && reset == that.reset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(upV2, upV1, downV1, downV2, doorOpen, doorClose, crawlCmPerS, reset);
    }

    @Override
    public String toString() {
        return "PlcOutputs[upV2=" + upV2 + ", upV1=" + upV1 + ", downV1=" + downV1 + ", downV2=" + downV2
                + ", doorOpen=" + doorOpen + ", doorClose=" + doorClose + ", crawlCmPerS=" + crawlCmPerS
                + ", reset=" + reset + "]";
    }
}
