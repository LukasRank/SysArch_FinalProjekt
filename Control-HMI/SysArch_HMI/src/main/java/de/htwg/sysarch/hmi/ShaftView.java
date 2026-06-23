package de.htwg.sysarch.hmi;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Set;

/**
 * Animated drawing of the 4-level elevator shaft. The cabin moves <em>continuously</em>:
 * its position is integrated from the reported velocity (so the speed is visible — fast at
 * v2≈100 cm/s, slow at crawl≈5 cm/s) and snapped to the exact floor whenever the cabin is
 * stationary there. This works for any transport that reports a velocity (simulation today,
 * the PLC's velocity register later) — no extra "position" signal is needed.
 *
 * <p>Pure view: it only renders the state pushed in via {@link #update}; it knows nothing
 * about MQTT or the controller.
 */
public final class ShaftView extends JPanel {

    private static final int LEVELS = 4;
    private static final int MARGIN = 18;
    private static final double CM_PER_LEVEL = 350.0; // 3.5 m floor height (assignment §6.1)
    private static final double MAX_CM_PER_S = 100.0; // v2, for the speed gauge scale

    private static final Color SHAFT_BG = new Color(0xF5F6F8);
    private static final Color SHAFT_BORDER = new Color(0xB0BEC5);
    private static final Color FLOOR_LINE = new Color(0xCFD8DC);
    private static final Color CABIN_INTERIOR = new Color(0x263238);
    private static final Color TEXT = new Color(0x37474F);

    // Continuous position, integrated from velocity. 1.0 = floor 1 … 4.0 = floor 4.
    private double positionLevel = 1;
    private boolean positioned;
    private long lastNanos = System.nanoTime();

    private int reportedLevel = 1;
    private int directionSign;        // +1 up, -1 down, 0 none
    private double speedCmPerS;       // magnitude
    private int signedVelocity;       // for the read-out
    private double doorFrac;          // 0 = closed, 1 = open (animated)
    private double doorTarget;
    private String phase = "IDLE";
    private boolean emergency;

    private boolean[] cabinCalls = new boolean[LEVELS + 1];
    private boolean[] upCalls = new boolean[LEVELS + 1];
    private boolean[] downCalls = new boolean[LEVELS + 1];

    public ShaftView() {
        setPreferredSize(new Dimension(320, 540));
        setBackground(Color.WHITE);
        new Timer(16, e -> animate()).start();
    }

    /** Push a new state snapshot to render. {@code velocity} is signed cm/s. */
    public void update(int level, String direction, String phase, String door,
                       int velocity, boolean emergency,
                       Set<Integer> cabin, Set<Integer> up, Set<Integer> down) {
        this.reportedLevel = level;
        this.directionSign = "UP".equals(direction) ? 1 : "DOWN".equals(direction) ? -1 : 0;
        this.speedCmPerS = Math.abs(velocity);
        this.signedVelocity = velocity;
        this.phase = phase;
        this.emergency = emergency;
        this.doorTarget = switch (door) {
            case "OPEN" -> 1.0;
            case "CLOSED" -> 0.0;
            default -> 0.5; // MOVING / UNKNOWN
        };
        this.cabinCalls = toFlags(cabin);
        this.upCalls = toFlags(up);
        this.downCalls = toFlags(down);

        // Re-anchor the integrated position to the exact floor whenever the cabin is
        // truly there (idle or doors), correcting any integration drift. While moving
        // we trust the velocity integration for smooth motion.
        boolean atFloor = "IDLE".equals(phase) || phase.startsWith("DOOR_");
        if (!positioned || atFloor) {
            positionLevel = level;
            positioned = true;
        }
        repaint();
    }

    private static boolean[] toFlags(Set<Integer> levels) {
        boolean[] flags = new boolean[LEVELS + 1];
        if (levels != null) {
            for (int l : levels) {
                if (l >= 1 && l <= LEVELS) {
                    flags[l] = true;
                }
            }
        }
        return flags;
    }

    private void animate() {
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (dt > 0.2) {
            dt = 0.2; // clamp after pauses so the cabin never leaps
        }
        // Integrate velocity → continuous position (cm/s ÷ cm-per-level = levels/s).
        positionLevel += directionSign * (speedCmPerS / CM_PER_LEVEL) * dt;
        positionLevel = Math.max(1, Math.min(LEVELS, positionLevel));
        doorFrac += (doorTarget - doorFrac) * 0.22;
        repaint();
    }

    private double floorHeight() {
        return (getHeight() - 2.0 * MARGIN) / LEVELS;
    }

