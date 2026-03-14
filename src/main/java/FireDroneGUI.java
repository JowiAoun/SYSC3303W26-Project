import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FireDroneGUI.java
 *
 * Main window for the firefighting drone simulation.
 * Displays a grid of zones and a sidebar with simulation status.
 */
public class FireDroneGUI extends JFrame {
    private static final int COLS = 16;
    private static final int ROWS = 16;

    // grid reference so caller can change cell states
    private ZoneCell[][] gridCells = new ZoneCell[ROWS][COLS];

    // public state enum to use externally
    public enum CellState {
        EMPTY,
        ACTIVE_FIRE,
        EXTINGUISHED,
        DRONE_OUTBOUND,
        DRONE_EXTINGUISHED,
        DRONE_RETURNING
    }

    //zone definitions
    private final List<ZoneDef> zones = new ArrayList<>();
    private final List<JLabel> droneLabels = new ArrayList<>();
    private final List<JLabel> zoneLabels = new ArrayList<>();
    private final Set<Integer> activeZoneIds = new HashSet<>();
    private final Map<Integer, int[]> droneLastCell = new HashMap<>();         // droneId → {col, row}
    private final Set<Integer> activeDroneIds = new HashSet<>();               // non-idle drone IDs
    private final Map<Integer, FireEvent.Severity> zoneSeverities = new HashMap<>(); // zoneId → severity
    private final Map<Integer, DroneStatus> droneCurrentStatuses = new HashMap<>();  // droneId → latest status
    private JTextArea eventLog;
    private JLabel statusLeft;
    private JLabel statusRight;
    private int activeFires = 0;
    private int activeDrones = 0;
    private int droneCount;

    public FireDroneGUI() {
        this(1);
    }

    public FireDroneGUI(int droneCount) {
        super("Fire Drone GUI");
        this.droneCount = droneCount;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        zones.addAll(ZoneLoader.loadZones("./src/main/resources/data/zones.csv", COLS, ROWS));

        //grid panel showing the zones
        JPanel gridPanel = createGridPanel(COLS, ROWS);
        JScrollPane gridScroll = new JScrollPane(gridPanel);
        gridScroll.getViewport().setPreferredSize(new Dimension(800, 800));

        //sidebar with Zones, Drones, Events, Legend
        JPanel sidebar = createSidebar();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gridScroll, sidebar);
        split.setResizeWeight(0.75);

