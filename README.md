# SysArch_FinalProjekt — Elevator Control System (Group E / C2)

Control system for the HTWG Konstanz "System Architecture" elevator project.
It drives the lab elevator **simulation** (4 levels) as a **Modbus/TCP master**
(~100 ms cycle) and implements the full collective-control (SCAN) logic.

> 📄 **Start with [`CLAUDE.md`](CLAUDE.md)** — it is the single source of truth
> for goal, architecture, I/O mapping, configuration and open questions, and is
> kept in sync with every change.

## Quick start

```bash
mvn test                                  # run unit tests
# Run against the in-memory simulation (no lab/VPN needed):
mvn exec:java -Dexec.args="--sim"
# Run against the real PLC (requires HTWG network/VPN, Group E port 506):
mvn exec:java -Dexec.args="--modbus"
```

## Architecture (hexagonal / ports & adapters)

```
HMI / tests ──(OperatorPanel)──▶ Application ──(PlcGateway/HmiGateway)──▶ PLC(Modbus) / HMI
                                      │
                                      ▼
                                  Domain (pure SCAN control logic)
```

See [`CLAUDE.md`](CLAUDE.md) for the full package map and requirement traceability.
