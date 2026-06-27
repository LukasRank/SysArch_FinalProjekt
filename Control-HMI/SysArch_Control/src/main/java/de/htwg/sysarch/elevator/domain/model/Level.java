package de.htwg.sysarch.elevator.domain.model;

import java.util.Optional;

/** The four levels the elevator serves (assignment §1.6 (1)). */
public enum Level {
    LEVEL_1,
    LEVEL_2,
    LEVEL_3,
    LEVEL_4;

    public static final int COUNT = 4;

    /** Human-readable level number 1..4. */
    public int number() {
        return ordinal() + 1;
    }

    public boolean isLowest() {
        return this == LEVEL_1;
    }

    public boolean isHighest() {
        return this == LEVEL_4;
    }

    public static Level ofNumber(int number) {
        if (number < 1 || number > COUNT) {
            throw new IllegalArgumentException("level number out of range [1,4]: " + number);
        }
        return values()[number - 1];
    }

    /** The adjacent level in travel direction {@code d}, or empty at a terminal level. */
    public Optional<Level> neighbour(Direction d) {
        switch (d) {
            case UP:
                return isHighest() ? Optional.empty() : Optional.of(values()[ordinal() + 1]);
            case DOWN:
                return isLowest() ? Optional.empty() : Optional.of(values()[ordinal() - 1]);
            default:
                return Optional.empty();
        }
    }
}
