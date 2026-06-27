# CLAUDE.md — Elevator Control System (Group E / C2)

> **This file is the single source of truth for the project.**
> Read it first. Whenever you change architecture, behaviour, interfaces,
> configuration, dependencies or the I/O mapping, **update this file in the
> same change.** A change is not "done" until CLAUDE.md reflects it.
> See [Maintenance rule](#maintenance-rule).

---

## 1. Goal

Develop the **Control System** part of the HTWG Konstanz "System Architecture"
elevator project. The control program drives a ready-made elevator **simulation
running on a PLC** in the automation laboratory. We talk to that PLC as a
**Modbus/TCP master** (cycle ≈ 100 ms) and implement the full elevator control
logic for **4 levels**.

Our team is responsible **only for the Control System** (section 1.6 of the
assignment). A partner group builds the HMI. The control system must therefore
expose a clean, transport-agnostic interface so the HMI can operate it and read
its state.

**Definition of done for the control system:**

1. Connects to the assigned Modbus slave and reads all sensor inputs / writes all
   actuator outputs reliably at ~100 ms.
2. Implements the collective-control (SCAN) elevator logic per requirements
   §1.6 (1)–(12) — see [Requirements traceability](#5-requirements-traceability).
3. Decelerates correctly using the approach → safety → reached sensor chain and
   stops at the door-safe position.
4. Honours minimum dwell time (6 s + door open/close) and the emergency stop.
5. Exposes an interface (driving + driven ports) so the HMI can issue calls and
   read elevator state, independent of the concrete transport.

---

## 2. Maintenance rule

**Every change to the codebase must be reflected in this file in the same commit.**

When you:

- add/rename/remove a module, package or port → update [§4 Architecture](#4-architecture).
- change control behaviour → update [§5 Requirements traceability](#5-requirements-traceability).
- change the Modbus mapping → update [§6 I/O & hardware reference](#6-io--hardware-reference).
- change config (host, port, cycle time, group) → update [§7 Configuration](#7-configuration).
- add a dependency or change build/run commands → update [§3 Tech stack](#3-tech-stack--commands).
- decide an open question → move it from [§9 Open questions](#9-open-questions--decisions-needed)
  into the relevant section and record the decision.

Keep the [Changelog](#10-changelog) at the bottom up to date with one line per
meaningful change.

---

## 3. Tech stack & commands

- **Language/Build:** Java 11, Maven. (Targets Java 11 bytecode so the fat jar runs on the
  lab server `ea-pc165`, whose system JRE is OpenJDK 11 — no records/switch-expressions/
  text-blocks in the source.)
- **Modbus:** Modbus/TCP master. Library intended: the course-provided
  `easymodbus-maven` (see [§9](#9-open-questions--decisions-needed) — coordinates
  to be confirmed). Until wired in, the code targets a thin internal
  `ModbusConnection` port so the concrete library stays swappable.
- **HMI transport:** MQTT/TCP via Eclipse Paho (`org.eclipse.paho.client.mqttv3`);
  messages are JSON via Gson. Used only by the infrastructure adapters and the
  separate HMI app — the domain stays transport-agnostic (see [§8](#8-hmi-interface-boundary)).
- **Tests:** JUnit 5.
- **Logging:** `java.util.logging` (no extra dependency).

**Repo layout:** the control system and the HMI are two **separate Maven modules** under
`Control-HMI/` (`SysArch_Control/`, `SysArch_HMI/`), each built independently. They share
only the `de.htwg.sysarch.mqtt` contract (a copy lives in each module) — never a code import.

**Easiest path — PowerShell helper scripts** in `Control-HMI/` (they source the gitignored
`mqtt-credentials.ps1` so the broker host/user/password are set once, never on the command
line). First-time setup: `cp mqtt-credentials.example.ps1 mqtt-credentials.ps1` and fill in
the group password.

```powershell
.\run-control.ps1           # control: --sim --mqtt against the lab broker (ea-pc165)
.\run-control.ps1 -Modbus   # control against the real PLC (needs easymodbus, §9)
.\run-hmi.ps1               # graphical Swing HMI on the laptop, MQTT to the lab broker
.\run-hmi.ps1 -Console      # console HMI (headless/debug)
.\deploy-control.ps1 -RzUser <rzlogin>        # build jar + scp it to ea-pc165, print run cmd
.\deploy-control.ps1 -RzUser <rzlogin> -Run   # …and start it on the server
```

**Raw Maven** (run inside the module dir; PowerShell: quote each `-D…` arg):

```bash
mvn compile                                   # compile
mvn test                                      # run unit tests (SysArch_Control)
mvn package                                   # SysArch_Control → target/elevator-control-0.1.0.jar (fat jar)
mvn exec:java -Dexec.args="--sim"             # in-memory sim, no lab/VPN
mvn exec:java -Dexec.args="--sim --interactive"   # type calls at runtime (c/u/d <1-4>, e, r, x, h, q)
mvn exec:java -Dexec.args="--sim --mqtt"      # expose HMI over MQTT (falls back to console log if no broker)
mvn exec:java -Dexec.args="--modbus"          # real PLC (HTWG network/VPN)
mvn exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiSwingApplication   # graphical HMI (in SysArch_HMI)
mvn exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiApplication        # console HMI
```

> **Lab broker:** mosquitto on `ea-pc165:1883` (HTWG VPN, per-group auth). Group E verified
> working with `mosquitto_pub/sub -h ea-pc165.ei.htwg-konstanz.de -u E -P <pw> -t /SysArch/E`.
> The control system connects with the same credentials (auth confirmed 2026-06-25).
> For offline dev run a local anonymous broker (`brew install mosquitto && mosquitto -v`) and
> set `mqtt.host=localhost`.
>
> ⚠️ **Git Bash mangles topics:** a base topic starting with `/` (e.g. `/SysArch/E`) gets
> rewritten by MSYS path conversion into `C:/Program Files/Git/SysArch/E`. Use **PowerShell**
> (the helper scripts do) to start the apps; don't pass `-Dmqtt.baseTopic=/...` from Git Bash.

---

## 4. Architecture

**Style: Hexagonal (Ports & Adapters).** The control logic is pure and has no
knowledge of Modbus or the HMI. The assignment explicitly asks for components
with well-defined interfaces that can be driven from the HMI or for testing —
ports & adapters maps directly onto that.

```
HMI / tests ──(driving port)──▶  Application  ──(driven ports)──▶  PLC (Modbus) / HMI sink
                                     │
                                     ▼
                                  Domain (pure control logic)
```

### Package map (`de.htwg.sysarch.elevator`)

```
elevator/
├── ElevatorControlApplication.java     # main: wires config + adapters + loop
│
├── domain/                             # PURE logic, no external dependencies
│   ├── model/                          # value objects / DTOs
│   │   ├── Direction.java              # UP | DOWN | NONE
│   │   ├── Level.java                  # LEVEL_1..LEVEL_4 (4 levels)
│   │   ├── Speed.java                  # OFF | CRAWL | V1 | V2
│   │   ├── Drive.java                  # OFF | UP_V1 | UP_V2 | DOWN_V1 | DOWN_V2
│   │   ├── MotorCommand.java           # Drive + crawl cm/s
│   │   ├── DoorCommand.java            # NONE | OPEN | CLOSE
│   │   ├── DoorState.java              # OPEN | CLOSED | MOVING | UNKNOWN
│   │   ├── LevelSensors.java           # per-level sensor snapshot
│   │   ├── PlcInputs.java              # full input snapshot read each cycle
│   │   ├── PlcOutputs.java             # full output snapshot written each cycle
│   │   └── HallCall.java               # (level, direction) outside call
│   └── control/
│       ├── RequestStore.java           # pending cabin/hall calls + SCAN predicates
│       ├── Phase.java                  # FSM phase
│       ├── ElevatorStatus.java         # HMI-facing immutable state view (references Phase)
│       ├── ElevatorEvent.java          # arrival/departure/door/alarm events
│       ├── CycleResult.java            # outputs + status + events of one cycle
│       └── ElevatorController.java     # the control state machine (heart)
│
├── application/
│   ├── ElevatorControlService.java     # orchestrates one cycle; implements OperatorPanel
│   ├── ControlLoop.java                # ~100 ms scheduler
│   └── port/
│       ├── in/OperatorPanel.java       # DRIVING port: HMI issues calls/commands here
│       └── out/
│           ├── PlcGateway.java         # DRIVEN port: read inputs / write outputs
│           └── HmiGateway.java         # DRIVEN port: publish status + events to HMI
│
└── infrastructure/                     # adapters (the only place with I/O)
    ├── config/ElevatorConfig.java      # host, port, group, cycle time, MQTT broker/topic
    ├── modbus/
    │   ├── ModbusIoMap.java            # Codesys %QX/%IX/%QW → Modbus addresses
    │   ├── ModbusConnection.java       # thin port over the modbus client lib
    │   └── ModbusPlcGateway.java       # PlcGateway impl over ModbusConnection
    ├── simulation/SimulatedPlcGateway.java  # in-memory PLC for offline dev/tests
    ├── console/InteractiveConsole.java # stdin command driver for the simulation (--interactive)
    ├── mqtt/                           # MQTT HMI transport (control side, --mqtt)
    │   ├── MqttConnection.java         # thin port over the MQTT client lib (testable)
    │   ├── PahoMqttConnection.java     # MqttConnection impl over Eclipse Paho
    │   ├── MqttHmiGateway.java         # HmiGateway impl: publishes status/events as JSON
    │   └── MqttCommandRouter.java      # subscribes cmd topic → drives OperatorPanel
    └── hmi/LoggingHmiGateway.java      # console-logging HMI sink (default / --mqtt fallback)
```

Two **sibling** top-level packages keep the HMI strictly separated from the control
system across the MQTT boundary:

```
de.htwg.sysarch.mqtt/                   # SHARED contract — the only thing both sides import
├── MqttTopics.java                     # <base>/status, <base>/event, <base>/cmd
├── StatusMessage.java                  # control → HMI state DTO (primitives only)
├── EventMessage.java                   # control → HMI event DTO
├── CommandMessage.java                 # HMI → control command DTO (+ factory methods)
└── JsonCodec.java                      # Gson facade used by both sides

de.htwg.sysarch.hmi/                    # HMI side (reference/demo; partner group's responsibility)
├── HmiSwingApplication.java            # graphical HMI (Swing): shaft animation, status, buttons, log
├── ShaftView.java                      # animated 4-level shaft + cabin/doors/call-lamps (pure view)
└── HmiApplication.java                 # console HMI (headless/debug); same MQTT contract
```

> The HMI apps depend on the shared `mqtt` contract, Paho and the JDK (Swing) — **never**
> on any `de.htwg.sysarch.elevator.*` class. That is the clear control↔HMI separation.
> Swing needs no extra dependency.

### Interface convention (per assignment §1.3)

Interface variables are **writable in one direction only**. State that the HMI
reads is exposed via `ElevatorStatus` / `HmiGateway` (read-only for the HMI).
Commands the HMI sends go through `OperatorPanel` (write-only into the control
system). The two never mix on the same variable.

---

## 5. Requirements traceability

Mapping of control requirements §1.6 (1)–(12) to where they live:

| # | Requirement | Where |
|---|-------------|-------|
| 1 | 4 levels | `Level` enum |
| 2 | 2 speeds up/down + crawl + off | `Speed`, `Drive`, `MotorCommand` → `ModbusIoMap` coils + crawl register |
| 3 | 2 approach sensors per level/direction | `LevelSensors` (approachLower/Upper, safetyLower/Upper) |
| 4 | sensor for safe door position | `reached` sensor = door-safe position (assumption, see §9) |
| 5 | drive at highest speed, slow before stop | `ElevatorController` deceleration: V2 → V1 (approach) → CRAWL (safety) → stop (reached) |
| 6 | hall call with desired direction; stop if same direction | `HallCall`, `RequestStore.shouldStopAt` |
| 7 | cabin call selects target level | `RequestStore` cabin calls |
| 8 | idle → move toward first (oldest) request | `RequestStore` request timestamps + `ElevatorController` idle handling |
| 9 | continue in a direction while requests exist ahead and not at terminal | `RequestStore.hasRequestBeyond`, SCAN logic |
| 10 | reverse when no more requests ahead but some behind | `ElevatorController.decideDirection` |
| 11 | min dwell 6 s + door open/close time | `ElevatorController` dwell timer |
| 12 | emergency stop button; resume after reset | `ElevatorController` emergency handling + `OperatorPanel.emergencyStop/reset` |

---

## 6. I/O & hardware reference

### 6.1 Levels, sensor positions & speeds

- **Level height:** 3.5 m. **Speeds:** v1 ≈ 0.1 m/s, v2 ≈ 1 m/s, crawl −5..5 cm/s.
- Sensor offsets relative to a level (tolerance in brackets):

| Sensor (suffix) | Offset | Meaning |
|-----------------|--------|---------|
| `*al` approach lower | −1.5 m (±50 mm) | far below level → start decel when going UP |
| `*sl` safety lower   | −50 mm (±5 mm) | just below → go to crawl when going UP |
| `*r`  reached        | 0 mm (±5 mm)   | at level → **door-safe stop position** |
| `*su` safety upper   | +50 mm (±5 mm) | just above → go to crawl when going DOWN |
| `*au` approach upper | +1.5 m (±50 mm)| far above → start decel when going DOWN |

Level 1 (lowest) has **no** `al` (only approached from above). Level 4 (highest)
has **no** `au` (only approached from below).

**Deceleration chain:**
- Going **UP** to target T: `T.al` → V1, `T.sl` → CRAWL, `T.r` → STOP.
- Going **DOWN** to target T: `T.au` → V1, `T.su` → CRAWL, `T.r` → STOP.

### 6.2 Modbus I/O map (Codesys syntax from the assignment §4.1)

> ⚠️ **VERIFY against the running simulation.** The Codesys→Modbus address
> derivation below (`bit address = byte·8 + bit`, `%QX`→coil, `%IX`→discrete
> input, `%QW/%IW/%ID`→register) is the standard convention but the concrete
> slave layout/offset must be confirmed at the lab. All addresses are
> centralised in `ModbusIoMap` so this is the only place to fix.

**Outputs we write (`%QX` → coils, `%QW` → holding register):**

| Symbol | Codesys | Meaning |
|--------|---------|---------|
| POreset | %QX0.0 | simulation reset / clear motor error |
| POdv2 | %QX1.0 | motor down v2 |
| POdv1 | %QX1.1 | motor down v1 |
| POuv1 | %QX1.2 | motor up v1 |
| POuv2 | %QX1.3 | motor up v2 |
| POdclose | %QX1.4 | close door |
| POdopen | %QX1.5 | open door |
| POv_crawlSelect | %QW1 | crawl speed select, INT −5..5 cm/s |

**Inputs we read (`%IX` → discrete inputs, `%IW/%ID` → registers):**

Per level L∈{1..4}: `PIs_lLal, PIs_lLsl, PIs_lLr, PIs_lLsu, PIs_lLau`
(level 1 has no `al`, level 4 has no `au`), plus:

| Symbol | Codesys | Meaning |
|--------|---------|---------|
| PIs_dopened | %IX10.0 | door open |
| PIs_dclosed | %IX10.1 | door closed |
| PIm_ready | %IX10.2 | motor ready |
| PIm_on | %IX10.3 | motor on |
| PIm_error | %IX10.4 | error state (reset via POreset) |
| PIcycles | %ID1 | PLC cycles since start (UDINT) |
| PIaufzugID | %IW4 | instance id (1..4) |
| PIs_v | %IW6 | velocity, 1 cm/s units (INT) |

---

## 7. Configuration

**Group E (C2):**

- Modbus slave: `ea-pc111.ei.htwg-konstanz.de:506`
- Simulation GUI: http://ea-pc111.ei.htwg-konstanz.de:8080/aufzugesimu.htm
- Control GUI: http://ea-pc111.ei.htwg-konstanz.de:8080/aufzugectrl.htm
- Access only from the lab or **HTWG VPN**.
- Cycle time: 100 ms.

**HMI transport (MQTT):**

- Broker: lab mosquitto on `ea-pc165:1883` (already running as a service; you do **not**
  start it). Requires per-group credentials; topics live under `/SysArch/…` (renamed
  from `/22WS-SysArch/…` in the lab handout). For offline dev run a local anonymous
  broker and set `mqtt.host=localhost` with empty credentials.
- Base topic: `/SysArch/E` (status/event/cmd appended).
- Keys: `mqtt.host`, `mqtt.port`, `mqtt.baseTopic`, `mqtt.username`, `mqtt.password`.
- **Credentials are never committed.** They default to empty and are supplied at runtime;
  precedence is `-Dmqtt.username=…` system property > `MQTT_USERNAME`/`MQTT_PASSWORD`
  env var > properties file. Same keys on the control side (`ElevatorConfig`) and the
  HMI apps (which read straight from system props/env, no classpath loader).

**Deployment split (lab):** the broker and the control system both run on `ea-pc165`
(control reaches the broker as `localhost:1883` and the PLC as `ea-pc111:506`); the HMI
runs on the laptop and reaches the broker through an SSH tunnel
(`ssh -L 1883:localhost:1883 FHKN.<rzuser>@ea-pc165.ei.htwg-konstanz.de`), then points at
`mqtt.host=localhost`. `mvn package` builds a self-contained
`target/elevator-control-0.1.0.jar` (maven-shade-plugin); run it on the server with
`java -Dmqtt.host=localhost -Dmqtt.username=… -Dmqtt.password=… -jar elevator-control-0.1.0.jar --sim --mqtt`.
`Control-HMI/deploy-control.ps1 -RzUser <rzlogin>` automates the build + `scp` to the server
and prints (or, with `-Run`, executes) that command.

**Credentials** are kept in the gitignored `Control-HMI/mqtt-credentials.ps1` (copied from
`mqtt-credentials.example.ps1`); the helper scripts source it and pass the values as `-D`
properties. They never live in git or in `application.properties` (which default to empty).

Defaults live in `src/main/resources/application.properties` and
`infrastructure/config/ElevatorConfig.java`.

---

## 8. HMI interface boundary

**Decision: the control↔HMI transport is MQTT** (the provided mosquitto on `ea-pc165`).
The control system is the MQTT side that publishes state and subscribes to commands;
the HMI is a separate process. The two share **only** the JSON message contract in
`de.htwg.sysarch.mqtt` — neither imports the other's classes.

The control system keeps both ports; MQTT is just their adapter:

- `OperatorPanel` (driving) — HMI → control: hall calls, cabin calls, emergency
  stop/reset, supervisory reset. MQTT adapter: `MqttCommandRouter`.
- `HmiGateway` (driven) — control → HMI: `ElevatorStatus` snapshots + logged
  `ElevatorEvent`s. MQTT adapter: `MqttHmiGateway`.

**Topics** (`<base>` = `/SysArch/E`), honouring the one-directional rule (§1.3):

| Topic | Direction | Payload | Retained |
|-------|-----------|---------|----------|
| `<base>/status` | control → HMI | `StatusMessage` JSON | yes (last state) |
| `<base>/event`  | control → HMI | `EventMessage` JSON  | no (stream) |
| `<base>/cmd`    | HMI → control | `CommandMessage` JSON | no |

**Payload examples:**

```jsonc
// <base>/status
{"level":2,"direction":"UP","phase":"MOVING","door":"CLOSED","velocity":100,
 "emergencyActive":false,"motorError":false,"cabinCalls":[4],"hallUpCalls":[2],"hallDownCalls":[]}
// <base>/event
{"type":"ARRIVAL","level":3,"detail":""}
// <base>/cmd
{"type":"cabin","level":3}
{"type":"hall","level":2,"direction":"UP"}
{"type":"emergency","action":"engage"}   // or "clear"
{"type":"reset"}
```

Activated with `--mqtt`. Without `--mqtt` (or if the broker is unreachable) the
control system uses `LoggingHmiGateway` (console) and keeps running — it never
depends on HMI/broker availability. The HMI side ships as two reference clients:
`de.htwg.sysarch.hmi.HmiSwingApplication` (graphical: animated shaft, status read-outs,
call/emergency buttons, event log) and `HmiApplication` (console). Both consume only the
topic contract, so the partner group can replace them with any GUI/web HMI.

---

## 9. Open questions / decisions needed

1. **easymodbus-maven coordinates** — the course provides a Maven build at
   `https://gitlab.ei.htwg-konstanz.de/system-architecture/25ws/easymodbus-maven`.
   Need groupId/artifactId/version (requires HTWG access) to add the dependency
   and implement `ModbusConnection`. Until then the Modbus adapter compiles
   against the internal port only; the in-memory simulation is the runnable path.
2. **Modbus address layout** — confirm the `%QX/%IX/%QW`→Modbus address mapping
   against the live slave (see §6.2 warning).
3. **Door-safe sensor** — assumed to be `reached` (`*r`, 0 mm). Confirm whether
   the safe window is `*r` only or the `*sl/*su` band.
4. **HMI transport** — ✅ decided: **MQTT** (see §8). The JSON message contract in
   `de.htwg.sysarch.mqtt` (topics + DTOs) must still be confirmed with the partner
   HMI group and fixed in the faculty GitLab wiki.
5. **Code submission deadline** — assignment lists "Fri 03.06.26" which predates
   today; likely a typo for **03.07.26**. Confirm via Moodle.

---

## 10. Changelog

- **2026-06-17** — Initial project scaffold: hexagonal architecture, domain model,
  ports, in-memory simulation adapter, Modbus I/O map (unverified), control-loop
  skeleton, JUnit setup. Group E config. CLAUDE.md created.
- **2026-06-17** — Added interactive console driver (`--interactive`) and targeted
  controller tests (direction reversal §1.6 (10), emergency-during-travel resume
  §1.6 (12)). 13 unit tests, all green.
- **2026-06-21** — HMI transport decided and implemented: **MQTT** (Eclipse Paho +
  Gson). Added shared JSON contract `de.htwg.sysarch.mqtt` (topics + status/event/
  command DTOs), control-side adapters `MqttHmiGateway` / `MqttCommandRouter` over a
  testable `MqttConnection` port (`PahoMqttConnection`), `--mqtt` wiring with graceful
  fallback to console logging, and a standalone reference HMI `de.htwg.sysarch.hmi.
  HmiApplication` (depends only on the contract). 8 new broker-free tests; 21 total,
  all green.
- **2026-06-21** — Added a graphical HMI `de.htwg.sysarch.hmi.HmiSwingApplication`
  (Swing, no extra dependency): animated 4-level shaft with moving cabin, sliding
  doors, per-floor call lamps, live status read-outs, clickable call/emergency/reset
  buttons and an event log. `ShaftView` is the pure shaft renderer. Still depends only
  on the `mqtt` contract; the console `HmiApplication` is kept for headless/debug.
- **2026-06-23** — Lab-broker wiring: added MQTT username/password auth on both sides
  (`PahoMqttConnection` + HMI apps), with credentials resolved at runtime
  (`-Dmqtt.username/password` > `MQTT_USERNAME`/`MQTT_PASSWORD` env > properties, empty
  default so secrets stay out of git). Switched the base topic to the lab namespace
  `/SysArch/E`. Added maven-shade-plugin so `mvn package` yields a runnable
  `elevator-control-0.1.0.jar` for server deployment, and documented the SSH-tunnel
  deployment split (broker+control on `ea-pc165`, HMI on the laptop) in §7.
- **2026-06-25** — Made the split setup turnkey. Control and HMI are now separate Maven
  modules under `Control-HMI/`. Added PowerShell helper scripts (`run-control.ps1`,
  `run-hmi.ps1`, `deploy-control.ps1`) that source a gitignored `mqtt-credentials.ps1`
  (template: `mqtt-credentials.example.ps1`) so host/user/password are set once and stay
  out of git. **Verified the control system authenticates against the lab broker**
  (`ea-pc165:1883`, user `E`). Documented the Git-Bash `/topic` MSYS-mangling gotcha
  (use PowerShell) and the `deploy-control.ps1` build+scp flow in §3/§7.
- **2026-06-25** — Lowered the build target from Java 17 to **Java 11** so the deployed jar
  runs on the lab server (`ea-pc165` ships only OpenJDK 11; class-file 61 failed with
  `UnsupportedClassVersionError`). Rewrote all Java-17-only syntax to Java-11 equivalents
  (records → final classes with explicit accessors/equals/hashCode; switch-expressions/rules
  → classic `switch`; text blocks → concatenated strings) across the domain model, control,
  config, MQTT DTOs (both module copies) and the HMI apps. `maven.compiler.release=11` in
  both POMs. All 21 tests still green; jar verified as class-file 55 (Java 11).
