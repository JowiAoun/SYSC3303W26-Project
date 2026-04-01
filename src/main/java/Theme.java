import java.awt.*;

public final class Theme {
    private Theme() {}

    // === BACKGROUNDS ===
    public static final Color BG_MAIN        = new Color(0x48, 0x64, 0x3C);       // #48643c olive green
    public static final Color BG_PANEL       = new Color(0x35, 0x4A, 0x2C);       // darker olive green
    public static final Color BG_CARD        = new Color(45, 62, 37);       // darker olive green

    // === TEXT ===
    public static final Color TEXT_PRIMARY   = new Color(0xDD, 0xC0, 0x26);       // #ddc026 gold/yellow
    public static final Color TEXT_SECONDARY = new Color(156, 142, 102);       // #999381 sand
    public static final Color TEXT_TERTIARY  = new Color(0x84, 0x75, 0x52);       // #847552 khaki
    public static final Color TEXT_BRIGHT    = new Color(0xDD, 0xC0, 0x26);       // #ddc026 gold/yellow

    // === GRID / MAP ===
    public static final Color GRID_LINE_FAINT  = new Color(0x84, 0x75, 0x52, 50);     // faint khaki
    public static final Color GRID_LINE_ZONE   = new Color(0x84, 0x75, 0x52, 100);    // khaki
    public static final Color ZONE_SAFE        = new Color(0x48, 0x64, 0x3C);         // olive green
    public static final Color ZONE_EXTINGUISHED = new Color(0x84, 0x75, 0x52);        // khaki

    // === FIRE COLORS ===
    public static final Color FIRE_ACTIVE     = new Color(200, 49, 18);      // #692c1f dark red
    public static final Color FIRE_HIGH       = new Color(138, 52, 32);
    public static final Color FIRE_MODERATE   = new Color(0x69, 0x2C, 0x1F);      
    public static final Color FIRE_LOW        = new Color(74, 32, 21);

    // === DRONE COLORS ===
    public static final Color DRONE_OUTBOUND    = new Color(0x84, 0x75, 0x52);    // khaki
    public static final Color DRONE_FIGHTING    = new Color(234, 201, 27);    // gold
    public static final Color DRONE_RETURNING   = new Color(0x99, 0x93, 0x81);    // sand
    public static final Color DRONE_REFILLING   = new Color(0x99, 0x93, 0x81);    
    public static final Color DRONE_IDLE        = new Color(0x84, 0x75, 0x52);    // khaki
    public static final Color WATER_LEVEL       = new Color(60, 160, 240);        // blue
    public static final Color WATER_LOW         = new Color(40, 100, 180);        // dark blue
    public static final Color WATER_CRITICAL    = new Color(20, 50, 100);         // very dark blue

    // === FAULT COLORS ===
    public static final Color FAULT_SOFT      = new Color(0xDD, 0xC0, 0x26);      // gold
    public static final Color FAULT_HARD      = new Color(0x69, 0x2C, 0x1F);      // dark red

    // === BORDERS ===
    public static final Color BORDER_DEFAULT  = new Color(0x84, 0x75, 0x52);      // khaki
    public static final Color BORDER_ACTIVE   = new Color(0xDD, 0xC0, 0x26);      // gold

    // === OVERLAY EFFECTS ===
    public static final Color SCANLINE        = new Color(0x84, 0x75, 0x52, 20);  // faint khaki
    public static final Color RADAR_SWEEP     = new Color(0xDD, 0xC0, 0x26, 40);  // translucent gold

    // === FONTS ===
    public static final Font FONT_MONO       = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD  = new Font(Font.MONOSPACED, Font.BOLD, 12);
    public static final Font FONT_MONO_SMALL = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    public static final Font FONT_HEADER     = new Font(Font.MONOSPACED, Font.BOLD, 18);
    public static final Font FONT_TITLE      = new Font(Font.MONOSPACED, Font.BOLD, 14);

    // === MAP CONFIGURATION ===
    public static final int MAP_RESOLUTION = 600; // 600x600 px map
    public static final int GRID_COLS = 200;
    public static final int GRID_ROWS = 200;
    public static final int ZONE_PIXEL_SIZE = MAP_RESOLUTION / GRID_COLS; // 125 pixels per zone cell

    // === ANIMATION ===
    public static final int FRAME_DELAY_MS = 33;    // ~30 FPS
    public static final int FIRE_FLICKER_MS = 150;  // fire flicker interval
    public static final int RADAR_SWEEP_MS = 50;    // radar sweep update interval
}
