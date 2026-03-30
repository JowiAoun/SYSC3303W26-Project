import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
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

    private TacticalMapPanel tacticalMap;

    //zone definitions
    private final List<ZoneDef> zones = new ArrayList<>();
    private final List<JLabel> droneLabels = new ArrayList<>();
    private final List<JLabel> zoneLabels = new ArrayList<>();
    private final Set<Integer> activeZoneIds = new HashSet<>();
    private final Set<Integer> activeDroneIds = new HashSet<>();               // non-idle drone IDs
    private final Map<Integer, FireEvent.Severity> zoneSeverities = new HashMap<>(); // zoneId → severity
    private final Map<Integer, DroneStatus> droneCurrentStatuses = new HashMap<>();  // droneId → latest status
    private final Set<Integer> faultedDroneIds = new HashSet<>();            // drones currently faulted
    private final Set<Integer> permanentlyFaultedDrones = new HashSet<>();    // hard-faulted, never cleared
    private JTextArea eventLog;
    private JLabel statusLeft;
    private JLabel statusRight;
    private int activeFires = 0;
    private int activeDrones = 0;
    private int faultedDrones = 0;
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

        tacticalMap = new TacticalMapPanel(zones);

        //sidebar with Zones, Drones, Events, Legend
        JPanel sidebar = createSidebar();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tacticalMap, sidebar);
        split.setResizeWeight(0.75);

        add(createToolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(1150, 850));
        setLocationRelativeTo(null);
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



    private JComponent createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(200,200,200)));
        statusLeft = new JLabel("Simulation: Stopped | Active Fires: 0 | Active Drones: 0");
        statusRight = new JLabel("Time: 00:00:00", SwingConstants.RIGHT);
        status.add(statusLeft, BorderLayout.WEST);
        status.add(statusRight, BorderLayout.EAST);
        return status;
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

            // Hard-faulted drones are permanently frozen — ignore any further updates
            if (permanentlyFaultedDrones.contains(droneId)) {
                return;
            }

            int col = status.getCurrentCol();
            int row = status.getCurrentRow();

            // Update tracking maps
            droneCurrentStatuses.put(droneId, status);

            // Track active drones via set
            if (status.getState() == DroneState.IDLE) {
                activeDroneIds.remove(droneId);
            } else {
                activeDroneIds.add(droneId);
            }
            setActiveDrones(activeDroneIds.size());

            // Track faulted drones
            if (status.getState() == DroneState.FAULTED) {
                faultedDroneIds.add(droneId);
                // Hard faults freeze permanently once the drone reaches base (zone 0)
                if (isHardFault(status.getFaultType()) && status.getZoneId() == 0) {
                    permanentlyFaultedDrones.add(droneId);
                }
            } else {
                faultedDroneIds.remove(droneId);
            }
            setFaultedDrones(faultedDroneIds.size());

            tacticalMap.updateDrone(status);

            // Update sidebar label with zone + remaining liters + fault info
            int currentZoneId = status.getZoneId();
            String zoneInfo = currentZoneId > 0 ? " \u2192 Zone " + currentZoneId : "";
            String litersInfo = " (" + status.getRemainingLiters() + "L)";
            String faultInfo = "";
            if (status.getState() == DroneState.FAULTED && status.getFaultType() != FaultType.NONE) {
                faultInfo = " [" + faultLabel(status.getFaultType()) + "]";
            }
            setDroneState(droneId, status.getState().name() + zoneInfo + litersInfo + faultInfo,
                    status.getState() == DroneState.FAULTED);
        });
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
                } else {
                    zoneSeverities.remove(zoneId);
                }
            }
            tacticalMap.setZoneFire(zoneId, active, severity);

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
        setDroneState(droneId, state, false);
    }

    /**
     * update the displayed drone state label with optional fault highlighting.
     */
    public void setDroneState(int droneId, String state, boolean faulted) {
        runOnEdt(() -> {
            int index = droneId - 1;
            if (index >= 0 && index < droneLabels.size()) {
                JLabel lbl = droneLabels.get(index);
                lbl.setText("Drone " + droneId + " - " + state);
                lbl.setForeground(faulted ? new Color(200, 40, 40) : Color.BLACK);
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
     * set the faulted drone count in the status bar.
     */
    public void setFaultedDrones(int count) {
        this.faultedDrones = Math.max(0, count);
        updateStatusBar();
    }

    /**
     * rebuild the status bar text.
     */
    private void updateStatusBar() {
        runOnEdt(() -> {
            if (statusLeft != null) {
                String text = "Simulation: Running | Active Fires: " + activeFires
                        + " | Active Drones: " + activeDrones
                        + " | Faulted: " + faultedDrones;
                statusLeft.setText(text);
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

    /**
     * Returns whether the given fault type is a hard (permanent) fault.
     */
    private boolean isHardFault(FaultType ft) {
        return ft == FaultType.NOZZLE_JAM;
    }



    /**
     * Returns a human-readable label for a fault type.
     */
    private String faultLabel(FaultType ft) {
        switch (ft) {
            case STUCK_MID_FLIGHT:       return "Stuck Mid-Flight";
            case NOZZLE_JAM:             return "Nozzle Jam";
            case ARRIVAL_SENSOR_FAILURE: return "Arrival Sensor Failure";
            case CORRUPTED_MESSAGE:      return "Corrupted Message";
            default:                     return "Unknown Fault";
        }
    }

    /**
     * Creates a toolbar with the fault injection button.
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)));

        JButton injectFaultBtn = new JButton("\u26A0 Inject Fault");
        injectFaultBtn.setToolTipText("Manually inject a fault into a drone");
        injectFaultBtn.addActionListener(e -> showFaultInjectionDialog());
        toolbar.add(injectFaultBtn);

        return toolbar;
    }

    /**
     * Shows a dialog allowing the user to select a drone and fault type to inject.
     */
    private void showFaultInjectionDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Drone selector
        JComboBox<String> droneSelector = new JComboBox<>();
        for (int i = 1; i <= droneCount; i++) {
            droneSelector.addItem("Drone " + i);
        }
        panel.add(new JLabel("Target Drone:"));
        panel.add(droneSelector);

        // Fault type selector (exclude NONE and CORRUPTED_MESSAGE which is packet-level)
        FaultType[] injectableFaults = {
            FaultType.STUCK_MID_FLIGHT,
            FaultType.NOZZLE_JAM,
            FaultType.ARRIVAL_SENSOR_FAILURE
        };
        JComboBox<String> faultSelector = new JComboBox<>();
        for (FaultType ft : injectableFaults) {
            faultSelector.addItem(faultLabel(ft));
        }
        panel.add(new JLabel("Fault Type:"));
        panel.add(faultSelector);

        // Duration spinner (seconds) — disabled for hard faults (permanent)
        SpinnerNumberModel durationModel = new SpinnerNumberModel(10, 5, 60, 5);
        JSpinner durationSpinner = new JSpinner(durationModel);
        faultSelector.addActionListener(e -> {
            int idx = faultSelector.getSelectedIndex();
            boolean isHard = injectableFaults[idx] == FaultType.NOZZLE_JAM;
            durationSpinner.setEnabled(!isHard);
        });
        panel.add(new JLabel("Duration (sec):"));
        panel.add(durationSpinner);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Inject Fault", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int droneIndex = droneSelector.getSelectedIndex() + 1;
            FaultType selectedFault = injectableFaults[faultSelector.getSelectedIndex()];
            long durationMs = ((Number) durationSpinner.getValue()).longValue() * 1000;

            sendFaultInjectionViaUDP(droneIndex, selectedFault, durationMs);
        }
    }

    /**
     * Sends a FAULT_INJECTION message to the Scheduler via UDP so the fault
     * is processed through the normal network pipeline.
     */
    private void sendFaultInjectionViaUDP(int droneId, FaultType faultType, long durationMs) {
        try {
            DatagramSocket tempSocket = new DatagramSocket();
            FireEvent faultPayload = new FireEvent(
                    "00:00:00", droneId,
                    FireEvent.EventType.FIRE_DETECTED,
                    FireEvent.Severity.LOW,
                    faultType, durationMs
            );
            Message msg = Message.faultInjection(faultPayload);
            SwarmNetwork.sendMessage(tempSocket,
                    InetAddress.getByName(SwarmNetwork.LOCALHOST),
                    SwarmNetwork.SCHEDULER_PORT,
                    msg, "[GUI]", "Injected fault", "to Scheduler");
            tempSocket.close();
            String durLabel = isHardFault(faultType) ? "permanent" : (durationMs / 1000) + "s";
            appendEvent("[GUI] Injected " + faultLabel(faultType) + " on Drone " + droneId + " (" + durLabel + ")");
        } catch (Exception ex) {
            appendEvent("[GUI] Failed to inject fault: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { com.formdev.flatlaf.FlatDarkLaf.setup(); } catch (Exception ignored) {}
            FireDroneGUI window = new FireDroneGUI();
            window.setVisible(true);


        });
    }
}