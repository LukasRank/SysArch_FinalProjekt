package de.htwg.sysarch.elevator.domain.model;

/**
 * The discrete motor drive state corresponding to the four motor coils
 * (POuv2, POuv1, POdv1, POdv2). Crawl is not a coil — it is set via the
 * crawl register while all four coils are off (see {@link MotorCommand}).
 */
public enum Drive {
    OFF(Direction.NONE, Speed.OFF),
    UP_V1(Direction.UP, Speed.V1),
    UP_V2(Direction.UP, Speed.V2),
    DOWN_V1(Direction.DOWN, Speed.V1),
    DOWN_V2(Direction.DOWN, Speed.V2);

    private final Direction direction;
    private final Speed speed;

    Drive(Direction direction, Speed speed) {
        this.direction = direction;
        this.speed = speed;
    }

    public Direction direction() {
        return direction;
    }

    public Speed speed() {
        return speed;
    }

    /** The drive for a direction/speed pair; OFF/CRAWL map to {@link #OFF} (no coil). */
    public static Drive of(Direction d, Speed s) {
        switch (s) {
            case OFF:
            case CRAWL:
                return OFF;
            case V1:
                return d == Direction.UP ? UP_V1 : d == Direction.DOWN ? DOWN_V1 : OFF;
            case V2:
                return d == Direction.UP ? UP_V2 : d == Direction.DOWN ? DOWN_V2 : OFF;
            default:
                throw new IllegalArgumentException("unknown speed: " + s);
        }
    }
}
