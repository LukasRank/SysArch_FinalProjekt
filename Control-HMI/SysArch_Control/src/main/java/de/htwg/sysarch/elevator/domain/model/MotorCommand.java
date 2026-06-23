package de.htwg.sysarch.elevator.domain.model;

/**
 * A motor actuation command: the discrete {@link Drive} (the four motor coils)
 * plus the crawl register value (−5..5 cm/s, positive = up).
 *
 * <p>Crawl magnitude for fine adjustment is {@link #CRAWL_CM_PER_S}; this is an
 * assumption to be tuned against the simulation (see CLAUDE.md §9).
 */
public record MotorCommand(Drive drive, int crawlCmPerS) {

    public static final int CRAWL_CM_PER_S = 5;
    public static final MotorCommand OFF = new MotorCommand(Drive.OFF, 0);

    public MotorCommand {
        if (crawlCmPerS < -5 || crawlCmPerS > 5) {
            throw new IllegalArgumentException("crawl speed out of range [-5,5]: " + crawlCmPerS);
        }
    }

    /** Build a command for a direction/speed pair. */
    public static MotorCommand drive(Direction d, Speed s) {
        return switch (s) {
            case OFF -> OFF;
            case CRAWL -> new MotorCommand(Drive.OFF, crawlFor(d));
            case V1, V2 -> new MotorCommand(Drive.of(d, s), 0);
        };
    }

    private static int crawlFor(Direction d) {
        return switch (d) {
            case UP -> CRAWL_CM_PER_S;
            case DOWN -> -CRAWL_CM_PER_S;
            case NONE -> 0;
        };
    }
}
