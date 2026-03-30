# GUI Revamp Plan — Fire Drone Simulation

> **Audience**: This document is a step-by-step implementation plan for an AI coding agent. Each phase must be completed in order. The human reviewer will approve each phase before the next begins.

> **Design Direction**: Tactical / Military HUD — pixelated dark-green-on-black radar aesthetic, amber text, CRT scanline effects, monospace fonts. The grid is a single high-resolution pixel canvas (no per-cell Swing components). No text on the grid — all state info lives in the side panels.

---

## Codebase Reference

All source files are in `src/main/java/` (flat, no packages). Build system is Maven (`pom.xml`).

### Files you will modify

| File | Class | Role |
|------|-------|------|
| `FireDroneGUI.java` | `FireDroneGUI extends JFrame` (774 lines) | Main window. Holds the grid, sidebar, toolbar, status bar. Updated by `Scheduler` via three public methods (see below). |
| `ZoneCell.java` | `ZoneCell extends JLabel` (147 lines) | Single grid cell. **Will be deleted** — replaced by `TacticalMapPanel`. |
| `ZoneLoader.java` | `ZoneLoader` (164 lines) | Loads `zones.csv`, assigns colors via `pickColor()`. Colors must change to military palette. |
| `ZoneDef.java` | `ZoneDef` (46 lines) | Zone geometry data class. Has a `baseColor` field — palette will change. |
| `Main.java` | `Main` (76 lines) | Entry point. Creates `FireDroneGUI(droneCount)`, passes it to `Scheduler`. Minor changes only (FlatLaf init). |
| `pom.xml` | — | Add FlatLaf dependency. |

### Files you must NOT modify (backend — read-only reference)

| File | Why you need it |
|------|----------------|
| `Scheduler.java` | Calls `gui.updateDroneStatus(DroneStatus)`, `gui.setZoneFire(int, boolean, Severity)`, `gui.appendEvent(String)`. These three public method signatures are the contract — do not change them. The `Scheduler` constructor takes `FireDroneGUI gui`. |
| `DroneStatus.java` | Immutable data: `droneId`, `state` (DroneState enum), `zoneId`, `remainingLiters`, `currentCol`, `currentRow`, `faultType` (FaultType enum). |
| `DroneState.java` | Enum: `IDLE, EN_ROUTE, EXTINGUISHING, RETURNING, REFILLING, FAULTED`. |
| `FaultType.java` | Enum: `NONE, STUCK_MID_FLIGHT, NOZZLE_JAM, ARRIVAL_SENSOR_FAILURE, CORRUPTED_MESSAGE`. |
| `FireEvent.java` | Has inner enums `EventType` (`FIRE_DETECTED`, `DRONE_REQUEST`), `Severity` (`LOW`, `MODERATE`, `HIGH`). Used by fault injection dialog and `setZoneFire`. |
| `SwarmNetwork.java` | Network constants. Used by fault injection UDP send. |
| `DroneSubsystem.java` | Drone logic. Do not touch. |
| `FireIncidentSubsystem.java` | Fire event source. Do not touch. |

### Public API contract (DO NOT CHANGE these signatures)

The `Scheduler` calls these methods on `FireDroneGUI`. They must continue to exist with the same signatures:

```java
public void updateDroneStatus(DroneStatus status)
public void setZoneFire(int zoneId, boolean active, FireEvent.Severity severity)
public void setZoneFire(int zoneId, boolean active)  // backward-compat overload
public void appendEvent(String message)
```

The `Scheduler` constructor: `new Scheduler(gui)` where `gui` is `FireDroneGUI`. The `Main` class creates `new FireDroneGUI(droneCount)`. These constructors must remain compatible.

### Data files

- `src/main/resources/data/zones.csv` — 8 zones (zone 0 is base at (0,0)-(2,2), zones 1-7 cover the rest of the 16x16 grid)
- `src/main/resources/data/events.csv` — 7 fire events with timestamps and severities

---

