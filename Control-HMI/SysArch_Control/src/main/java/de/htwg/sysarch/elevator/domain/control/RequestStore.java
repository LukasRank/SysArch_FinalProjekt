package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.HallCall;
import de.htwg.sysarch.elevator.domain.model.Level;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pending elevator requests plus the collective-control (SCAN) predicates over them
 * (assignment §1.6 (6)–(10)). Three kinds of request:
 * <ul>
 *   <li>cabin calls — a target level chosen inside the cabin (§1.6 (7))</li>
 *   <li>hall-up calls — a level requesting upward travel (§1.6 (6))</li>
 *   <li>hall-down calls — a level requesting downward travel (§1.6 (6))</li>
 * </ul>
 */
public final class RequestStore {

    private final Set<Level> cabinCalls = EnumSet.noneOf(Level.class);
    private final Set<Level> hallUp = EnumSet.noneOf(Level.class);
    private final Set<Level> hallDown = EnumSet.noneOf(Level.class);

    /** Insertion-ordered set of pending request keys, to pick the oldest when idle (§1.6 (8)). */
    private final LinkedHashMap<String, Level> order = new LinkedHashMap<>();

    // ---------------------------------------------------------------- mutation

    public void addCabinCall(Level level) {
        if (cabinCalls.add(level)) {
            order.putIfAbsent("C" + level.number(), level);
        }
    }

    public void addHallCall(HallCall call) {
        Set<Level> set = call.direction() == Direction.UP ? hallUp : hallDown;
        if (set.add(call.level())) {
            order.putIfAbsent(call.direction().name() + call.level().number(), call.level());
        }
    }

    /**
     * Clear the requests served by stopping at {@code level} and departing in
     * {@code servedDirection}: always the cabin call, plus the hall call in the
     * departing direction (both directions when {@code servedDirection} is NONE).
     */
    public void clearAt(Level level, Direction servedDirection) {
        if (cabinCalls.remove(level)) {
            order.remove("C" + level.number());
        }
        if (servedDirection == Direction.UP || servedDirection == Direction.NONE) {
            if (hallUp.remove(level)) {
                order.remove(Direction.UP.name() + level.number());
            }
        }
        if (servedDirection == Direction.DOWN || servedDirection == Direction.NONE) {
            if (hallDown.remove(level)) {
                order.remove(Direction.DOWN.name() + level.number());
            }
        }
    }

    // ------------------------------------------------------------- predicates

    public boolean isEmpty() {
        return cabinCalls.isEmpty() && hallUp.isEmpty() && hallDown.isEmpty();
    }

    public boolean hasCabinCall(Level level) {
        return cabinCalls.contains(level);
    }

    public boolean hasHallCall(Level level, Direction direction) {
        switch (direction) {
            case UP:
                return hallUp.contains(level);
            case DOWN:
                return hallDown.contains(level);
            default:
                return false;
        }
    }

    public boolean hasAnyRequestAt(Level level) {
        return cabinCalls.contains(level) || hallUp.contains(level) || hallDown.contains(level);
    }

    public boolean hasRequestsAbove(Level level) {
        return anyRequest(l -> l.number() > level.number());
    }

    public boolean hasRequestsBelow(Level level) {
        return anyRequest(l -> l.number() < level.number());
    }

    public boolean hasRequestsBeyond(Level level, Direction direction) {
        switch (direction) {
            case UP:
                return hasRequestsAbove(level);
            case DOWN:
                return hasRequestsBelow(level);
            default:
                return false;
        }
    }

    /**
     * Should the cabin stop at {@code level} while travelling {@code direction}?
     * It stops for a cabin call, a hall call in the same direction, or — at a
     * turning point (no requests beyond) — a hall call in the opposite direction.
     */
    public boolean shouldStopAt(Level level, Direction direction) {
        if (hasCabinCall(level)) {
            return true;
        }
        if (hasHallCall(level, direction)) {
            return true;
        }
        return !hasRequestsBeyond(level, direction) && hasHallCall(level, direction.opposite());
    }

    /**
     * Decide the direction to travel from {@code level}, preferring to keep going
     * in {@code preferred} (§1.6 (9),(10)). When {@code preferred} is NONE (idle),
     * head toward the oldest pending request (§1.6 (8)). Returns NONE when no move
     * is required.
     */
    public Direction continueOrReverse(Level level, Direction preferred) {
        if (preferred == Direction.UP) {
            if (hasRequestsAbove(level)) {
                return Direction.UP;
            }
            return hasRequestsBelow(level) ? Direction.DOWN : Direction.NONE;
        }
        if (preferred == Direction.DOWN) {
            if (hasRequestsBelow(level)) {
                return Direction.DOWN;
            }
            return hasRequestsAbove(level) ? Direction.UP : Direction.NONE;
        }
        Optional<Level> oldest = oldestRequestLevel();
        if (oldest.isEmpty()) {
            return Direction.NONE;
        }
        int cmp = Integer.compare(oldest.get().number(), level.number());
        return cmp > 0 ? Direction.UP : cmp < 0 ? Direction.DOWN : Direction.NONE;
    }

    public Optional<Level> oldestRequestLevel() {
        var it = order.values().iterator();
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
    }

    // --------------------------------------------------------------- snapshots

    public Set<Level> cabinCalls() {
        return copy(cabinCalls);
    }

    public Set<Level> hallUpCalls() {
        return copy(hallUp);
    }

    public Set<Level> hallDownCalls() {
        return copy(hallDown);
    }

    // ----------------------------------------------------------------- helpers

    private boolean anyRequest(Predicate<Level> p) {
        for (Level l : cabinCalls) {
            if (p.test(l)) {
                return true;
            }
        }
        for (Level l : hallUp) {
            if (p.test(l)) {
                return true;
            }
        }
        for (Level l : hallDown) {
            if (p.test(l)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Level> copy(Set<Level> source) {
        Set<Level> c = EnumSet.noneOf(Level.class);
        c.addAll(source);
        return Collections.unmodifiableSet(c);
    }
}
