# Elevator Control System — Group E (C2)

Control system for the HTWG Konstanz *System Architecture* elevator project.
It drives the lab elevator **simulation** (4 levels) as a **Modbus/TCP master**
(~100 ms cycle), implements the full collective-control (SCAN) logic, and exposes
a clean **MQTT** interface for the HMI.

> 📄 **[`CLAUDE.md`](CLAUDE.md)** is the single source of truth — architecture,
> I/O mapping, requirement traceability, configuration and open questions.

## Features

- Collective-control (SCAN) logic for 4 levels — requirements §1.6 (1)–(12).
- Two-speed approach with deceleration chain (v2 → v1 → crawl → stop at the door-safe position).
- Minimum dwell time, door cycle, emergency stop with resume, motor-error handling.
- In-memory **simulation** — runs with no lab access or VPN.
- **MQTT HMI interface** with two reference clients: a graphical HMI (animated shaft,
  live status, clickable controls) and a console HMI.
- Hexagonal architecture — the domain logic is pure and transport-agnostic.

## Architecture

Ports & adapters. The control logic knows nothing about Modbus or the HMI; both are
adapters behind ports. Control system and HMI are separate processes, coupled only by
the MQTT message contract.

```
        ┌─────────────┐   MQTT (JSON)   ┌──────────────────────────┐
        │     HMI      │ ◀────status──── │      Control System      │
        │ (GUI/console)│ ────command──▶  │  Application ── Domain    │ ──Modbus──▶ PLC
        └─────────────┘                  └──────────────────────────┘   (or simulation)
```

- **Driving port** `OperatorPanel` — commands in (cabin/hall calls, emergency, reset).
- **Driven ports** `PlcGateway` (sensors/actuators) and `HmiGateway` (status/events out).

## Prerequisites

- Java 17+ and Maven.
- An MQTT broker for the HMI, e.g. [Mosquitto](https://mosquitto.org):
  `brew install mosquitto && brew services start mosquitto` (listens on `localhost:1883`).

## Quick start

```bash
mvn test                       # run the unit tests

# Control system + graphical HMI together (needs a broker); closing the HMI stops both:
./run-demo.sh

# Control system only, against the in-memory simulation (console logging, no broker):
mvn exec:java -Dexec.args="--sim"
```

### Run the pieces separately

```bash
# Control system, simulation + MQTT HMI transport:
mvn exec:java -Dexec.args="--sim --mqtt"

# Graphical HMI (separate process):
mvn exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiSwingApplication

# Console HMI (headless/debug):
mvn exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiApplication

# Against the real lab PLC (HTWG network/VPN; not yet wired — see CLAUDE.md §9):
mvn exec:java -Dexec.args="--modbus"
```

> Broker/topic are configurable via `-Dmqtt.host`, `-Dmqtt.port`, `-Dmqtt.baseTopic`
> (defaults: `localhost:1883`, base `elevator/e`).

## Using the HMI

The graphical HMI shows the shaft with the cabin moving continuously (its speed is
visible), per-floor call lamps, live status read-outs and an event log.

| Action | Control |
|--------|---------|
| Send the cabin to a level | **Cabin → N** |
| Call the elevator going up/down | **▲ up** / **▼ down** at that floor |
| Emergency stop / resume | **⚠ Emergency** / **Reset emergency** |
| Reset the simulation | **Reset sim** |

## Project layout

```
de.htwg.sysarch.elevator   # control system: domain (pure SCAN logic), application (ports), infrastructure (adapters)
de.htwg.sysarch.mqtt       # shared MQTT message contract (topics + JSON DTOs)
de.htwg.sysarch.hmi        # reference HMI clients (graphical + console)
```

See [`CLAUDE.md`](CLAUDE.md) for the full package map, I/O mapping and requirement traceability.
