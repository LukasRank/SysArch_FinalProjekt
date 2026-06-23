package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.elevator.domain.model.LevelSensors;
import de.htwg.sysarch.elevator.domain.model.PlcInputs;
import de.htwg.sysarch.elevator.domain.model.PlcOutputs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevatorControllerTest {

    private final ElevatorController controller = new ElevatorController();
    private final RequestStore requests = new RequestStore();

    @Test
    void fullStopSequenceForCabinCallAbove() {
        requests.addCabinCall(Level.LEVEL_3);

        // 1) idle with a request above → travel up at v2
        PlcOutputs o = controller.step(allSensorsClear(true), requests, 0).outputs();
        assertEquals(Phase.MOVING, controller.phase());
        assertTrue(o.upV2(), "should travel up at v2");

        // 2) target approach sensor → decelerate to v1
        o = controller.step(sensor(Level.LEVEL_3, approachLower()), requests, 10).outputs();
        assertEquals(Phase.DECELERATING, controller.phase());
        assertTrue(o.upV1());
        assertFalse(o.upV2());

        // 3) target safety sensor → crawl (coils off, crawl register set)
        o = controller.step(sensor(Level.LEVEL_3, safetyLower()), requests, 20).outputs();
        assertEquals(Phase.CRAWLING, controller.phase());
        assertEquals(5, o.crawlCmPerS());
        assertFalse(o.upV1() || o.upV2());

        // 4) reached → stop at door-safe position, door opens
        o = controller.step(sensor(Level.LEVEL_3, reached()), requests, 30).outputs();
        assertEquals(Phase.DOOR_OPENING, controller.phase());
        assertEquals(Level.LEVEL_3, controller.currentLevel());
        assertTrue(o.doorOpen());

        // 5) door reports open → dwell starts
        controller.step(doorState(true, false), requests, 1_000);
        assertEquals(Phase.DOOR_OPEN, controller.phase());

        // 6) after the minimum dwell (6 s) → door closes
        o = controller.step(doorState(false, false), requests,
                1_000 + ElevatorController.MIN_DWELL_MILLIS + 1).outputs();
        assertEquals(Phase.DOOR_CLOSING, controller.phase());
        assertTrue(o.doorClose());

        // 7) door closed, no further requests → idle
        controller.step(doorState(false, true), requests, 9_000);
        assertEquals(Phase.IDLE, controller.phase());
    }

    @Test
    void emergencyStopHaltsMotorAndResumesAfterReset() {
        requests.addCabinCall(Level.LEVEL_4);
        controller.engageEmergency();

        var result = controller.step(allSensorsClear(true), requests, 0);
        assertEquals(Phase.EMERGENCY, controller.phase());
        assertTrue(result.status().emergencyActive());
        assertFalse(anyMotorCoil(result.outputs()));

        controller.resetEmergency();
        var resumed = controller.step(allSensorsClear(true), requests, 1);
        assertFalse(resumed.status().emergencyActive());
        assertEquals(Phase.MOVING, controller.phase(), "should resume toward the scheduled stop");
        assertTrue(resumed.outputs().upV2());
    }

    @Test
    void motorErrorPulsesResetAndStopsMotor() {
        requests.addCabinCall(Level.LEVEL_4);

        PlcInputs.Builder b = PlcInputs.builder();
        for (Level l : Level.values()) {
            b.sensors(l, LevelSensors.NONE);
        }
        PlcOutputs o = controller.step(b.motorError(true).build(), requests, 0).outputs();

        assertTrue(o.reset(), "POreset should be pulsed on motor error");
        assertFalse(anyMotorCoil(o));
    }

    @Test
    void reversesDirectionToServeRequestBelowAfterStop() {
        // position the cabin at level 2 (idle, no requests yet)
        controller.step(sensor(Level.LEVEL_2, reached()), requests, -1);
        assertEquals(Level.LEVEL_2, controller.currentLevel());

        requests.addCabinCall(Level.LEVEL_4); // above → served first
        requests.addCabinCall(Level.LEVEL_1); // below → must be served afterwards (reversal)

        // travel up to level 4 and complete the door cycle
        controller.step(allSensorsClear(true), requests, 0);
        controller.step(sensor(Level.LEVEL_4, approachLower()), requests, 10);
        controller.step(sensor(Level.LEVEL_4, safetyLower()), requests, 20);
        controller.step(sensor(Level.LEVEL_4, reached()), requests, 30);
        controller.step(doorState(true, false), requests, 1_000);
        controller.step(doorState(false, false), requests, 1_000 + ElevatorController.MIN_DWELL_MILLIS + 1);
        PlcOutputs o = controller.step(doorState(false, true), requests, 9_000).outputs();

        // no more requests above → reverse downward to serve level 1
        assertEquals(Direction.DOWN, controller.travelDirection());
        assertEquals(Phase.MOVING, controller.phase());
        assertTrue(o.downV2());
    }

    @Test
    void emergencyDuringTravelStopsAndResumesSameDirection() {
        requests.addCabinCall(Level.LEVEL_4);

        PlcOutputs moving = controller.step(allSensorsClear(true), requests, 0).outputs();
        assertTrue(moving.upV2());

        controller.engageEmergency();
        PlcOutputs halted = controller.step(allSensorsClear(true), requests, 10).outputs();
        assertEquals(Phase.EMERGENCY, controller.phase());
        assertFalse(anyMotorCoil(halted));

        controller.resetEmergency();
        PlcOutputs resumed = controller.step(allSensorsClear(true), requests, 20).outputs();
        assertEquals(Phase.MOVING, controller.phase());
        assertTrue(resumed.upV2(), "should resume travelling up toward level 4");
    }

    // ------------------------------------------------------------- fixtures

    private static boolean anyMotorCoil(PlcOutputs o) {
        return o.upV2() || o.upV1() || o.downV1() || o.downV2();
    }

    private static LevelSensors approachLower() {
        return new LevelSensors(true, false, false, false, false);
    }

    private static LevelSensors safetyLower() {
        return new LevelSensors(false, true, false, false, false);
    }

    private static LevelSensors reached() {
        return new LevelSensors(false, false, true, false, false);
    }

    private static PlcInputs allSensorsClear(boolean doorClosed) {
        return doorState(false, doorClosed);
    }

    private static PlcInputs doorState(boolean opened, boolean closed) {
        PlcInputs.Builder b = PlcInputs.builder();
        for (Level l : Level.values()) {
            b.sensors(l, LevelSensors.NONE);
        }
        return b.doorOpened(opened).doorClosed(closed).motorReady(true).build();
    }

    private static PlcInputs sensor(Level level, LevelSensors sensors) {
        PlcInputs.Builder b = PlcInputs.builder();
        for (Level l : Level.values()) {
            b.sensors(l, l == level ? sensors : LevelSensors.NONE);
        }
        return b.doorClosed(true).motorReady(true).build();
    }
}
