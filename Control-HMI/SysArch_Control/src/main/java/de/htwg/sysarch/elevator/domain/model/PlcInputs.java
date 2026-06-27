package de.htwg.sysarch.elevator.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable snapshot of all PLC inputs read in one control cycle (assignment §4.1). */
public final class PlcInputs {

    private final Map<Level, LevelSensors> levels;
    private final boolean doorOpened;
    private final boolean doorClosed;
    private final boolean motorReady;
    private final boolean motorOn;
    private final boolean motorError;
    private final int velocityCmPerS;
    private final long plcCycles;
    private final int aufzugId;

    public PlcInputs(
            Map<Level, LevelSensors> levels,
            boolean doorOpened,
            boolean doorClosed,
            boolean motorReady,
            boolean motorOn,
            boolean motorError,
            int velocityCmPerS,
            long plcCycles,
            int aufzugId) {
        this.levels = levels;
        this.doorOpened = doorOpened;
        this.doorClosed = doorClosed;
        this.motorReady = motorReady;
        this.motorOn = motorOn;
        this.motorError = motorError;
        this.velocityCmPerS = velocityCmPerS;
        this.plcCycles = plcCycles;
        this.aufzugId = aufzugId;
    }

    public Map<Level, LevelSensors> levels() { return levels; }
    public boolean doorOpened() { return doorOpened; }
    public boolean doorClosed() { return doorClosed; }
    public boolean motorReady() { return motorReady; }
    public boolean motorOn() { return motorOn; }
    public boolean motorError() { return motorError; }
    public int velocityCmPerS() { return velocityCmPerS; }
    public long plcCycles() { return plcCycles; }
    public int aufzugId() { return aufzugId; }

    public LevelSensors at(Level level) {
        return levels.getOrDefault(level, LevelSensors.NONE);
    }

    public DoorState doorState() {
        return DoorState.from(doorOpened, doorClosed);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder; convenient for the simulation adapter and tests. */
    public static final class Builder {
        private final Map<Level, LevelSensors> levels = new EnumMap<>(Level.class);
        private boolean doorOpened;
        private boolean doorClosed;
        private boolean motorReady;
        private boolean motorOn;
        private boolean motorError;
        private int velocityCmPerS;
        private long plcCycles;
        private int aufzugId;

        public Builder sensors(Level level, LevelSensors sensors) {
            levels.put(level, sensors);
            return this;
        }

        public Builder reached(Level level) {
            return sensors(level, new LevelSensors(false, false, true, false, false));
        }

        public Builder doorOpened(boolean v) { this.doorOpened = v; return this; }
        public Builder doorClosed(boolean v) { this.doorClosed = v; return this; }
        public Builder motorReady(boolean v) { this.motorReady = v; return this; }
        public Builder motorOn(boolean v) { this.motorOn = v; return this; }
        public Builder motorError(boolean v) { this.motorError = v; return this; }
        public Builder velocityCmPerS(int v) { this.velocityCmPerS = v; return this; }
        public Builder plcCycles(long v) { this.plcCycles = v; return this; }
        public Builder aufzugId(int v) { this.aufzugId = v; return this; }

        public PlcInputs build() {
            return new PlcInputs(
                    Collections.unmodifiableMap(new EnumMap<>(levels)),
                    doorOpened, doorClosed, motorReady, motorOn, motorError,
                    velocityCmPerS, plcCycles, aufzugId);
        }
    }
}
