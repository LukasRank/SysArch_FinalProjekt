package de.htwg.sysarch.elevator.domain.control;

import de.htwg.sysarch.elevator.domain.model.Direction;
import de.htwg.sysarch.elevator.domain.model.DoorCommand;
import de.htwg.sysarch.elevator.domain.model.Level;
import de.htwg.sysarch.elevator.domain.model.LevelSensors;
import de.htwg.sysarch.elevator.domain.model.MotorCommand;
import de.htwg.sysarch.elevator.domain.model.PlcInputs;
import de.htwg.sysarch.elevator.domain.model.PlcOutputs;
import de.htwg.sysarch.elevator.domain.model.Speed;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The elevator control state machine (assignment §1.6). Pure logic: a cycle is a
 * function of the current PLC inputs, the pending {@link RequestStore} and the
 * current time. It produces the {@link PlcOutputs} to write, an {@link ElevatorStatus}
 * for the HMI, and a list of {@link ElevatorEvent}s.
 *
 * <p>Deceleration chain (§1.6 (5), §6 in CLAUDE.md): v2 while travelling, switch to
 * v1 at the target's approach sensor, to crawl at its safety sensor, and stop at its
 * reached sensor (the door-safe position).
 */
public final class ElevatorController {

    /** Minimum dwell time at a level (§1.6 (11)); door open/close time adds to this. */
    public static final long MIN_DWELL_MILLIS = 6_000L;

    private Level currentLevel = Level.LEVEL_1;
    private Direction travelDirection = Direction.NONE;
    private Phase phase = Phase.IDLE;
    private Level target;
    private long dwellEndsAt;

    private boolean emergencyRequested;
    private boolean emergencyActive;
    private boolean errorLatched;

    // ------------------------------------------------- operator-side commands

    /** Cabin emergency stop (§1.6 (12)). */
    public void engageEmergency() {
        emergencyRequested = true;
    }

    /** Reset the emergency state; the elevator resumes toward the next scheduled stop (§1.6 (12)). */
    public void resetEmergency() {
        emergencyRequested = false;
    }

    public Phase phase() {
        return phase;
    }

    public Level currentLevel() {
        return currentLevel;
    }

    public Direction travelDirection() {
        return travelDirection;
    }

    // ------------------------------------------------------------- one cycle

    public CycleResult step(PlcInputs in, RequestStore requests, long now) {
        List<ElevatorEvent> events = new ArrayList<>();
        updatePosition(in);

        // Motor fault: pulse POreset and hold (§4.1 PIm_error).
        if (in.motorError()) {
            if (!errorLatched) {
                events.add(ElevatorEvent.of(ElevatorEvent.Type.ALARM, currentLevel, "motor error"));
                errorLatched = true;
            }
            return buildResult(MotorCommand.OFF, DoorCommand.NONE, true, in, requests, events);
        }
        errorLatched = false;

        // Emergency stop overrides everything (§1.6 (12)).
        if (emergencyRequested) {
            if (!emergencyActive) {
                emergencyActive = true;
                phase = Phase.EMERGENCY;
                events.add(ElevatorEvent.of(ElevatorEvent.Type.EMERGENCY_ENGAGED, currentLevel));
            }
            return buildResult(MotorCommand.OFF, DoorCommand.NONE, false, in, requests, events);
        }
        if (emergencyActive) {
            emergencyActive = false;
            events.add(ElevatorEvent.of(ElevatorEvent.Type.EMERGENCY_RESET, currentLevel));
            phase = (target != null && travelDirection != Direction.NONE) ? Phase.MOVING : Phase.IDLE;
        }

        advance(in, requests, now, events);

        return buildResult(deriveMotor(), deriveDoor(in), false, in, requests, events);
    }

    // -------------------------------------------------------- state machine

    private void advance(PlcInputs in, RequestStore requests, long now, List<ElevatorEvent> events) {
        switch (phase) {
            case IDLE:
                handleIdle(requests, events);
                break;
            case MOVING:
            case DECELERATING:
            case CRAWLING:
                handleTravel(in, requests, events);
                break;
            case DOOR_OPENING:
                if (in.doorOpened()) {
                    phase = Phase.DOOR_OPEN;
                    dwellEndsAt = now + MIN_DWELL_MILLIS;
                    events.add(ElevatorEvent.of(ElevatorEvent.Type.DOOR_OPENED, currentLevel));
                }
                break;
            case DOOR_OPEN:
                if (now >= dwellEndsAt) {
                    phase = Phase.DOOR_CLOSING;
                    events.add(ElevatorEvent.of(ElevatorEvent.Type.DOOR_CLOSING, currentLevel));
                }
                break;
            case DOOR_CLOSING:
                if (in.doorClosed()) {
                    events.add(ElevatorEvent.of(ElevatorEvent.Type.DOOR_CLOSED, currentLevel));
                    departFrom(currentLevel, requests, events);
                }
                break;
            case EMERGENCY:
                /* handled in step() before advance() */
                break;
            default:
                break;
        }
    }

