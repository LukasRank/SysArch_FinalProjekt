package de.htwg.sysarch.elevator.domain.model;

/**
 * Snapshot of the cabin-detection sensors around one level (assignment §1.6 (3),(4), §4.2).
 * Offsets relative to the level: al = −1.5 m, sl = −50 mm, r = 0 mm (door-safe),
 * su = +50 mm, au = +1.5 m. Level 1 has no {@code al}; level 4 has no {@code au}.
 */
public record LevelSensors(
        boolean approachLower,
        boolean safetyLower,
        boolean reached,
        boolean safetyUpper,
        boolean approachUpper) {

    public static final LevelSensors NONE = new LevelSensors(false, false, false, false, false);

    /** Approach sensor on the side from which the cabin arrives when travelling in {@code d}. */
    public boolean approachFrom(Direction d) {
        return switch (d) {
            case UP -> approachLower;
            case DOWN -> approachUpper;
            case NONE -> false;
        };
    }

    /** Safety sensor on the side from which the cabin arrives when travelling in {@code d}. */
    public boolean safetyFrom(Direction d) {
        return switch (d) {
            case UP -> safetyLower;
            case DOWN -> safetyUpper;
            case NONE -> false;
        };
    }
}
