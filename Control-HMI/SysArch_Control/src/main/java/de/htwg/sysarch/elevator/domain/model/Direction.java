package de.htwg.sysarch.elevator.domain.model;

/** Direction of travel. {@link #NONE} means the elevator is not committed to a direction. */
public enum Direction {
    UP(1),
    DOWN(-1),
    NONE(0);

    private final int sign;

    Direction(int sign) {
        this.sign = sign;
    }

    /** +1 for UP, -1 for DOWN, 0 for NONE. */
    public int sign() {
        return sign;
    }

    public Direction opposite() {
        switch (this) {
            case UP:
                return DOWN;
            case DOWN:
                return UP;
            default:
                return NONE;
        }
    }
}