    private void handleIdle(RequestStore requests, List<ElevatorEvent> events) {
        if (requests.hasAnyRequestAt(currentLevel)) {
            beginDoorCycle(requests, events);
            return;
        }
        Direction d = requests.continueOrReverse(currentLevel, Direction.NONE);
        if (d != Direction.NONE) {
            startMoving(d, requests, events);
        }
    }

    private void handleTravel(PlcInputs in, RequestStore requests, List<ElevatorEvent> events) {
        Level stop = (phase == Phase.MOVING)
                ? nextStop(requests, currentLevel, travelDirection)
                : target;

        if (stop == null) {
            departFrom(currentLevel, requests, events);
            return;
        }
        target = stop;

        LevelSensors s = in.at(stop);
        if (s.reached()) {
            currentLevel = stop;
            beginDoorCycle(requests, events);
        } else if (s.safetyFrom(travelDirection)) {
            phase = Phase.CRAWLING;
        } else if (s.approachFrom(travelDirection) && phase == Phase.MOVING) {
            phase = Phase.DECELERATING;
        }
    }

    /** Decide direction and target after a stop; go IDLE if nothing remains (§1.6 (9),(10)). */
    private void departFrom(Level level, RequestStore requests, List<ElevatorEvent> events) {
        Direction d = requests.continueOrReverse(level, travelDirection);
        if (d == Direction.NONE) {
            travelDirection = Direction.NONE;
            target = null;
            phase = Phase.IDLE;
        } else {
            startMoving(d, requests, events);
        }
    }

    private void startMoving(Direction d, RequestStore requests, List<ElevatorEvent> events) {
        Level stop = nextStop(requests, currentLevel, d);
        if (stop == null) {
            travelDirection = Direction.NONE;
            target = null;
            phase = Phase.IDLE;
            return;
        }
        travelDirection = d;
        target = stop;
        phase = Phase.MOVING;
        events.add(ElevatorEvent.of(ElevatorEvent.Type.DEPARTURE, currentLevel, d.name()));
    }

    /** Arrive at {@code currentLevel}: open the door and clear the served requests. */
    private void beginDoorCycle(RequestStore requests, List<ElevatorEvent> events) {
        target = currentLevel;
        phase = Phase.DOOR_OPENING;
        events.add(ElevatorEvent.of(ElevatorEvent.Type.ARRIVAL, currentLevel));
        events.add(ElevatorEvent.of(ElevatorEvent.Type.DOOR_OPENING, currentLevel));

        Direction departing = requests.continueOrReverse(currentLevel, travelDirection);
        requests.clearAt(currentLevel, travelDirection);
        requests.clearAt(currentLevel, departing);
    }

    /** Nearest level in {@code direction} (strictly beyond {@code from}) the cabin must stop at. */
    private Level nextStop(RequestStore requests, Level from, Direction direction) {
        Level cur = from;
        while (true) {
            Optional<Level> next = cur.neighbour(direction);
            if (next.isEmpty()) {
                return null;
            }
            cur = next.get();
            if (requests.shouldStopAt(cur, direction)) {
                return cur;
            }
        }
    }

    private void updatePosition(PlcInputs in) {
        for (Level l : Level.values()) {
            if (in.at(l).reached()) {
                currentLevel = l;
            }
        }
    }

    // ----------------------------------------------------------- output derivation

    private MotorCommand deriveMotor() {
        switch (phase) {
            case MOVING:
                return MotorCommand.drive(travelDirection, Speed.V2);
            case DECELERATING:
                return MotorCommand.drive(travelDirection, Speed.V1);
            case CRAWLING:
                return MotorCommand.drive(travelDirection, Speed.CRAWL);
            default:
                return MotorCommand.OFF;
        }
    }

    private DoorCommand deriveDoor(PlcInputs in) {
        switch (phase) {
            case DOOR_OPENING:
                return DoorCommand.OPEN;
            case DOOR_CLOSING:
                return DoorCommand.CLOSE;
            case IDLE:
                return in.doorClosed() ? DoorCommand.NONE : DoorCommand.CLOSE;
            default:
                return DoorCommand.NONE;
        }
    }

    private CycleResult buildResult(MotorCommand motor, DoorCommand door, boolean reset,
                                    PlcInputs in, RequestStore requests, List<ElevatorEvent> events) {
        PlcOutputs outputs = PlcOutputs.of(motor, door, reset);
        ElevatorStatus status = new ElevatorStatus(
                currentLevel, travelDirection, phase, in.doorState(), in.velocityCmPerS(),
                emergencyActive, in.motorError(),
                requests.cabinCalls(), requests.hallUpCalls(), requests.hallDownCalls());
        return new CycleResult(outputs, status, List.copyOf(events));
    }
}
