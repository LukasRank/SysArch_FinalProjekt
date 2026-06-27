package de.htwg.sysarch.elevator.domain.model;

import java.util.Objects;

/** A call from outside the cabin: at {@code level}, wanting to travel {@code direction} (§1.6 (6)). */
public final class HallCall {

    private final Level level;
    private final Direction direction;

    public HallCall(Level level, Direction direction) {
        if (direction != Direction.UP && direction != Direction.DOWN) {
            throw new IllegalArgumentException("hall call direction must be UP or DOWN, was " + direction);
        }
        this.level = level;
        this.direction = direction;
    }

    public Level level() { return level; }
    public Direction direction() { return direction; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HallCall)) {
            return false;
        }
        HallCall that = (HallCall) o;
        return level == that.level && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, direction);
    }

    @Override
    public String toString() {
        return "HallCall[level=" + level + ", direction=" + direction + "]";
    }
}