## Phase 1 — Add FlatLaf Dependency and Create Theme Class

### Step 1.1: Add FlatLaf to `pom.xml`

Add this dependency inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.formdev</groupId>
    <artifactId>flatlaf</artifactId>
    <version>3.5.4</version>
</dependency>
```

Run `mvn dependency:resolve` to verify it downloads.

### Step 1.2: Create `src/main/java/Theme.java`

Create a new file `Theme.java` with all color, font, and dimension constants. This is the single source of truth for all visual styling.

```java
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

    // === DIMENSIONS ===
    public static final int MAP_RESOLUTION = 2000;  // tactical map BufferedImage size (pixels)
    public static final int GRID_COLS = 16;
    public static final int GRID_ROWS = 16;
    public static final int ZONE_PIXEL_SIZE = MAP_RESOLUTION / GRID_COLS; // 125 pixels per zone cell

    // === ANIMATION ===
    public static final int FRAME_DELAY_MS = 33;    // ~30 FPS
    public static final int FIRE_FLICKER_MS = 150;  // fire flicker interval
    public static final int RADAR_SWEEP_MS = 50;    // radar sweep update interval
}
```

### Step 1.3: Initialize FlatLaf in `Main.java`

In `Main.java`, replace the look-and-feel setup. Currently `FireDroneGUI.main()` (line 764) sets system L&F. The actual app entry point is `Main.main()` which has no L&F setup.

Add FlatLaf initialization at the very start of `Main.main()`, before creating the GUI:

```java
import com.formdev.flatlaf.FlatDarkLaf;
// ... at top of main():
try {
    FlatDarkLaf.setup();
} catch (Exception ignored) {}
```

Also update `FireDroneGUI.main()` (the standalone demo entry point at line 762) to use FlatLaf instead of system L&F.

### Step 1.4: Update `ZoneLoader.pickColor()` to use military palette

In `ZoneLoader.java`, method `pickColor()` (lines 99-110), replace the pastel palette with dark olive/green shades that differentiate zones subtly on the tactical map. Zone 0 (base) should be a distinct dark gray-green. All colors should be dark — they are zone fill colors on a dark background.

### Step 1.5: Verify

Run `mvn compile` to confirm everything compiles. Run `Main.main()` to verify FlatLaf applies and the window opens with the dark theme. The grid will still be the old `ZoneCell`-based grid at this point — that's fine. The point of this phase is just to get the theme infrastructure in place.

---

## Phase 2 — Create TacticalMapPanel (Single-Canvas High-Res Grid)

This is the most critical phase. Replace the 16x16 grid of `ZoneCell` JLabels with a single `JPanel` that paints a ~2000x2000 pixel `BufferedImage`.

### Step 2.1: Create `src/main/java/TacticalMapPanel.java`

Create a new class:

```java
public class TacticalMapPanel extends JPanel {
    ...
}
```

**Requirements:**

1. **Fields:**
   - `BufferedImage mapImage` — the tactical map (dimensions: `Theme.MAP_RESOLUTION x Theme.MAP_RESOLUTION`, type `BufferedImage.TYPE_INT_ARGB`)
   - `List<ZoneDef> zones` — zone definitions (passed in constructor)
   - `Map<Integer, Boolean> zoneFireActive` — tracks which zones have active fires (zoneId -> true/false)
   - `Map<Integer, FireEvent.Severity> zoneSeverities` — tracks fire severity per zone
   - `Map<Integer, DroneStatus> droneStatuses` — current status of each drone (keyed by droneId)
   - `javax.swing.Timer animationTimer` — drives repaint at ~30 FPS
   - `int radarAngle` — current angle for radar sweep effect (0-360, incremented each frame)
   - `long frameCount` — incremented each frame, used for animation timing

2. **Constructor:** `TacticalMapPanel(List<ZoneDef> zones)`
   - Initialize the `BufferedImage`
   - Start the animation timer (`Theme.FRAME_DELAY_MS` interval) that calls `repaint()`
   - Set preferred size to something reasonable (e.g., 800x800) — the panel will scale

3. **Core rendering method:** `private void renderMap()`
   - Get `Graphics2D` from `mapImage`
   - Enable antialiasing / rendering hints
   - **Layer 1 — Background:** Fill entire image with `Theme.BG_MAIN`
   - **Layer 2 — Zone fills:** For each `ZoneDef`, compute the pixel rectangle: `(zone.startCol * ZONE_PIXEL_SIZE, zone.startRow * ZONE_PIXEL_SIZE, zone.widthCols * ZONE_PIXEL_SIZE, zone.heightRows * ZONE_PIXEL_SIZE)`. Fill with:
     - `Theme.ZONE_SAFE` if no fire
     - Fire color (flickering between `Theme.FIRE_HIGH/FIRE_MODERATE/FIRE_LOW` based on severity) if fire is active. Use `frameCount` to drive flicker — e.g., alternate colors every few frames with some randomness per zone
     - `Theme.ZONE_EXTINGUISHED` if fire was just extinguished (track this state)
   - **Layer 3 — Grid lines:** Draw faint horizontal and vertical lines at each cell boundary (every `ZONE_PIXEL_SIZE` pixels) using `Theme.GRID_LINE_FAINT`. Draw thicker lines at zone boundaries using `Theme.GRID_LINE_ZONE`.
   - **Layer 4 — Drones:** For each drone in `droneStatuses`:
     - Skip IDLE drones at non-base zones (same logic as current `shouldRenderDrone`)
     - Compute pixel position: `(currentCol * ZONE_PIXEL_SIZE + ZONE_PIXEL_SIZE/2, currentRow * ZONE_PIXEL_SIZE + ZONE_PIXEL_SIZE/2)` — center of the cell
     - Draw a filled diamond or chevron shape (roughly 8-12 pixels wide) at that position
     - Color based on drone state: EN_ROUTE=`DRONE_OUTBOUND`, EXTINGUISHING=`DRONE_FIGHTING`, RETURNING=`DRONE_RETURNING`, REFILLING=`DRONE_REFILLING`, IDLE=`DRONE_IDLE`, FAULTED=`FAULT_SOFT` or `FAULT_HARD` depending on fault type
     - **No text on the map** — just the colored shape
   - **Layer 5 — Radar sweep:** Draw a translucent green wedge from the center of the map, spanning ~30 degrees, at angle `radarAngle`. Use `Theme.RADAR_SWEEP` color. Increment `radarAngle` each frame.
   - **Layer 6 — Scanlines:** Draw faint horizontal lines every 3 pixels across the entire image using `Theme.SCANLINE` color (very low alpha). This creates the CRT effect.
   - Dispose the `Graphics2D`

4. **`paintComponent(Graphics g)`:**
   - Call `renderMap()` to update the `BufferedImage`
   - Draw the `BufferedImage` scaled to fit the panel: `g.drawImage(mapImage, 0, 0, getWidth(), getHeight(), null)`
   - This automatically scales the 2000x2000 image to whatever size the panel is on screen

5. **Public update methods** (called by `FireDroneGUI` when it receives updates from `Scheduler`):
   - `public void updateDrone(DroneStatus status)` — puts the status into `droneStatuses` map. Repaint happens automatically via the timer.
   - `public void setZoneFire(int zoneId, boolean active, FireEvent.Severity severity)` — updates `zoneFireActive` and `zoneSeverities` maps
   - These methods must be thread-safe (use `synchronized` or call on EDT)

6. **Coordinate mapping** (for future click handling):
   - `public int[] screenToGrid(int screenX, int screenY)` — converts mouse click coordinates to `{col, row}` by: `col = screenX * GRID_COLS / getWidth()`, `row = screenY * GRID_ROWS / getHeight()`

### Step 2.2: Integrate TacticalMapPanel into FireDroneGUI

In `FireDroneGUI.java`:

1. **Remove** the `ZoneCell[][] gridCells` field (line 24) and the `CellState` enum (lines 27-36). Remove `createGridPanel()` (lines 90-118). Remove `setCellState()`, `setCellText()`, `recomputeCell()`, `restoreCellDefault()`, `droneShortLabel()`, `droneCellState()`, `statePriority()`. Remove the legend panel (`createLegendPanel()`, `createLegendRow()`) — it showed CellState color samples which no longer apply.

2. **Add** a field: `private TacticalMapPanel tacticalMap;`

3. **In the constructor**, replace `createGridPanel(COLS, ROWS)` with:
   ```java
   tacticalMap = new TacticalMapPanel(zones);
   ```
   Replace the `JScrollPane(gridPanel)` with just the `tacticalMap` panel directly (no scroll pane needed — the map scales to fit).

4. **Update `updateDroneStatus(DroneStatus status)`:** Keep the tracking logic for `activeDroneIds`, `faultedDroneIds`, `permanentlyFaultedDrones`, `droneCurrentStatuses`, and the sidebar label update. But replace the cell recompute calls (`recomputeCell(...)`) with:
   ```java
   tacticalMap.updateDrone(status);
   ```

5. **Update `setZoneFire(int zoneId, boolean active, FireEvent.Severity severity)`:** Keep the `activeZoneIds` / counter tracking and the `updateZoneLabel()` call for the sidebar. But replace the `setCellState` / `setCellText` calls with:
   ```java
   tacticalMap.setZoneFire(zoneId, active, severity);
   ```

6. **Remove** the `findZoneForCell()` method from `FireDroneGUI` (it was used for grid cell creation). The one in `ZoneLoader`/`ZoneDef` still exists if needed.

### Step 2.3: Delete `ZoneCell.java`

This file is no longer needed. Delete it entirely.

### Step 2.4: Verify

Run `mvn compile`. Run `Main.main()`. You should see:
- A dark window with the tactical map in the center showing the zone grid as colored rectangles on a dark background
- Radar sweep animation rotating
- Faint scanlines
- The sidebar still shows zone/drone labels (unstyled yet — that's Phase 4)
- When the simulation runs, fires should appear as colored zones on the map progressively (not all at once)
- Drones should appear as colored blips moving across the map

---

## Phase 3 — Restyle the Layout (Header, Sidebars, Comms Log)

Restructure the window layout from the current `[toolbar | splitpane(grid, sidebar) | statusbar]` to the tactical command layout.

### Step 3.1: Redesign the overall layout

Replace the current layout in the `FireDroneGUI` constructor. The new layout should be:

```
+--------------------------------------------------------------+
|  HEADER BAR (BorderLayout.NORTH)                              |
|  "TACTICAL DRONE COMMAND" + live counters + clock             |
+--------------------------------------------------------------+
|  LEFT SIDEBAR  |  TACTICAL MAP (center)  |  RIGHT SIDEBAR    |
|  Zone cards    |  TacticalMapPanel       |  Drone cards       |
|  (200-250px)   |  (fills remaining)      |  (250-300px)       |
+--------------------------------------------------------------+
|  COMMS LOG (BorderLayout.SOUTH, ~150px tall)                  |
|  Terminal-style event log + controls                          |
+--------------------------------------------------------------+
```

Use `BorderLayout` for the main frame. The center section (left sidebar + map + right sidebar) can use a `BorderLayout` or a panel with `BorderLayout` where the map is `CENTER` and sidebars are `WEST`/`EAST`.

### Step 3.2: Create the Header Bar

Replace `createToolbar()` with a new `createHeaderBar()` method that returns a `JPanel`:

- Background: `Theme.BG_PANEL`
- Left: Title label "TACTICAL DRONE COMMAND" in `Theme.FONT_HEADER` color `Theme.TEXT_PRIMARY`
- Center: Live counters as labels — "FIRES: 0", "DRONES: 0", "FAULTS: 0" in `Theme.TEXT_BRIGHT` (update these via the existing `setActiveFires/setActiveDrones/setFaultedDrones` methods)
- Right: "INJECT FAULT" button styled with `Theme.BG_CARD` background, `Theme.TEXT_PRIMARY` foreground, `Theme.FONT_MONO_BOLD` font. Plus a clock label showing current time in HH:MM:SS format (military time), updated by a 1-second Swing Timer.
- Thin border on bottom edge using `Theme.GRID_LINE_ZONE`
- Total height ~50-60px

### Step 3.3: Rework Zone Panel (left sidebar)

Replace `createZonesList()` and the zone card. Create a new `createZonePanel()` method:

- Background: `Theme.BG_PANEL`
- For each zone, create a small panel (card-like):
  - Background: `Theme.BG_CARD`
  - Border: 1px `Theme.BORDER_DEFAULT`, changes to `Theme.FIRE_ACTIVE` when zone has fire
  - Zone ID label: `Theme.TEXT_PRIMARY`, monospace
  - Coordinates label: `Theme.TEXT_SECONDARY`, monospace, smaller
  - Fire status: severity badge text (H/M/L) in the appropriate fire color, or "CLEAR" in `Theme.TEXT_SECONDARY`
- Wrap in a `JScrollPane` with dark-themed scrollbar
- Preferred width: 220px

### Step 3.4: Rework Drone Panel (right sidebar)

Replace `createDroneList()`. Create a new `createDronePanel()` method:

- Background: `Theme.BG_PANEL`
- For each drone, create a card panel:
  - Background: `Theme.BG_CARD`
  - Border: 1px `Theme.BORDER_DEFAULT`
  - Drone ID: "DRONE n" in `Theme.TEXT_PRIMARY`, monospace bold
  - State label: current state text in the drone's state color (use `Theme.DRONE_OUTBOUND` for EN_ROUTE, etc.)
  - Target zone: "-> ZONE n" in `Theme.TEXT_SECONDARY`
  - Water gauge: a custom-painted horizontal bar showing `remainingLiters / 15` fill. Green when > 30%, yellow when > 10%, red when <= 10%
  - Fault status: if faulted, show fault type in `Theme.FAULT_SOFT` or `Theme.FAULT_HARD` color
  - Hard-faulted drones: entire card gets dimmed, red border, "OFFLINE" text overlay
- Wrap in a `JScrollPane`
- Preferred width: 270px

### Step 3.5: Rework Event Log (bottom panel) into Comms Log

Replace `createEventPreview()`. Create a new `createCommsLog()` method:

- Use `JTextPane` instead of `JTextArea` to support colored text
- Background: `Theme.BG_MAIN` (pure black feel)
- Font: `Theme.FONT_MONO` in `Theme.TEXT_BRIGHT` (green on black — terminal aesthetic)
- Height: ~150px (use preferred size on the scroll pane)
- Update `appendEvent(String message)` to insert styled text into the `JTextPane`:
  - Parse the message for keywords: if it contains "FIRE" or "fire" → red text
  - If it contains "FAULT" or "fault" → amber text (`Theme.FAULT_SOFT`)
  - If it contains "extinguished" or "SAFE" → green text (`Theme.TEXT_BRIGHT`)
  - Default → dim green (`Theme.TEXT_SECONDARY`)
- Auto-scroll to bottom on each append

### Step 3.6: Restyle the Fault Injection Dialog

Update `showFaultInjectionDialog()`:

- Replace `JOptionPane.showConfirmDialog` with a custom `JDialog`
- Background: `Theme.BG_PANEL`
- All labels: `Theme.TEXT_PRIMARY`, monospace
- Input fields (combo boxes, spinner): dark background, green/amber text
- Buttons: "CONFIRM" and "ABORT" instead of OK/Cancel, styled with `Theme.BG_CARD` background and `Theme.TEXT_PRIMARY` foreground
- Title: "FAULT INJECTION" in `Theme.FONT_TITLE`

### Step 3.7: Remove the Legend panel

The legend showed `CellState` color examples which no longer apply (grid is now purely visual). Remove `createLegendPanel()` and `createLegendRow()` entirely, and remove the legend card from the sidebar.

### Step 3.8: Verify

Run the application. The window should now have:
- Dark military-themed header bar with title, counters, clock, and styled fault button
- Left sidebar with dark zone cards showing zone info
- Center tactical map with animations
- Right sidebar with dark drone cards showing state, water gauge, fault info
- Bottom comms log with green-on-black terminal-style colored text
- All standard Swing widgets (scrollbars, buttons, dialogs) themed dark by FlatLaf

---

## Phase 4 — Tactical Map Visual Polish

Enhance the `TacticalMapPanel` rendering with better visual effects.

### Step 4.1: Improve Fire Rendering

Currently fires are flat colored rectangles. Improve them:

- For active fire zones, instead of filling the entire zone rectangle with one color, paint scattered "hot pixel" clusters:
  - Fill zone with a very dark red base (`new Color(0x2A, 0x0A, 0x0A)`)
  - Paint random bright pixels (red/orange/yellow) that change each frame — use `frameCount` as seed to deterministic randomness so it doesn't strobe too hard
  - Higher severity = more bright pixels, more red vs orange
  - The effect should look like thermal imaging of a fire
- When a fire is extinguished, briefly flash the zone bright green for ~10 frames, then settle to `Theme.ZONE_EXTINGUISHED`

### Step 4.2: Improve Drone Rendering

- Draw drones as a small filled diamond (rotated square) or chevron/arrow shape, not just a dot
- The shape should point in the direction of travel (if drone is EN_ROUTE, point toward target zone center; if RETURNING, point toward zone 0)
- Add a subtle pulsing glow around the drone blip (expand/contract a translucent circle by 1-2 pixels each frame)
- Faulted drones should blink (visible every other ~15 frames)

### Step 4.3: Improve Radar Sweep

- The radar sweep should originate from the center of the map
- It should be a filled arc/wedge spanning ~30 degrees
- The leading edge should be brighter, fading to transparent at the trailing edge
- Use `AlphaComposite` for the translucent overlay

### Step 4.4: Drone Trail (stretch goal)

If you have room in the render loop:
- Track the last N positions (e.g., 8) of each drone in a list
- For each past position, draw a smaller, more transparent version of the drone blip
- Creates a "radar trace" fading trail effect

### Step 4.5: Verify

Run the simulation and confirm:
- Fire zones show flickering thermal-style pixels
- Drones are clearly visible as directional blips
- Radar sweep rotates smoothly
- Fire extinguish produces a green flash transition
- Performance stays smooth (~30 FPS). If laggy, reduce effects or lower `MAP_RESOLUTION` to 1500.

---

## Phase 5 — Animations and Final Polish

### Step 5.1: Animate fire zone borders in the sidebar

When a zone has an active fire, its zone card in the left sidebar should have a pulsing border — alternate between `Theme.FIRE_ACTIVE` and `Theme.FIRE_HIGH` every 500ms using a Swing Timer.

### Step 5.2: Animate faulted drone cards in the sidebar

When a drone is faulted, its card in the right sidebar should blink — toggle the border between `Theme.FAULT_SOFT/FAULT_HARD` and `Theme.BORDER_DEFAULT` every 750ms.

### Step 5.3: Transition effects on the comms log

When a critical event is logged (fire detected, fault injected), briefly flash the comms log background to a slightly brighter shade for 200ms, then fade back. This draws the eye to new critical events.

### Step 5.4: CRT startup effect (stretch goal)

When the application launches, briefly show a "boot sequence" effect on the tactical map — horizontal lines expanding from center, text "INITIALIZING TACTICAL DISPLAY..." in green monospace, then fade into the live map. This is purely cosmetic but impressive for a demo.

### Step 5.5: Verify

Full end-to-end run of the simulation. Confirm:
- Fires appear progressively as the simulation runs (not all at once on startup)
- Drones dispatch, travel (blips move), fight fires, return
- Faults show correctly in both the map and sidebar
- All animations are smooth
- No heap errors, no visual glitches
- The window looks like a military tactical command center, not a Java Swing application

---

## Phase 6 — Additional Features (Competition Differentiators)

These are optional enhancements. Implement as many as time allows, in order of impact.

### 6.1: Drone Selection / Inspection

- Add a `MouseListener` to `TacticalMapPanel`
- On click, use `screenToGrid()` to find which cell was clicked, then check if any drone is at that cell
- If a drone is found, highlight its card in the right sidebar (bright border) and draw a highlight ring around its blip on the map
- Clicking a drone card in the sidebar should do the same (select that drone)
- Clicking empty space deselects

### 6.2: Simulation Speed Control

- Add a speed control to the header bar: buttons labeled "1x", "2x", "4x"
- This requires sending a message to the backend to adjust sleep timers — if the backend doesn't support this, skip or implement as a visual-only speedup of the animation frame rate

### 6.3: Statistics Readout

- Add a small stats section in the header bar or as a collapsible panel:
  - Total fires detected / extinguished
  - Total faults injected
  - Drone water consumption
- Track these counters in `FireDroneGUI` by counting calls to `setZoneFire` and `updateDroneStatus`

### 6.4: Custom Fire Event Injection

- Add a right-click context menu on the tactical map
- Right-clicking a zone opens a small popup: "INJECT FIRE — Severity: [H/M/L]"
- This sends a synthetic `FIRE_EVENT` message to the Scheduler via UDP (similar to fault injection)

---

## Execution Order Summary

| Phase | Focus | Deliverable |
|-------|-------|-------------|
| 1 | FlatLaf + Theme class + dark palette | Compiles, dark window, `Theme.java` exists |
| 2 | TacticalMapPanel (single-canvas grid) | High-res pixel map replaces cell grid, `ZoneCell.java` deleted |
| 3 | Layout restructure + styled panels | Header, sidebars, comms log all military-themed |
| 4 | Tactical map visual polish | Fire flicker, drone shapes, radar sweep, scanlines |
| 5 | Animations and transitions | Sidebar animations, log flashes, smooth experience |
| 6 | Extra features | Drone selection, stats, speed control (stretch) |

Each phase must compile and run before moving to the next. The human reviewer will check each phase.

---

## Important Notes for the Agent

1. **Progressive fire display**: Fires appear on the map ONLY when `setZoneFire(zoneId, true, severity)` is called by the Scheduler. The map starts with all zones dark/safe. Do NOT pre-load fire states.

2. **No text on the grid**: The `TacticalMapPanel` renders ZERO text. No zone labels, no drone IDs, no state names. It is a purely graphical pixel map. All text information goes in the sidebars.

3. **Memory**: The `BufferedImage` at 2000x2000 TYPE_INT_ARGB uses ~16 MB. This is fine. Do NOT create Swing components per pixel/cell. The entire map is ONE `JPanel` with ONE `BufferedImage`.

4. **Thread safety**: `updateDroneStatus`, `setZoneFire`, and `appendEvent` are called from the Scheduler thread. All GUI updates must go through `SwingUtilities.invokeLater()` or be synchronized. The `TacticalMapPanel` data maps should be updated on the EDT.

5. **The Scheduler holds a `FireDroneGUI` reference**. Do not change the `Scheduler` class. The three public methods (`updateDroneStatus`, `setZoneFire`, `appendEvent`) are the only integration points.

6. **Backward compatibility**: `setZoneFire(int, boolean)` (no severity) must still work — it calls the 3-arg version with `null` severity.

7. **Fault injection**: The dialog must still send UDP messages to the Scheduler via `sendFaultInjectionViaUDP()`. The underlying mechanism does not change — only the visual styling of the dialog.

8. **Zone 0 is the base station** at grid coordinates (0,0) to (2,2). Drones start and return here. It should appear visually distinct on the map (e.g., a slightly different shade or a small "base" marker).
