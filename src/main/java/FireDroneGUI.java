import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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

    public FireDroneGUI() {
        super("Fire Drone GUI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Zone Layout for 16x16 Grid
        // Zone 0 (Base): Top-Left Corner
        zones.add(new ZoneDef(0, 0, 0, 2, 2, new Color(220, 220, 220))); 
        
        // Top Strip
        zones.add(new ZoneDef(1, 2, 0, 7, 4, new Color(235, 245, 255)));
        zones.add(new ZoneDef(2, 9, 0, 7, 4, new Color(235, 255, 235)));
        
        // Middle Left Strip (Below Base)
        zones.add(new ZoneDef(3, 0, 2, 2, 6, new Color(255, 245, 235))); 
        
        // Middle Center/Right
        zones.add(new ZoneDef(4, 2, 4, 7, 4, new Color(245, 235, 255)));
        zones.add(new ZoneDef(5, 9, 4, 7, 4, new Color(235, 250, 245)));
        
        // Bottom Half
        zones.add(new ZoneDef(6, 0, 8, 8, 8, new Color(250, 240, 235)));
        zones.add(new ZoneDef(7, 8, 8, 8, 8, new Color(255, 255, 235)));

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
                    boolean isTopLeft = (c == zone.startCol && r == zone.startRow);
                    // top-left cell of the zone gets the zone label, others are blank
                    if (isTopLeft) {
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
        side.add(createCard("Drones (todo)", createSimpleList(
                "Drone 1 - Idle", "Drone 2 - Idle", "Drone 3 - Idle", "Drone 4 - Idle", "Drone 5 - Idle")));
        side.add(Box.createVerticalStrut(8));
        side.add(createCard("Event Log (todo)", createEventPreview()));
        side.add(Box.createVerticalStrut(8));
        side.add(createCard("Legend", createLegendPanel()));

        return side;
    }

    private JComponent createZonesList() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (ZoneDef z : zones) {
            String desc = String.format("Zone %d — (%d,%d) size %dx%d", z.id, z.startCol, z.startRow, z.widthCols, z.heightRows);
            JLabel lbl = new JLabel(desc);
            lbl.setBorder(new EmptyBorder(4,4,4,4));
            p.add(lbl);
        }
        return p;
    }

    //Simple titled container used by the sidebar
    private JPanel createCard(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createTitledBorder(title));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JComponent createSimpleList(String... items) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (String s : items) {
            JLabel lbl = new JLabel(s);
            lbl.setBorder(new EmptyBorder(4,4,4,4));
            p.add(lbl);
        }
        return p;
    }

    private JComponent createEventPreview() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        ta.setRows(8);
        ta.setText("Logs here");
        return new JScrollPane(ta);
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
        status.add(new JLabel("Simulation: Stopped | Events: 0 | Active Drones: 0"), BorderLayout.WEST);
        status.add(new JLabel("Time: 00:00:00", SwingConstants.RIGHT), BorderLayout.EAST);
        return status;
    }

    //update a specific cell's state
    public void setCellState(int col, int row, CellState state) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            gridCells[row][col].setState(state);
        }
    }

    //set a text on a cell
    public void setCellText(int col, int row, String text) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            gridCells[row][col].setText(text);
        }
    }

    private ZoneDef getZoneById(int id) {
        for (ZoneDef z : zones) {
            if (z.id == id) return z;
        }
        return null; 
    }

    /**
     * Updates the GUI based on the drone's status.
     * Thread-safe.
     */
    public void updateDroneStatus(DroneStatus status) {
        SwingUtilities.invokeLater(() -> {
            // Update Sidebar (TODO: Make a dedicated DroneList component)
            // For now, simpler update of the status bar or a label
            // (There is no easy public access to sidebar labels yet without refactoring)
            
            // Map DroneState to CellState for the target Zone
            ZoneDef zone = getZoneById(status.getZoneId());
            if (zone != null) {
                // Determine cell state based on drone state
                CellState cellState = CellState.EMPTY;
                String text = "";
                
                switch (status.getState()) {
                    case EN_ROUTE:
                        cellState = CellState.DRONE_OUTBOUND;
                        text = ">>>";
                        break;
                    case EXTINGUISHING:
                        cellState = CellState.DRONE_EXTINGUISHED;
                        text = "FIGHT";
                        break;
                    case RETURNING:
                        cellState = CellState.DRONE_RETURNING;
                        text = "<<<";
                        break;
                    case REFILLING:
                        cellState = CellState.DRONE_RETURNING; // Use same color or new state? Let's treat REFILLING as visible.
                        text = "FILL";
                        break;
                    case IDLE:
                        // Show drone at base if IDLE there
                        if (status.getZoneId() == 0) {
                            cellState = CellState.DRONE_RETURNING; // Or a specific IDLE color?
                            // Let's reuse DRONE_RETURNING color (purple) for now or add a new one.
                            // Actually, IDLE usually means "Ready". 
                            text = "IDLE";
                        }
                        break;
                }

                // Update the cell state
                if (cellState != CellState.EMPTY) {
                   setCellState(zone.startCol, zone.startRow, cellState);
                   setCellText(zone.startCol, zone.startRow, text);
                }

            }
            
            // Also update a global status label if we had one accessibly.
        });
    }

    /**
     * Updates a zone's fire state (e.g. when a new fire is detected).
     */
    public void setZoneFire(int zoneId, boolean active) {
        SwingUtilities.invokeLater(() -> {
            ZoneDef zone = getZoneById(zoneId);
            if (zone != null) {
                setCellState(zone.startCol, zone.startRow, active ? CellState.ACTIVE_FIRE : CellState.EXTINGUISHED);
                setCellText(zone.startCol, zone.startRow, active ? "FIRE" : "SAFE");
            }
        });
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