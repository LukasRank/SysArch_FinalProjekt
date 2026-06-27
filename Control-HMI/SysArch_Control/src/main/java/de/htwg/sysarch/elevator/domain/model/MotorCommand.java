package de.htwg.sysarch.elevator.domain.model;

import java.util.Objects;

/**
 * A motor actuation command: the discrete {@link Drive} (the four motor coils)
 * plus the crawl register value (-5..5 cm/s, positive = up).
 *
 * <p>Crawl magnitude for fine adjustment is {@link #CRAWL_CM_PER_S}; this is an
 * assumption to be tuned against the simulation (see CLAUDE.md §9).
 */
public final class MotorCommand {

    public static final int CRAWL_CM_PER_S = 5;
    public static final MotorCommand OFF = new MotorCommand(Drive.OFF, 0);

    private final Drive drive;
    private final int crawlCmPerS;

    public MotorCommand(Drive drive, int crawlCmPerS) {
        if (crawlCmPerS < -5 || crawlCmPerS > 5) {
            throw new IllegalArgumentException("crawl speed out of range [-5,5]: " + crawlCmPerS);
        }
        this.drive = drive;
        this.crawlCmPerS = crawlCmPerS;
    }

    public Drive drive() {
        return drive;
    }

    public int crawlCmPerS() {
        return crawlCmPerS;
    }

    /** Build a command for a direction/speed pair. */
    public static MotorCommand drive(Direction d, Speed s) {
        switch (s) {
            case OFF:
                return OFF;
            case CRAWL:
                return new MotorCommand(Drive.OFF, crawlFor(d));
            case V1:
            case V2:
                return new MotorCommand(Drive.of(d, s), 0);
            default:
                throw new IllegalArgumentException("unknown speed: " + s);
        }
    }

    private static int crawlFor(Direction d) {
        switch (d) {
            case UP:
                return CRAWL_CM_PER_S;
            case DOWN:
                return -CRAWL_CM_PER_S;
            case NONE:
                return 0;
            default:
                throw new IllegalArgumentException("unknown direction: " + d);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MotorCommand)) {
            return false;
        }
        MotorCommand that = (MotorCommand) o;
        return crawlCmPerS == that.crawlCmPerS && drive == that.drive;
    }

    @Override
    public int hashCode() {
        return Objects.hash(drive, crawlCmPerS);
    }

    @Override
    public String toString() {
        return "MotorCommand[drive=" + drive + ", crawlCmPerS=" + crawlCmPerS + "]";
    }
}
