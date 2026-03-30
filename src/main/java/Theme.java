import java.awt.*;

public final class Theme {
    private Theme() {}

    // === BACKGROUNDS ===
    public static final Color BG_MAIN        = new Color(0x0A, 0x0A, 0x0A);       // #0A0A0A near-black
    public static final Color BG_PANEL       = new Color(0x0D, 0x1A, 0x0D);       // #0D1A0D dark olive
    public static final Color BG_CARD        = new Color(0x1A, 0x2A, 0x1A);       // #1A2A1A slightly lighter olive

    // === TEXT ===
    public static final Color TEXT_PRIMARY   = new Color(0xFF, 0xB0, 0x00);       // #FFB000 amber
    public static final Color TEXT_SECONDARY = new Color(0x44, 0xAA, 0x44);       // #44AA44 dim green
    public static final Color TEXT_TERTIARY  = new Color(0xAA, 0x88, 0x00);       // #AA8800 dim amber
    public static final Color TEXT_BRIGHT    = new Color(0x00, 0xFF, 0x41);       // #00FF41 CRT green

    // === GRID / MAP ===
    public static final Color GRID_LINE_FAINT  = new Color(0x1A, 0x3A, 0x1A);    // #1A3A1A dim green
    public static final Color GRID_LINE_ZONE   = new Color(0x2A, 0x5A, 0x2A);    // #2A5A2A medium green
    public static final Color ZONE_SAFE        = new Color(0x0D, 0x1A, 0x0D);    // #0D1A0D base dark olive
    public static final Color ZONE_EXTINGUISHED = new Color(0x11, 0x44, 0x11);   // #114411 darker green

    // === FIRE COLORS ===
    public static final Color FIRE_ACTIVE     = new Color(0xFF, 0x22, 0x00);      // #FF2200 bright red
    public static final Color FIRE_HIGH       = new Color(0xFF, 0x00, 0x00);      // #FF0000 pulsing red
    public static final Color FIRE_MODERATE   = new Color(0xFF, 0x66, 0x00);      // #FF6600 orange
    public static final Color FIRE_LOW        = new Color(0xFF, 0xAA, 0x00);      // #FFAA00 yellow-amber

    // === DRONE COLORS ===
    public static final Color DRONE_OUTBOUND    = new Color(0x00, 0xFF, 0x41);    // #00FF41 bright green
    public static final Color DRONE_FIGHTING    = new Color(0xFF, 0xB0, 0x00);    // #FFB000 bright amber
    public static final Color DRONE_RETURNING   = new Color(0x00, 0xDD, 0xAA);    // #00DDAA cyan-green
    public static final Color DRONE_REFILLING   = new Color(0x00, 0xAA, 0xDD);    // #00AADD blue-green
    public static final Color DRONE_IDLE        = new Color(0x22, 0xAA, 0x22);    // #22AA22 dim green

    // === FAULT COLORS ===
    public static final Color FAULT_SOFT      = new Color(0xFF, 0xAA, 0x00);      // #FFAA00 amber
    public static final Color FAULT_HARD      = new Color(0xFF, 0x00, 0x00);      // #FF0000 bright red

    // === BORDERS ===
    public static final Color BORDER_DEFAULT  = new Color(0x2A, 0x3A, 0x2A);     // dim green border
    public static final Color BORDER_ACTIVE   = new Color(0x00, 0xFF, 0x41);     // bright green for active elements

    // === OVERLAY EFFECTS ===
    public static final Color SCANLINE        = new Color(0x00, 0xFF, 0x08, 0x08); // very faint green scanline
    public static final Color RADAR_SWEEP     = new Color(0x00, 0xFF, 0x41, 0x20); // translucent green sweep

    // === FONTS ===
    public static final Font FONT_MONO       = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD  = new Font(Font.MONOSPACED, Font.BOLD, 12);
    public static final Font FONT_MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    public static final Font FONT_HEADER     = new Font(Font.MONOSPACED, Font.BOLD, 18);
    public static final Font FONT_TITLE      = new Font(Font.MONOSPACED, Font.BOLD, 14);

    // === MAP CONFIGURATION ===
    public static final int MAP_RESOLUTION = 600; // 600x600 px map
    public static final int GRID_COLS = 100;
    public static final int GRID_ROWS = 100;
    public static final int ZONE_PIXEL_SIZE = MAP_RESOLUTION / GRID_COLS; // 125 pixels per zone cell

    // === ANIMATION ===
    public static final int FRAME_DELAY_MS = 33;    // ~30 FPS
    public static final int FIRE_FLICKER_MS = 150;  // fire flicker interval
    public static final int RADAR_SWEEP_MS = 50;    // radar sweep update interval
}
