package de.htwg.sysarch.elevator.infrastructure.modbus;

import de.htwg.sysarch.elevator.domain.model.Level;

/**
 * Maps the Codesys I/O symbols (assignment §4.1) to Modbus addresses.
 *
 * <p>Convention used (standard Codesys → Modbus): {@code %QX a.b} → coil
 * {@code a*8+b}; {@code %IX a.b} → discrete input {@code a*8+b}; {@code %QW/%IW}
 * → register index; {@code %ID} → two consecutive registers.
 *
 * <p>⚠️ The concrete slave layout/offset must be VERIFIED against the running
 * simulation (CLAUDE.md §9). This is the single place to change if it differs.
 *
 * <p>Per-level bit layout within a level byte: {@code al=.0, sl=.1, r=.2, su=.3, au=.4}.
 * Level 1 has no {@code al}; level 4 has no {@code au} (those addresses are unused).
 */
public final class ModbusIoMap {

    private ModbusIoMap() {
    }

    private static int bit(int byteAddress, int bitAddress) {
        return byteAddress * 8 + bitAddress;
    }

    // --- Output coils (%QX) ---
    public static final int COIL_RESET = bit(0, 0);        // POreset    %QX0.0
    public static final int COIL_DOWN_V2 = bit(1, 0);      // POdv2      %QX1.0
    public static final int COIL_DOWN_V1 = bit(1, 1);      // POdv1      %QX1.1
    public static final int COIL_UP_V1 = bit(1, 2);        // POuv1      %QX1.2
    public static final int COIL_UP_V2 = bit(1, 3);        // POuv2      %QX1.3
    public static final int COIL_DOOR_CLOSE = bit(1, 4);   // POdclose   %QX1.4
    public static final int COIL_DOOR_OPEN = bit(1, 5);    // POdopen    %QX1.5

    // --- Output register (%QW) ---
    public static final int REG_CRAWL_SELECT = 1;          // POv_crawlSelect %QW1

    // --- Status discrete inputs (%IX10.*) ---
    public static final int DI_DOOR_OPENED = bit(10, 0);   // PIs_dopened
    public static final int DI_DOOR_CLOSED = bit(10, 1);   // PIs_dclosed
    public static final int DI_MOTOR_READY = bit(10, 2);   // PIm_ready
    public static final int DI_MOTOR_ON = bit(10, 3);      // PIm_on
    public static final int DI_MOTOR_ERROR = bit(10, 4);   // PIm_error

    // --- Input registers ---
    public static final int REG_CYCLES = 1;                // PIcycles  %ID1 (UDINT → regs 1,2)
    public static final int REG_AUFZUG_ID = 4;             // PIaufzugID %IW4
    public static final int REG_VELOCITY = 6;              // PIs_v     %IW6

    // --- Per-level cabin-detection discrete inputs ---
    // Byte per level: level1=%IX0, level2=%IX1, level3=%IX2, level4=%IX3.
    public static int approachLower(Level l) {
        return bit(l.ordinal(), 0);
    }

    public static int safetyLower(Level l) {
        return bit(l.ordinal(), 1);
    }

    public static int reached(Level l) {
        return bit(l.ordinal(), 2);
    }

    public static int safetyUpper(Level l) {
        return bit(l.ordinal(), 3);
    }

    public static int approachUpper(Level l) {
        return bit(l.ordinal(), 4);
    }
}
