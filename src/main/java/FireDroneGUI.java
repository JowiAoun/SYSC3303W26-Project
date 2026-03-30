import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class FireDroneGUI extends JFrame {
    private static final int COLS = 16;
    private static final int ROWS = 16;

    private TacticalMapPanel tacticalMap;

    private final List<ZoneDef> zones = new ArrayList<>();
    private final List<DroneCard> droneCards = new ArrayList<>();
    private final List<ZoneCard> zoneCards = new ArrayList<>();
    private final Set<Integer> activeZoneIds = new HashSet<>();
    private final Set<Integer> activeDroneIds = new HashSet<>();
    private final Set<Integer> faultedDroneIds = new HashSet<>();
    private final Set<Integer> permanentlyFaultedDrones = new HashSet<>();
    
    // UI components
    private JTextPane eventLog;
    private JLabel firesCounter;
    private JLabel dronesCounter;
    private JLabel faultsCounter;
    private JLabel clockLabel;
    
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

        JPanel leftSidebar = createZonePanel();
        JPanel rightSidebar = createDronePanel(droneCount);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(leftSidebar, BorderLayout.WEST);
        centerPanel.add(tacticalMap, BorderLayout.CENTER);
        centerPanel.add(rightSidebar, BorderLayout.EAST);

        add(createHeaderBar(), BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(createCommsLog(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(1200, 850));
        setLocationRelativeTo(null);
    }

    // --- PHASE 3 UI COMPONENTS ---

    private JPanel createHeaderBar() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.BG_PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID_LINE_ZONE),
                new EmptyBorder(8, 16, 8, 16)
        ));
        header.setPreferredSize(new Dimension(0, 60));

        JLabel title = new JLabel("TACTICAL DRONE COMMAND");
        title.setFont(Theme.FONT_HEADER);
        title.setForeground(Theme.TEXT_PRIMARY);

        JPanel counters = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        counters.setOpaque(false);
        firesCounter = createCounterLabel("FIRES: 0");
        dronesCounter = createCounterLabel("DRONES: 0");
        faultsCounter = createCounterLabel("FAULTS: 0");
        counters.add(firesCounter);
        counters.add(dronesCounter);
        counters.add(faultsCounter);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setOpaque(false);

        JButton injectBtn = new JButton("INJECT FAULT");
        injectBtn.setBackground(Theme.BG_CARD);
        injectBtn.setForeground(Theme.TEXT_PRIMARY);
        injectBtn.setFont(Theme.FONT_MONO_BOLD);
        injectBtn.setFocusPainted(false);
        injectBtn.addActionListener(e -> showFaultInjectionDialog());

        clockLabel = new JLabel();
        clockLabel.setForeground(Theme.TEXT_BRIGHT);
        clockLabel.setFont(Theme.FONT_MONO_BOLD);
        updateClock();
        new javax.swing.Timer(1000, e -> updateClock()).start();

        rightPanel.add(injectBtn);
        rightPanel.add(clockLabel);

        header.add(title, BorderLayout.WEST);
        header.add(counters, BorderLayout.CENTER);
        header.add(rightPanel, BorderLayout.EAST);

        return header;
    }

    private JLabel createCounterLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Theme.FONT_MONO_BOLD);
        lbl.setForeground(Theme.TEXT_BRIGHT);
        return lbl;
    }

    private void updateClock() {
        clockLabel.setText(new SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
    }

    private JPanel createZonePanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(Theme.BG_PANEL);
        container.setPreferredSize(new Dimension(250, 0));

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Theme.BG_PANEL);
        listPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        zoneCards.clear();
        for (ZoneDef z : zones) {
            ZoneCard card = new ZoneCard(z);
            zoneCards.add(card);
            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(8));
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        container.add(scroll, BorderLayout.CENTER);
        return container;
    }

    private JPanel createDronePanel(int count) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(Theme.BG_PANEL);
        container.setPreferredSize(new Dimension(300, 0));

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Theme.BG_PANEL);
        listPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        droneCards.clear();
        for (int i = 1; i <= count; i++) {
            DroneCard card = new DroneCard(i);
            droneCards.add(card);
            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(8));
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        container.add(scroll, BorderLayout.CENTER);
        return container;
    }

    private JComponent createCommsLog() {
        JPanel container = new JPanel(new BorderLayout());
        container.setPreferredSize(new Dimension(0, 150));
        
        eventLog = new JTextPane();
        eventLog.setEditable(false);
        eventLog.setBackground(Theme.BG_MAIN);
        eventLog.setFont(Theme.FONT_MONO);
        
        JScrollPane scroll = new JScrollPane(eventLog);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID_LINE_ZONE));
        container.add(scroll, BorderLayout.CENTER);
        return container;
    }

    // --- CARDS ---

    private class ZoneCard extends JPanel {
        private final int zoneId;
        private final JLabel statusLabel;

        public ZoneCard(ZoneDef zone) {
            this.zoneId = zone.id;
            setLayout(new BorderLayout());
            setBackground(Theme.BG_CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_DEFAULT, 1),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JLabel idLbl = new JLabel("ZONE " + zone.id);
            idLbl.setFont(Theme.FONT_MONO_BOLD);
            idLbl.setForeground(Theme.TEXT_PRIMARY);
            
            JLabel coordsLbl = new JLabel(String.format("(%d,%d)", zone.startCol, zone.startRow));
            coordsLbl.setFont(Theme.FONT_MONO_SMALL);
            coordsLbl.setForeground(Theme.TEXT_SECONDARY);
            
            top.add(idLbl, BorderLayout.WEST);
            top.add(coordsLbl, BorderLayout.EAST);

            statusLabel = new JLabel("CLEAR");
            statusLabel.setFont(Theme.FONT_MONO_BOLD);
            statusLabel.setForeground(Theme.TEXT_SECONDARY);

            add(top, BorderLayout.NORTH);
            add(statusLabel, BorderLayout.SOUTH);
        }

        public void update(boolean active, FireEvent.Severity severity) {
            if (active) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.FIRE_ACTIVE, 1),
                        new EmptyBorder(8, 8, 8, 8)
                ));
                statusLabel.setText("FIRE " + severityShortLabel(severity));
                if (severity == FireEvent.Severity.HIGH) statusLabel.setForeground(Theme.FIRE_HIGH);
                else if (severity == FireEvent.Severity.MODERATE) statusLabel.setForeground(Theme.FIRE_MODERATE);
                else statusLabel.setForeground(Theme.FIRE_LOW);
            } else {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER_DEFAULT, 1),
                        new EmptyBorder(8, 8, 8, 8)
                ));
                statusLabel.setText("CLEAR");
                statusLabel.setForeground(Theme.TEXT_SECONDARY);
            }
        }
    }

    private class WaterGauge extends JPanel {
        private int capacity = 15;
        private int current = 15;

        public WaterGauge() {
            setPreferredSize(new Dimension(0, 10));
            setBackground(Theme.BG_MAIN);
            setBorder(BorderFactory.createLineBorder(Theme.BORDER_DEFAULT));
        }

        public void update(int liters) {
            this.current = liters;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            float ratio = (float) current / capacity;
            int width = (int) (getWidth() * ratio);
            
            Color fill = Theme.TEXT_BRIGHT;
            if (ratio <= 0.1f) fill = Theme.FIRE_HIGH;
            else if (ratio <= 0.3f) fill = Theme.FIRE_LOW;
            
            g.setColor(fill);
            g.fillRect(0, 0, width, getHeight());
        }
    }

    private class DroneCard extends JPanel {
        private final JLabel idLabel;
        private final JLabel stateLabel;
        private final JLabel targetLabel;
        private final JLabel faultLabel;
        private final WaterGauge gauge;

        public DroneCard(int droneId) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Theme.BG_CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_DEFAULT, 1),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            
            idLabel = new JLabel("DRONE " + droneId);
            idLabel.setFont(Theme.FONT_MONO_BOLD);
            idLabel.setForeground(Theme.TEXT_PRIMARY);
            
            stateLabel = new JLabel("IDLE");
            stateLabel.setFont(Theme.FONT_MONO_BOLD);
            stateLabel.setForeground(Theme.DRONE_IDLE);
            
            header.add(idLabel, BorderLayout.WEST);
            header.add(stateLabel, BorderLayout.EAST);
            
            JPanel infoPanel = new JPanel(new BorderLayout());
            infoPanel.setOpaque(false);
            
            targetLabel = new JLabel("-> ZONE 0");
            targetLabel.setFont(Theme.FONT_MONO);
            targetLabel.setForeground(Theme.TEXT_SECONDARY);
            
            faultLabel = new JLabel("");
            faultLabel.setFont(Theme.FONT_MONO_BOLD);
            
            infoPanel.add(targetLabel, BorderLayout.WEST);
            infoPanel.add(faultLabel, BorderLayout.EAST);
            
            gauge = new WaterGauge();
            
            add(header);
            add(Box.createVerticalStrut(4));
            add(infoPanel);
            add(Box.createVerticalStrut(6));
            add(gauge);
        }

        public void update(DroneStatus ds, boolean isHardFaulted) {
            if (isHardFaulted) {
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.FAULT_HARD, 1),
                    new EmptyBorder(8, 8, 8, 8)
                ));
                stateLabel.setText("OFFLINE");
            } else {
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_DEFAULT, 1),
                    new EmptyBorder(8, 8, 8, 8)
                ));
                stateLabel.setText(ds.getState().name());
            }

            Color stateColor = Theme.DRONE_IDLE;
            switch(ds.getState()) {
                case EN_ROUTE: stateColor = Theme.DRONE_OUTBOUND; break;
                case EXTINGUISHING: stateColor = Theme.DRONE_FIGHTING; break;
                case RETURNING: stateColor = Theme.DRONE_RETURNING; break;
                case REFILLING: stateColor = Theme.DRONE_REFILLING; break;
                case FAULTED: 
                    stateColor = isHardFault(ds.getFaultType()) ? Theme.FAULT_HARD : Theme.FAULT_SOFT;
                    break;
            }
            if (isHardFaulted) stateColor = Theme.FAULT_HARD;
            stateLabel.setForeground(stateColor);
            
            targetLabel.setText("-> ZONE " + ds.getZoneId());
            
            if (ds.getState() == DroneState.FAULTED && ds.getFaultType() != FaultType.NONE) {
                faultLabel.setText(faultShortLabel(ds.getFaultType()));
                faultLabel.setForeground(isHardFault(ds.getFaultType()) ? Theme.FAULT_HARD : Theme.FAULT_SOFT);
            } else {
                faultLabel.setText("");
            }
            
            gauge.update(ds.getRemainingLiters());
        }
    }


    // --- API & LOGIC ---

    private ZoneDef getZoneById(int id) {
        for (ZoneDef z : zones) {
            if (z.id == id) return z;
        }
        return null; 
    }

    public void updateDroneStatus(DroneStatus status) {
        runOnEdt(() -> {
            int droneId = status.getDroneId();

            if (permanentlyFaultedDrones.contains(droneId)) {
                return;
            }

            // Track active drones
            if (status.getState() == DroneState.IDLE) {
                activeDroneIds.remove(droneId);
            } else {
                activeDroneIds.add(droneId);
            }
            setActiveDrones(activeDroneIds.size());

            // Track faulted drones
            if (status.getState() == DroneState.FAULTED) {
                faultedDroneIds.add(droneId);
                if (isHardFault(status.getFaultType()) && status.getZoneId() == 0) {
                    permanentlyFaultedDrones.add(droneId);
                }
            } else {
                faultedDroneIds.remove(droneId);
            }
            setFaultedDrones(faultedDroneIds.size());

            tacticalMap.updateDrone(status);

            // Update DroneCard
            if (droneId - 1 >= 0 && droneId - 1 < droneCards.size()) {
                boolean hard = permanentlyFaultedDrones.contains(droneId) || 
                               (status.getState() == DroneState.FAULTED && isHardFault(status.getFaultType()));
                droneCards.get(droneId - 1).update(status, hard);
            }
        });
    }

    public void setZoneFire(int zoneId, boolean active) {
        setZoneFire(zoneId, active, null);
    }

    public void setZoneFire(int zoneId, boolean active, FireEvent.Severity severity) {
        runOnEdt(() -> {
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

            // Update ZoneCard
            for (int i = 0; i < zoneCards.size(); i++) {
                if (zoneCards.get(i).zoneId == zoneId) {
                    zoneCards.get(i).update(active, severity);
                    break;
                }
            }
        });
    }

    public void appendEvent(String message) {
        runOnEdt(() -> {
            if (eventLog == null) return;
            
            Color c = Theme.TEXT_SECONDARY;
            String lower = message.toLowerCase();
            if (lower.contains("fire")) c = Theme.FIRE_MODERATE;
            else if (lower.contains("fault")) c = Theme.FAULT_SOFT;
            else if (lower.contains("extinguished") || lower.contains("safe")) c = Theme.TEXT_BRIGHT;
            
            StyleContext sc = StyleContext.getDefaultStyleContext();
            AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, c);
            
            try {
                int len = eventLog.getDocument().getLength();
                eventLog.getDocument().insertString(len, message + "\n", aset);
                eventLog.setCaretPosition(eventLog.getDocument().getLength());
            } catch (Exception e) {}
        });
    }

    public void setActiveFires(int count) {
        this.activeFires = Math.max(0, count);
        if (firesCounter != null) firesCounter.setText("FIRES: " + activeFires);
    }

    public void setActiveDrones(int count) {
        this.activeDrones = Math.max(0, count);
        if (dronesCounter != null) dronesCounter.setText("DRONES: " + activeDrones);
    }

    public void setFaultedDrones(int count) {
        this.faultedDrones = Math.max(0, count);
        if (faultsCounter != null) faultsCounter.setText("FAULTS: " + faultedDrones);
    }

    private void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private boolean isHardFault(FaultType ft) {
        return ft == FaultType.NOZZLE_JAM;
    }

    private String faultLabel(FaultType ft) {
        switch (ft) {
            case STUCK_MID_FLIGHT:       return "Stuck Mid-Flight";
            case NOZZLE_JAM:             return "Nozzle Jam";
            case ARRIVAL_SENSOR_FAILURE: return "Arrival Sensor Failure";
            case CORRUPTED_MESSAGE:      return "Corrupted Message";
            default:                     return "Unknown Fault";
        }
    }

    private String faultShortLabel(FaultType ft) {
        switch (ft) {
            case STUCK_MID_FLIGHT:       return "STUCK";
            case NOZZLE_JAM:             return "NOZZLE";
            case ARRIVAL_SENSOR_FAILURE: return "SENSOR";
            case CORRUPTED_MESSAGE:      return "CORRUPT";
            default:                     return "FAULT";
        }
    }

    private String severityShortLabel(FireEvent.Severity severity) {
        if (severity == null) return "";
        switch (severity) {
            case HIGH:     return "H";
            case MODERATE: return "M";
            case LOW:      return "L";
            default:       return "";
        }
    }

    private void showFaultInjectionDialog() {
        JDialog dialog = new JDialog(this, "INJECT FAULT", true);
        dialog.getContentPane().setBackground(Theme.BG_PANEL);
        
        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        panel.setOpaque(false);

        JLabel l1 = new JLabel("TARGET DRONE:");
        l1.setFont(Theme.FONT_MONO);
        l1.setForeground(Theme.TEXT_SECONDARY);
        
        JComboBox<String> droneSelector = new JComboBox<>();
        droneSelector.setBackground(Theme.BG_CARD);
        droneSelector.setForeground(Theme.TEXT_BRIGHT);
        droneSelector.setFont(Theme.FONT_MONO);
        for (int i = 1; i <= droneCount; i++) {
            droneSelector.addItem("Drone " + i);
        }
        
        panel.add(l1);
        panel.add(droneSelector);

        JLabel l2 = new JLabel("FAULT TYPE:");
        l2.setFont(Theme.FONT_MONO);
        l2.setForeground(Theme.TEXT_SECONDARY);
        
        FaultType[] injectableFaults = {
            FaultType.STUCK_MID_FLIGHT,
            FaultType.NOZZLE_JAM,
            FaultType.ARRIVAL_SENSOR_FAILURE
        };
        JComboBox<String> faultSelector = new JComboBox<>();
        faultSelector.setBackground(Theme.BG_CARD);
        faultSelector.setForeground(Theme.TEXT_BRIGHT);
        faultSelector.setFont(Theme.FONT_MONO);
        for (FaultType ft : injectableFaults) {
            faultSelector.addItem(faultLabel(ft));
        }
        
        panel.add(l2);
        panel.add(faultSelector);

        JLabel l3 = new JLabel("DURATION (SEC):");
        l3.setFont(Theme.FONT_MONO);
        l3.setForeground(Theme.TEXT_SECONDARY);
        
        SpinnerNumberModel durationModel = new SpinnerNumberModel(10, 5, 60, 5);
        JSpinner durationSpinner = new JSpinner(durationModel);
        durationSpinner.setFont(Theme.FONT_MONO);
        durationSpinner.getEditor().getComponent(0).setBackground(Theme.BG_CARD);
        durationSpinner.getEditor().getComponent(0).setForeground(Theme.TEXT_BRIGHT);
        
        faultSelector.addActionListener(e -> {
            int idx = faultSelector.getSelectedIndex();
            boolean isHard = injectableFaults[idx] == FaultType.NOZZLE_JAM;
            durationSpinner.setEnabled(!isHard);
        });
        
        panel.add(l3);
        panel.add(durationSpinner);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(0, 16, 16, 16));
        
        JButton confirmBtn = new JButton("CONFIRM");
        confirmBtn.setBackground(Theme.BG_CARD);
        confirmBtn.setForeground(Theme.TEXT_PRIMARY);
        confirmBtn.setFont(Theme.FONT_MONO_BOLD);
        
        JButton abortBtn = new JButton("ABORT");
        abortBtn.setBackground(Theme.BG_CARD);
        abortBtn.setForeground(Theme.TEXT_SECONDARY);
        abortBtn.setFont(Theme.FONT_MONO_BOLD);
        
        confirmBtn.addActionListener(e -> {
            int droneIndex = droneSelector.getSelectedIndex() + 1;
            FaultType selectedFault = injectableFaults[faultSelector.getSelectedIndex()];
            long durationMs = ((Number) durationSpinner.getValue()).longValue() * 1000;
            sendFaultInjectionViaUDP(droneIndex, selectedFault, durationMs);
            dialog.dispose();
        });
        
        abortBtn.addActionListener(e -> dialog.dispose());
        
        btnPanel.add(abortBtn);
        btnPanel.add(confirmBtn);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

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
}