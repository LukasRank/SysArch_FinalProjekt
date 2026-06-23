package de.htwg.sysarch.elevator.domain.model;

/** A call from outside the cabin: at {@code level}, wanting to travel {@code direction} (§1.6 (6)). */
public record HallCall(Level level, Direction direction) {

    public HallCall {
        if (direction != Direction.UP && direction != Direction.DOWN) {
            throw new IllegalArgumentException("hall call direction must be UP or DOWN, was " + direction);
        }
    }
}
