package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.HallCall;
import de.htwg.sysarch.elevator.domain.model.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestStoreTest {

    @Test
    void stopsForCabinCall() {
        RequestStore store = new RequestStore();
        store.addCabinCall(Level.LEVEL_3);

        assertTrue(store.shouldStopAt(Level.LEVEL_3, Direction.UP));
        assertFalse(store.shouldStopAt(Level.LEVEL_2, Direction.UP));
    }

    @Test
    void stopsForHallCallInSameDirectionOnly() {
        RequestStore store = new RequestStore();
        store.addHallCall(new HallCall(Level.LEVEL_2, Direction.UP));

        assertTrue(store.shouldStopAt(Level.LEVEL_2, Direction.UP));
        // a down-travelling cabin does not serve an up call when something is below... here nothing below,
        // so the turning-point rule applies and it would stop; assert the same-direction case explicitly:
        store.addCabinCall(Level.LEVEL_1);
        assertFalse(store.shouldStopAt(Level.LEVEL_2, Direction.DOWN));
    }

    @Test
    void stopsAtTurningPointForOppositeCall() {
        RequestStore store = new RequestStore();
        // up call at level 3 with nothing above → a cabin going UP must stop and reverse there.
        store.addHallCall(new HallCall(Level.LEVEL_3, Direction.DOWN));

        assertTrue(store.shouldStopAt(Level.LEVEL_3, Direction.UP));
    }

    @Test
    void doesNotStopForOppositeCallWhenRequestsBeyond() {
        RequestStore store = new RequestStore();
        store.addHallCall(new HallCall(Level.LEVEL_3, Direction.DOWN));
        store.addCabinCall(Level.LEVEL_4); // something above → keep going, serve L3-down later

        assertFalse(store.shouldStopAt(Level.LEVEL_3, Direction.UP));
    }

    @Test
    void continuesUpWhileRequestsAbove() {
        RequestStore store = new RequestStore();
        store.addCabinCall(Level.LEVEL_4);

        assertEquals(Direction.UP, store.continueOrReverse(Level.LEVEL_2, Direction.UP));
    }

    @Test
    void reversesWhenNoRequestsAheadButBehind() {
        RequestStore store = new RequestStore();
        store.addCabinCall(Level.LEVEL_1);

        assertEquals(Direction.DOWN, store.continueOrReverse(Level.LEVEL_3, Direction.UP));
    }

    @Test
    void idleHeadsTowardOldestRequest() {
        RequestStore store = new RequestStore();
        store.addCabinCall(Level.LEVEL_1);   // oldest → below level 3
        store.addCabinCall(Level.LEVEL_4);

        assertEquals(Direction.DOWN, store.continueOrReverse(Level.LEVEL_3, Direction.NONE));
    }

    @Test
    void clearAtRemovesServedRequests() {
        RequestStore store = new RequestStore();
        store.addCabinCall(Level.LEVEL_2);
        store.addHallCall(new HallCall(Level.LEVEL_2, Direction.UP));
        store.addHallCall(new HallCall(Level.LEVEL_2, Direction.DOWN));

        store.clearAt(Level.LEVEL_2, Direction.UP);

        assertFalse(store.hasCabinCall(Level.LEVEL_2));
        assertFalse(store.hasHallCall(Level.LEVEL_2, Direction.UP));
        assertTrue(store.hasHallCall(Level.LEVEL_2, Direction.DOWN)); // opposite kept for the return trip
    }
}
