package de.htwg.sysarch.elevator.domain.model;

import java.util.Objects;

/**
 * Snapshot of the cabin-detection sensors around one level (assignment §1.6 (3),(4), §4.2).
 * Offsets relative to the level: al = -1.5 m, sl = -50 mm, r = 0 mm (door-safe),
 * su = +50 mm, au = +1.5 m. Level 1 has no {@code al}; level 4 has no {@code au}.
 */
public final class LevelSensors {

    public static final LevelSensors NONE = new LevelSensors(false, false, false, false, false);

    private final boolean approachLower;
    private final boolean safetyLower;
    private final boolean reached;
    private final boolean safetyUpper;
    private final boolean approachUpper;

    public LevelSensors(
            boolean approachLower,
            boolean safetyLower,
            boolean reached,
            boolean safetyUpper,
            boolean approachUpper) {
        this.approachLower = approachLower;
        this.safetyLower = safetyLower;
        this.reached = reached;
        this.safetyUpper = safetyUpper;
        this.approachUpper = approachUpper;
    }

    public boolean approachLower() { return approachLower; }
    public boolean safetyLower() { return safetyLower; }
    public boolean reached() { return reached; }
    public boolean safetyUpper() { return safetyUpper; }
    public boolean approachUpper() { return approachUpper; }

    /** Approach sensor on the side from which the cabin arrives when travelling in {@code d}. */
    public boolean approachFrom(Direction d) {
        switch (d) {
            case UP:
                return approachLower;
            case DOWN:
                return approachUpper;
            default:
                return false;
        }
    }

    /** Safety sensor on the side from which the cabin arrives when travelling in {@code d}. */
    public boolean safetyFrom(Direction d) {
        switch (d) {
            case UP:
                return safetyLower;
            case DOWN:
                return safetyUpper;
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LevelSensors)) {
            return false;
        }
        LevelSensors that = (LevelSensors) o;
        return approachLower == that.approachLower && safetyLower == that.safetyLower
                && reached == that.reached && safetyUpper == that.safetyUpper
                && approachUpper == that.approachUpper;
    }

    @Override
    public int hashCode() {
        return Objects.hash(approachLower, safetyLower, reached, safetyUpper, approachUpper);
    }

    @Override
    public String toString() {
        return "LevelSensors[approachLower=" + approachLower + ", safetyLower=" + safetyLower
                + ", reached=" + reached + ", safetyUpper=" + safetyUpper
                + ", approachUpper=" + approachUpper + "]";
    }
}