        add(split, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(1150, 850));
        setLocationRelativeTo(null);
    }

    // Create a grid of ZoneCell objects and store references in gridCells[][]
    private JPanel createGridPanel(int cols, int rows) {
        JPanel panel = new JPanel(new GridLayout(rows, cols));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                ZoneDef zone = findZoneForCell(c, r);
                ZoneCell cell;
                if (zone != null) {
                    boolean isBottomRight = (c == zone.startCol + zone.widthCols - 1
                                          && r == zone.startRow + zone.heightRows - 1);
                    // bottom-right cell of the zone gets the zone label, others are blank
                    if (isBottomRight) {
                        cell = new ZoneCell(c, r, zone, String.format("Z%d", zone.id));
                    } else {
                        cell = new ZoneCell(c, r, zone, "");
                    }
                } else {
                    // fallback (shouldn't happen if zones tile the grid)
                    cell = new ZoneCell(c, r, null, "");
                }
                // Update border after creating the cell
                cell.updateBorder(COLS, ROWS, zones);
                gridCells[r][c] = cell;
                panel.add(cell);
            }
        }
        return panel;
    }

    private ZoneDef findZoneForCell(int col, int row) {
        for (ZoneDef z : zones) {
            if (col >= z.startCol && col < z.startCol + z.widthCols &&
                    row >= z.startRow && row < z.startRow + z.heightRows) {
                return z;
            }
        }
        return null;
    }

    //TODO: create method/class for the cards
    private JPanel createSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(new EmptyBorder(8, 8, 8, 8));
        side.setPreferredSize(new Dimension(300, 700));

        side.add(createCard("Zones", createZonesList()));
        side.add(Box.createVerticalStrut(8));
        side.add(createCard("Drones", createDroneList(droneCount)));
        side.add(Box.createVerticalStrut(8));
        side.add(createCard("Event Log", createEventPreview()));
        side.add(Box.createVerticalStrut(8));
        side.add(createCard("Legend", createLegendPanel()));

        return side;
    }

    private JComponent createZonesList() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        zoneLabels.clear();
        for (ZoneDef z : zones) {
            String desc = String.format("Zone %d — (%d,%d) %dx%d", z.id, z.startCol, z.startRow, z.widthCols, z.heightRows);
            JLabel lbl = new JLabel(desc);
            lbl.setBorder(new EmptyBorder(4,4,4,4));
            zoneLabels.add(lbl);
            p.add(lbl);
        }
        return p;
    }

    /**
     * Update a zone's sidebar label to reflect current fire status and severity.
     */
    private void updateZoneLabel(int zoneId, boolean active, FireEvent.Severity severity) {
        // zoneLabels are ordered by zones list index, find the matching one
        for (int i = 0; i < zones.size(); i++) {
            ZoneDef z = zones.get(i);
            if (z.id == zoneId && i < zoneLabels.size()) {
                String base = String.format("Zone %d — (%d,%d) %dx%d", z.id, z.startCol, z.startRow, z.widthCols, z.heightRows);
                if (active) {
                    String sevStr = severityShortLabel(severity);
                    base += " [FIRE" + (sevStr.isEmpty() ? "" : " " + sevStr) + "]";
                }
                zoneLabels.get(i).setText(base);
                break;
            }
        }
    }

    //Simple titled container used by the sidebar
    private JPanel createCard(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createTitledBorder(title));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JComponent createEventPreview() {
        eventLog = new JTextArea();
        eventLog.setEditable(false);
        eventLog.setRows(8);
        eventLog.setLineWrap(true);
        eventLog.setWrapStyleWord(true);
        eventLog.setText("Logs here");
        return new JScrollPane(eventLog);
    }

    // Legend uses small ZoneCell examples
    private JComponent createLegendPanel() {
        JPanel p = new JPanel(new GridLayout(0, 1, 4, 4));
        p.setBorder(new EmptyBorder(4,4,4,4));

        p.add(createLegendRow("Zone Label", CellState.EMPTY, "Zn"));
        p.add(createLegendRow("Active Fire", CellState.ACTIVE_FIRE, "F"));
        p.add(createLegendRow("Extinguished Fire", CellState.EXTINGUISHED, "X"));
        p.add(createLegendRow("Drone Outbound", CellState.DRONE_OUTBOUND, "D(n)"));
        p.add(createLegendRow("Drone Extinguished Fire", CellState.DRONE_EXTINGUISHED, "D(n)"));
        p.add(createLegendRow("Drone Returning", CellState.DRONE_RETURNING, "D(n)"));

        return p;
    }

    // small helper to build legend lines
    private JPanel createLegendRow(String text, CellState state, String exampleText) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        //create a tiny sample cell, this wont be part of main grid
        ZoneCell sample = new ZoneCell(0, 0, null, exampleText);
        sample.setPreferredSize(new Dimension(60, 24));
        sample.setState(state);
        sample.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel lbl = new JLabel(text);
        lbl.setBorder(new EmptyBorder(2,2,2,2));

        row.add(sample, BorderLayout.WEST);
        row.add(lbl, BorderLayout.CENTER);

        return row;
    }

    private JComponent createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(200,200,200)));
        statusLeft = new JLabel("Simulation: Stopped | Active Fires: 0 | Active Drones: 0");
        statusRight = new JLabel("Time: 00:00:00", SwingConstants.RIGHT);
        status.add(statusLeft, BorderLayout.WEST);
        status.add(statusRight, BorderLayout.EAST);
        return status;
    }

    //update a specific cell's state
    public void setCellState(int col, int row, CellState state) {
        runOnEdt(() -> {
            if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
                gridCells[row][col].setState(state);
            }
        });
    }

    //set a text on a cell
    public void setCellText(int col, int row, String text) {
        runOnEdt(() -> {
            if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
                gridCells[row][col].setText(text);
            }
        });
    }

    private ZoneDef getZoneById(int id) {
        for (ZoneDef z : zones) {
            if (z.id == id) return z;
        }
        return null; 
    }

    /**
     * Updates the GUI based on the drone's status.
     * Tracks drone position by exact (col, row) cell, recomputes old and new cells
     * so that multiple drones sharing a cell are all visible.
     * Thread-safe.
     */
    public void updateDroneStatus(DroneStatus status) {
        runOnEdt(() -> {
            int droneId = status.getDroneId();
            int col = status.getCurrentCol();
            int row = status.getCurrentRow();

            // Save the old cell before updating
            int[] lastCell = droneLastCell.get(droneId);

            // Update tracking maps
            droneCurrentStatuses.put(droneId, status);
            droneLastCell.put(droneId, new int[]{col, row});

            // Track active drones via set
            if (status.getState() == DroneState.IDLE) {
                activeDroneIds.remove(droneId);
            } else {
                activeDroneIds.add(droneId);
            }
            setActiveDrones(activeDroneIds.size());

            // Recompute the old cell (drone left it)
            if (lastCell != null && (lastCell[0] != col || lastCell[1] != row)) {
                recomputeCell(lastCell[0], lastCell[1]);
            }

            // Recompute the new cell (drone arrived or updated state)
            recomputeCell(col, row);

            // Update sidebar label with zone + remaining liters
            int currentZoneId = status.getZoneId();
            String zoneInfo = currentZoneId > 0 ? " \u2192 Zone " + currentZoneId : "";
            String litersInfo = " (" + status.getRemainingLiters() + "L)";
            setDroneState(droneId, status.getState().name() + zoneInfo + litersInfo);
        });
    }

    /**
     * Checks whether a drone should be rendered on the grid.
     * IDLE drones at non-base zones are hidden.
     */
    private boolean shouldRenderDrone(DroneStatus ds) {
        if (ds.getState() == DroneState.IDLE) {
            // Only show IDLE drones at base zone (zone 0)
            return ds.getZoneId() == 0;
        }
        return true;
    }

    /**
     * Recomputes a grid cell's text and state based on all drones currently at that (col, row).
     * If no drones are present, reverts the cell to its default zone appearance.
     */
    private void recomputeCell(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return;

        // Find all visible drones at this exact cell
        List<DroneStatus> dronesHere = new ArrayList<>();
        for (Map.Entry<Integer, int[]> entry : droneLastCell.entrySet()) {
            int[] cell = entry.getValue();
            if (cell[0] == col && cell[1] == row) {
                DroneStatus ds = droneCurrentStatuses.get(entry.getKey());
                if (ds != null && shouldRenderDrone(ds)) {
                    dronesHere.add(ds);
                }
            }
        }

        if (dronesHere.isEmpty()) {
            // No drones to display — revert to default cell appearance
            restoreCellDefault(col, row);
            return;
        }

        // Determine the highest-priority cell state and build text for all drones
        CellState bestState = CellState.EMPTY;
        StringBuilder htmlBuilder = new StringBuilder("<html><center>");
        for (int i = 0; i < dronesHere.size(); i++) {
            DroneStatus ds = dronesHere.get(i);
            String label = droneShortLabel(ds);
            CellState cs = droneCellState(ds);

            if (statePriority(cs) > statePriority(bestState)) {
                bestState = cs;
            }

            if (i > 0) htmlBuilder.append("<br>");
            htmlBuilder.append(label);
        }
        htmlBuilder.append("</center></html>");

        gridCells[row][col].setState(bestState);
        gridCells[row][col].setText(htmlBuilder.toString());
    }

    /**
     * Restores a cell to its default appearance: zone label, fire, or empty.
     */
    private void restoreCellDefault(int col, int row) {
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) return;

        ZoneDef zone = findZoneForCell(col, row);

        // Check if this cell's zone has an active fire
        if (zone != null && activeZoneIds.contains(zone.id)) {
            // Only show fire state on the zone's start cell
            if (col == zone.startCol && row == zone.startRow) {
                FireEvent.Severity sev = zoneSeverities.get(zone.id);
                gridCells[row][col].setState(CellState.ACTIVE_FIRE);
                gridCells[row][col].setText("FIRE" + severityLabel(sev));
                return;
            }
        }

        // Restore zone label on the bottom-right cell of the zone
        if (zone != null) {
            boolean isBottomRight = (col == zone.startCol + zone.widthCols - 1
                    && row == zone.startRow + zone.heightRows - 1);
            gridCells[row][col].setState(CellState.EMPTY);
            gridCells[row][col].setText(isBottomRight ? String.format("Z%d", zone.id) : "");
        } else {
            gridCells[row][col].setState(CellState.EMPTY);
            gridCells[row][col].setText("");
        }
    }

    /**
     * Returns a short display label for a drone status, e.g. "D1 >>>".
     */
    private String droneShortLabel(DroneStatus ds) {
        String prefix = "D" + ds.getDroneId();
        switch (ds.getState()) {
            case EN_ROUTE:       return prefix + " &gt;&gt;&gt;";
            case EXTINGUISHING:  return prefix + " FIGHT";
            case RETURNING:      return prefix + " &lt;&lt;&lt;";
            case REFILLING:      return prefix + " FILL";
            case IDLE:           return prefix + " IDLE";
            default:             return prefix;
        }
    }

    /**
     * Maps a DroneState to the corresponding CellState.
     */
    private CellState droneCellState(DroneStatus ds) {
        switch (ds.getState()) {
            case EN_ROUTE:      return CellState.DRONE_OUTBOUND;
            case EXTINGUISHING: return CellState.DRONE_EXTINGUISHED;
            case RETURNING:
            case REFILLING:
            case IDLE:          return CellState.DRONE_RETURNING;
            default:            return CellState.EMPTY;
        }
    }

    /**
     * Returns a priority value for cell states so the most important state wins
     * when multiple drones share a cell.
     */
    private int statePriority(CellState state) {
        switch (state) {
            case DRONE_EXTINGUISHED: return 3;
            case DRONE_OUTBOUND:     return 2;
            case DRONE_RETURNING:    return 1;
            default:                 return 0;
        }
    }

    /**
     * Update a zone's fire state and active count (backward-compatible, no severity).
     */
    public void setZoneFire(int zoneId, boolean active) {
        setZoneFire(zoneId, active, null);
    }

    /**
     * Update a zone's fire state, severity display, and active count.
     */
    public void setZoneFire(int zoneId, boolean active, FireEvent.Severity severity) {
        runOnEdt(() -> {
            ZoneDef zone = getZoneById(zoneId);
            if (zone != null) {
                if (active) {
                    zoneSeverities.put(zoneId, severity);
                    setCellState(zone.startCol, zone.startRow, CellState.ACTIVE_FIRE);
                    setCellText(zone.startCol, zone.startRow, "FIRE" + severityLabel(severity));
                } else {
                    zoneSeverities.remove(zoneId);
                    setCellState(zone.startCol, zone.startRow, CellState.EXTINGUISHED);
                    setCellText(zone.startCol, zone.startRow, "SAFE");
                }
            }

            if (active) {
                if (activeZoneIds.add(zoneId)) {
                    setActiveFires(activeZoneIds.size());
                }
            } else {
                if (activeZoneIds.remove(zoneId)) {
                    setActiveFires(activeZoneIds.size());
                }
            }

            updateZoneLabel(zoneId, active, severity);
        });
    }

    /**
     * Returns a severity suffix for cell text, e.g. " (H)", " (M)", " (L)".
     */
    private String severityLabel(FireEvent.Severity severity) {
        if (severity == null) return "";
        switch (severity) {
            case HIGH:     return " (H)";
            case MODERATE: return " (M)";
            case LOW:      return " (L)";
            default:       return "";
        }
    }

    /**
     * Returns a short severity label for sidebar, e.g. "H", "M", "L".
     */
    private String severityShortLabel(FireEvent.Severity severity) {
        if (severity == null) return "";
        switch (severity) {
            case HIGH:     return "H";
            case MODERATE: return "M";
            case LOW:      return "L";
            default:       return "";
        }
    }

    /**
     * append a line to the event log.
     */
    public void appendEvent(String message) {
        runOnEdt(() -> {
            if (eventLog == null) {
                return;
            }
            eventLog.append(message + "\n");
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
    }

    /**
     * update the displayed drone state label.
     */
    public void setDroneState(int droneId, String state) {
        runOnEdt(() -> {
            int index = droneId - 1;
            if (index >= 0 && index < droneLabels.size()) {
                droneLabels.get(index).setText("Drone " + droneId + " - " + state);
            }
        });
    }

    /**
     * set the active fire count in the status bar.
     */
    public void setActiveFires(int count) {
        this.activeFires = Math.max(0, count);
        updateStatusBar();
    }

    /**
     * set the active drone count in the status bar.
     */
    public void setActiveDrones(int count) {
        this.activeDrones = Math.max(0, count);
        updateStatusBar();
    }

    /**
     * rebuild the status bar text.
     */
    private void updateStatusBar() {
        runOnEdt(() -> {
            if (statusLeft != null) {
                statusLeft.setText("Simulation: Running | Active Fires: " + activeFires + " | Active Drones: " + activeDrones);
            }
        });
    }

    /**
     * create the drone list panel.
     */
    private JComponent createDroneList(int count) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        droneLabels.clear();
        for (int i = 1; i <= count; i++) {
            JLabel lbl = new JLabel("Drone " + i + " - Idle");
            lbl.setBorder(new EmptyBorder(4,4,4,4));
            droneLabels.add(lbl);
            p.add(lbl);
        }
        return p;
    }

    /**
     * run a task on the swing event thread.
     */
    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            FireDroneGUI window = new FireDroneGUI();
            window.setVisible(true);

            //small demo with some example states
            window.setCellState(2, 2, CellState.ACTIVE_FIRE);
            window.setCellState(13, 1, CellState.DRONE_OUTBOUND);
            window.setCellState(0, 8, CellState.DRONE_RETURNING);
        });
    }
}