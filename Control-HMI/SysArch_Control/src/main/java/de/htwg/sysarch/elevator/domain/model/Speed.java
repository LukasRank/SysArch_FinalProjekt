package de.htwg.sysarch.elevator.domain.model;

/**
 * Motor speed steps (assignment §1.6 (2), §4.3).
 * <ul>
 *   <li>{@link #V2} ≈ 1 m/s — travel speed</li>
 *   <li>{@link #V1} ≈ 0.1 m/s — approach speed</li>
 *   <li>{@link #CRAWL} — fine adjustment via the crawl register (−5..5 cm/s)</li>
 *   <li>{@link #OFF} — motor off</li>
 * </ul>
 */
public enum Speed {
    OFF,
    CRAWL,
    V1,
    V2
}