    private double yForLevel(double level) {
        return getHeight() - MARGIN - (level - 0.5) * floorHeight();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        double fh = floorHeight();
        int gaugeW = 14;
        int shaftX = 64;
        int shaftW = w - shaftX - MARGIN - gaugeW - 8;

        // Shaft background + border
        g2.setColor(SHAFT_BG);
        g2.fillRoundRect(shaftX, MARGIN, shaftW, h - 2 * MARGIN, 12, 12);
        g2.setColor(SHAFT_BORDER);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(shaftX, MARGIN, shaftW, h - 2 * MARGIN, 12, 12);

        // Floor separators, labels and call lamps
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (int l = 1; l <= LEVELS; l++) {
            int yCenter = (int) yForLevel(l);
            int yTop = (int) (yForLevel(l) + fh / 2);
            if (l > 1) {
                g2.setColor(FLOOR_LINE);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(shaftX, yTop, shaftX + shaftW, yTop);
            }
            g2.setColor(TEXT);
            g2.drawString("E" + l, MARGIN - 4, yCenter + 6);
            drawCallLamps(g2, MARGIN + 22, yCenter, l);
        }

        Color cabinColor = phaseColor(phase, emergency);

        // Cabin at the continuous position
        double cy = yForLevel(positionLevel);
        double cabinH = fh * 0.62;
        double cabinW = shaftW * 0.66;
        double cx = shaftX + (shaftW - cabinW) / 2.0;
        double cyTop = cy - cabinH / 2.0;

        // Interior (revealed when doors open)
        g2.setColor(CABIN_INTERIOR);
        g2.fill(new RoundRectangle2D.Double(cx, cyTop, cabinW, cabinH, 10, 10));

        // Doors: two panels sliding apart by doorFrac
        double inset = 6;
        double doorAreaX = cx + inset;
        double doorAreaW = cabinW - 2 * inset;
        double halfW = doorAreaW / 2.0;
        double open = halfW * doorFrac;
        double doorTop = cyTop + inset;
        double doorH = cabinH - 2 * inset;
        g2.setColor(cabinColor);
        g2.fill(new RoundRectangle2D.Double(doorAreaX, doorTop, Math.max(0, halfW - open), doorH, 6, 6));
        g2.fill(new RoundRectangle2D.Double(doorAreaX + halfW + open, doorTop, Math.max(0, halfW - open), doorH, 6, 6));

        // Cabin frame
        g2.setColor(cabinColor.darker());
        g2.setStroke(new BasicStroke(2.5f));
        g2.draw(new RoundRectangle2D.Double(cx, cyTop, cabinW, cabinH, 10, 10));

        // Direction arrow above the cabin
        drawDirectionArrow(g2, (int) (cx + cabinW / 2), (int) cyTop - 9, cabinColor);

        // Velocity read-out just below the cabin (always visible)
        g2.setColor(TEXT);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        String v = signedVelocity + " cm/s";
        int sw = g2.getFontMetrics().stringWidth(v);
        g2.drawString(v, (int) (cx + cabinW / 2 - sw / 2.0), (int) (cy + cabinH / 2 + 16));

        // Speed gauge on the right edge (fills from the bottom, scaled to v2)
        drawSpeedGauge(g2, w - MARGIN - gaugeW, MARGIN, gaugeW, h - 2 * MARGIN, cabinColor);

        g2.dispose();
    }

    private void drawSpeedGauge(Graphics2D g2, int x, int y, int gw, int gh, Color color) {
        g2.setColor(new Color(0xECEFF1));
        g2.fillRoundRect(x, y, gw, gh, 6, 6);
        double frac = Math.min(1.0, speedCmPerS / MAX_CM_PER_S);
        int filled = (int) (gh * frac);
        g2.setColor(color);
        g2.fillRoundRect(x, y + gh - filled, gw, filled, 6, 6);
        g2.setColor(SHAFT_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, gw, gh, 6, 6);
        // tick marks for crawl(5)/v1(10)/v2(100)
        g2.setColor(new Color(0x90A4AE));
        for (double mark : new double[] {5, 10, 100}) {
            int my = (int) (y + gh - gh * (mark / MAX_CM_PER_S));
            g2.drawLine(x, my, x + gw, my);
        }
    }

    private void drawCallLamps(Graphics2D g2, int x, int yCenter, int level) {
        int r = 7;
        lamp(g2, x, yCenter - 9, r, cabinCalls[level], new Color(0x1565C0));
        lamp(g2, x, yCenter + 6, r, upCalls[level], new Color(0x2E7D32));
        lamp(g2, x + 16, yCenter + 6, r, downCalls[level], new Color(0xEF6C00));
    }

    private static void lamp(Graphics2D g2, int x, int y, int r, boolean on, Color onColor) {
        g2.setColor(on ? onColor : new Color(0xE0E0E0));
        g2.fillOval(x, y, r, r);
        g2.setColor(new Color(0xB0BEC5));
        g2.drawOval(x, y, r, r);
    }

    private void drawDirectionArrow(Graphics2D g2, int cx, int y, Color color) {
        if (directionSign == 0) {
            return;
        }
        Path2D p = new Path2D.Double();
        int s = 9;
        if (directionSign > 0) {
            p.moveTo(cx, y - s);
            p.lineTo(cx - s, y + s);
            p.lineTo(cx + s, y + s);
        } else {
            p.moveTo(cx, y + s);
            p.lineTo(cx - s, y - s);
            p.lineTo(cx + s, y - s);
        }
        p.closePath();
        g2.setColor(color);
        g2.fill(p);
    }

    private static Color phaseColor(String phase, boolean emergency) {
        if (emergency) {
            return new Color(0xD32F2F);
        }
        return switch (phase) {
            case "MOVING" -> new Color(0x2E7D32);
            case "DECELERATING", "CRAWLING" -> new Color(0xEF6C00);
            case "DOOR_OPENING", "DOOR_OPEN", "DOOR_CLOSING" -> new Color(0x1565C0);
            case "EMERGENCY" -> new Color(0xD32F2F);
            default -> new Color(0x546E7A);
        };
    }
}
