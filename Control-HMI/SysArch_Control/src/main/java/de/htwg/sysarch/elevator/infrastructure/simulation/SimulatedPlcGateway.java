package de.htwg.sysarch.elevator.infrastructure.simulation;

import de.htwg.sysarch.elevator.application.port.out.PlcGateway;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.elevator.domain.model.LevelSensors;
import de.htwg.sysarch.elevator.domain.model.PlcInputs;
import de.htwg.sysarch.elevator.domain.model.PlcOutputs;

/**
 * Lightweight in-memory model of the lab PLC for offline development and demos.
 *
 * <p>NOT a physically accurate twin: it reproduces the cabin position, the
 * approach → safety → reached sensor chain and the door open/close timing closely
 * enough to exercise the control logic without lab/VPN access. Replace with the
 * real {@code ModbusPlcGateway} for hardware runs.
 */
public final class SimulatedPlcGateway implements PlcGateway {

    private static final int LEVEL_HEIGHT_MM = 3500;
    private static final int TOP_MM = (Level.COUNT - 1) * LEVEL_HEIGHT_MM;

    // Sensor offsets relative to a level (al, sl, r, su, au) and their tolerances.
    private static final int[] OFFSET_MM = {-1500, -50, 0, 50, 1500};
    private static final int APPROACH_TOL_MM = 50;
    private static final int SAFETY_TOL_MM = 5;
    private static final int REACHED_TOL_MM = 5;

    private static final long DOOR_TRAVEL_MS = 1500;

    private enum Door {CLOSED, OPENING, OPEN, CLOSING}

    private double positionMm;          // 0 = level 1
    private long lastMillis = -1;
    private PlcOutputs last = PlcOutputs.IDLE;
    private Door door = Door.CLOSED;
    private long doorTimerEndsAt;

    @Override
    public void connect() {
        // nothing to connect for the simulation
    }

    @Override
    public void close() {
        // nothing to release
    }

    @Override
    public void write(PlcOutputs outputs) {
        this.last = outputs;
    }

    @Override
    public PlcInputs read() {
        long now = System.currentTimeMillis();
        double dt = lastMillis < 0 ? 0.0 : (now - lastMillis) / 1000.0;
        lastMillis = now;

        int velocity = velocityMmPerS(last);
        positionMm = clamp(positionMm + velocity * dt, 0, TOP_MM);
        updateDoor(now);

        PlcInputs.Builder b = PlcInputs.builder();
        for (Level l : Level.values()) {
            double rel = positionMm - l.ordinal() * (double) LEVEL_HEIGHT_MM;
            b.sensors(l, new LevelSensors(
                    near(rel, OFFSET_MM[0], APPROACH_TOL_MM),
                    near(rel, OFFSET_MM[1], SAFETY_TOL_MM),
                    near(rel, OFFSET_MM[2], REACHED_TOL_MM),
                    near(rel, OFFSET_MM[3], SAFETY_TOL_MM),
                    near(rel, OFFSET_MM[4], APPROACH_TOL_MM)));
        }
        return b.doorOpened(door == Door.OPEN)
                .doorClosed(door == Door.CLOSED)
                .motorReady(true)
                .motorOn(velocity != 0)
                .velocityCmPerS(velocity / 10)
                .build();
    }

    private static int velocityMmPerS(PlcOutputs o) {
        if (o.upV2()) {
            return 1000;
        }
        if (o.upV1()) {
            return 100;
        }
        if (o.downV1()) {
            return -100;
        }
        if (o.downV2()) {
            return -1000;
        }
        return o.crawlCmPerS() * 10; // crawl register: cm/s → mm/s
    }

    private void updateDoor(long now) {
        switch (door) {
            case CLOSED:
                if (last.doorOpen()) {
                    door = Door.OPENING;
                    doorTimerEndsAt = now + DOOR_TRAVEL_MS;
                }
                break;
            case OPENING:
                if (now >= doorTimerEndsAt) {
                    door = Door.OPEN;
                }
                break;
            case OPEN:
                if (last.doorClose()) {
                    door = Door.CLOSING;
                    doorTimerEndsAt = now + DOOR_TRAVEL_MS;
                }
                break;
            case CLOSING:
                if (last.doorOpen()) {
                    door = Door.OPENING;
                    doorTimerEndsAt = now + DOOR_TRAVEL_MS;
                } else if (now >= doorTimerEndsAt) {
                    door = Door.CLOSED;
                }
                break;
        }
    }

    private static boolean near(double rel, int offset, int tolerance) {
        return Math.abs(rel - offset) <= tolerance;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }
}
