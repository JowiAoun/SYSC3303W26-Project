import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class FireDroneGUI extends JFrame {
    private static final String APP_TITLE = "FIRE DRONE SIMULATOR - TACTICAL HUD";
    private static final int COLS = 200;
    private static final int ROWS = 200;

    private TacticalMapPanel tacticalMap;
    private JTextPane commsLogPane;

    private final List<ZoneDef> zones = new ArrayList<>();
    private final List<DroneCard> droneCards = new ArrayList<>();
    private final List<ZoneCard> zoneCards = new ArrayList<>();
    
    private boolean flashState500 = false;
    private boolean flashState750 = false;
    private javax.swing.Timer borderAnimTimer;
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

        // Sidebar Animations
        borderAnimTimer = new javax.swing.Timer(250, e -> {
            long time = System.currentTimeMillis();
            flashState500 = (time / 500) % 2 == 0;
            flashState750 = (time / 750) % 2 == 0;
            for (ZoneCard zc : zoneCards) zc.updateBorder();
            for (DroneCard dc : droneCards) dc.updateBorder();
        });
        borderAnimTimer.start();

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

        PortraitPanel portraitPanel = new PortraitPanel();
        container.add(portraitPanel, BorderLayout.SOUTH);
        
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
        private boolean isActive = false;
        private FireEvent.Severity currentSeverity = null;

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
            this.isActive = active;
            this.currentSeverity = severity;
            
            if (active) {
                statusLabel.setText("FIRE " + severityShortLabel(severity));
                if (severity == FireEvent.Severity.HIGH) statusLabel.setForeground(Theme.FIRE_HIGH);
                else if (severity == FireEvent.Severity.MODERATE) statusLabel.setForeground(Theme.FIRE_MODERATE);
                else statusLabel.setForeground(Theme.FIRE_LOW);
            } else {
                statusLabel.setText("CLEAR");
                statusLabel.setForeground(Theme.TEXT_SECONDARY);
            }
            updateBorder();
        }
        
        public void updateBorder() {
            if (isActive) {
                Color c = flashState500 ? Theme.FIRE_ACTIVE : Theme.FIRE_HIGH;
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(c, 1),
                        new EmptyBorder(8, 8, 8, 8)
                ));
            } else {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.BORDER_DEFAULT, 1),
                        new EmptyBorder(8, 8, 8, 8)
                ));
            }
        }
    }
    private class PortraitPanel extends JPanel {
        private int frameCount = 0;
        private boolean isSpeaking = false;
        private String speechText = "";
        private float bubbleScale = 0.0f;
        private int speechTimer = 0;

        public PortraitPanel() {
            setPreferredSize(new Dimension(250, 420));
            setBackground(Theme.BG_PANEL);
            setBorder(null);

            new javax.swing.Timer(50, e -> {
                frameCount++;
                if (isSpeaking) {
                    bubbleScale = Math.min(1.0f, bubbleScale + 0.30f);
                } else {
                    bubbleScale = Math.max(0.0f, bubbleScale - 0.40f);
                }
                
                if (speechTimer > 0) {
                    speechTimer--;
                    if (speechTimer == 0) {
                        setIdle();
                    }
                } else if (Math.random() < 0.005) { // Roughly every 10 seconds empty
                    triggerRandomSpeech();
                }
                
                repaint();
            }).start();
        }

        private void triggerRandomSpeech() {
            int fires = activeZoneIds.size();
            int faults = faultedDroneIds.size();
            
            String[] options;
            if (fires > 0 && faults > 0) {
                options = new String[]{
                    "Warning! We have " + fires + " active fires and " + faults + " drones offline!",
                    "Deploy drones carefully, " + faults + " units are currently faulted.",
                    "We need to put out those " + fires + " fires immediately."
                };
            } else if (fires > 0) {
                options = new String[]{
                    "Target acquired: " + fires + " fires remaining.",
                    "All units, converge on the " + fires + " active thermal signatures.",
                    "Do not let those " + fires + " fires spread to adjacent zones!"
                };
            } else if (faults > 0) {
                options = new String[]{
                    "Maintenance team, we have " + faults + " units requiring repairs.",
                    "Keep an eye on telemetry. " + faults + " drones are currently grounded."
                };
            } else {
                options = new String[]{
                    "All sectors clear. Awaiting further orders.",
                    "Swarm is fully operational and standing by.",
                    "Simulated environment stable. No anomalies detected."
                };
            }
            
            setSpeech(options[(int)(Math.random() * options.length)], 80); // 4 seconds
        }

        public void setSpeech(String text, int ticks) {
            this.speechText = text;
            this.isSpeaking = true;
            this.speechTimer = ticks;
        }

        public void setIdle() {
            this.isSpeaking = false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (SpriteManager.portraitSprites != null && SpriteManager.portraitSprites.length >= 6) {
                int frame = 0;
                if (isSpeaking) {
                    int[] speakFrames = {3, 4, 5, 4};
                    frame = speakFrames[(frameCount / 3) % speakFrames.length]; // Animation timing adjustment
                } else {
                    if (frameCount % 120 < 9) { // Blink timing adjustment
                        int[] blinkFrames = {0, 1, 2};
                        frame = blinkFrames[(frameCount % 120) / 3];
                    } else {
                        frame = 0; // open eyes
                    }
                }
                
                BufferedImage pImg = SpriteManager.portraitSprites[frame];
                // Math config
                int px = 20;
                int pPad = 12;
                int py = 45; // Antenna room
                int pw = getWidth() - (px * 2);
                int ph = pw;
                
                // Draw Antenna
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2));
                int cx = px + (pw / 2);
                g2.drawLine(cx, py - pPad, cx - 25, py - pPad - 25);
                g2.drawLine(cx, py - pPad, cx + 25, py - pPad - 25);
                g2.fillOval(cx - 28, py - pPad - 28, 6, 6);
                g2.fillOval(cx + 22, py - pPad - 28, 6, 6);
                g2.setStroke(new BasicStroke(1));
                
                // Draw TV Border Chassis
                g2.setColor(new Color(0x22, 0x22, 0x22));
                g2.fillRoundRect(px - pPad, py - pPad, pw + (pPad * 2), ph + (pPad * 2) + 8, 10, 10);
                g2.setColor(new Color(0x3D, 0x3D, 0x3D));
                g2.drawRoundRect(px - pPad, py - pPad, pw + (pPad * 2), ph + (pPad * 2) + 8, 10, 10);
                
                // Draw Knobs
                g2.setColor(Color.GRAY);
                g2.fillOval(px + pw - 15, py + ph + 8, 8, 8);
                g2.fillOval(px + pw - 30, py + ph + 8, 8, 8);
                
                // Draw Screen Backdrop
                g2.setColor(Color.BLACK);
                g2.fillRect(px, py, pw, ph);

                // Draw Character Image (Flipped horizontally to look right)
                g2.drawImage(pImg, px + pw, py, -pw, ph, null);
                
                // Overlay TV Scanlines
                g2.setColor(new Color(0, 0, 0, 40));
                for(int i = py; i < py + ph; i += 4) {
                    g2.drawLine(px, i, px + pw, i);
                }
                
                // Overlay Random Static Drops
                if (frameCount % 70 < 8) {
                    g2.setColor(new Color(255, 255, 255, 40));
                    int staticHeight = 25;
                    int staticY = py + (int)(Math.random() * (ph - staticHeight));
                    g2.fillRect(px, staticY, pw, staticHeight);
                    
                    g2.setColor(new Color(0, 0, 0, 60));
                    staticY = py + (int)(Math.random() * (ph - staticHeight));
                    g2.fillRect(px, staticY, pw, staticHeight);
                }
                
                // TV "LIVE" Broadcast Overlay Banner
                int barHeight = 26;
                int barY = py + ph - barHeight;
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(px, barY, pw, barHeight);
                
                if (frameCount % 10 < 5) {
                    g2.setColor(Theme.FIRE_HIGH);
                    g2.fillOval(px + 8, barY + 8, 10, 10);
                }
                
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.FONT_MONO_BOLD);
                g2.drawString("LIVE: Cmdt. Luke", px + 24, barY + 18);
                
                // Speech bubble (Animated Scale & Shake)
                if (bubbleScale > 0) {
                    int bx = 15;
                    int baseBy = py + ph + pPad + 18;
                    int maxBh = getHeight() - baseBy - 10;
                    
                    int bh = (int)(maxBh * bubbleScale);
                    int by = baseBy;
                    int bw = getWidth() - 30;

                    int shakeX = (isSpeaking && bubbleScale > 0.95f) ? (int)(Math.random() * 4) - 2 : 0;
                    int shakeY = (isSpeaking && bubbleScale > 0.95f) ? (int)(Math.random() * 4) - 2 : 0;

                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(bx + shakeX, by + shakeY, bw, bh, 10, 10);
                    g2.setColor(Color.BLACK);
                    g2.drawRoundRect(bx + shakeX, by + shakeY, bw, bh, 10, 10);

                    // Draw polygon tail only if bubble is big enough
                    if (bubbleScale > 0.5f) {
                        int tx = getWidth() / 2;
                        int ty = by - 8;
                        int[] xPoints = {tx - 6 + shakeX, tx + 6 + shakeX, tx + shakeX};
                        int[] yPoints = {by + shakeY, by + shakeY, ty + shakeY};
                        g2.setColor(Color.WHITE);
                        g2.fillPolygon(xPoints, yPoints, 3);
                        g2.setColor(Color.BLACK);
                        g2.drawLine(tx - 6 + shakeX, by + shakeY, tx + shakeX, ty + shakeY);
                        g2.drawLine(tx + 6 + shakeX, by + shakeY, tx + shakeX, ty + shakeY);
                        g2.setColor(Color.WHITE);
                        g2.drawLine(tx - 5 + shakeX, by + shakeY, tx + 5 + shakeX, by + shakeY);
                    }
                    
                    // Draw text only if fully open
                    if (bubbleScale > 0.95f) {
                        g2.setColor(Color.BLACK);
                        g2.setFont(Theme.FONT_MONO.deriveFont(18f));
                        
                        FontMetrics fm = g2.getFontMetrics();
                        int textY = by + 24 + shakeY;
                        String[] words = speechText.split(" ");
                        StringBuilder line = new StringBuilder();
                        for (String word : words) {
                            if (fm.stringWidth(line.toString() + word) < bw - 16) {
                                line.append(word).append(" ");
                            } else {
                                g2.drawString(line.toString(), bx + 8 + shakeX, textY);
                                line = new StringBuilder(word + " ");
                                textY += fm.getHeight();
                            }
                        }
                        if (line.length() > 0) {
                            g2.drawString(line.toString(), bx + 8 + shakeX, textY);
                        }
                    }
                }
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
            
            Color fill = Theme.WATER_LEVEL;
            if (ratio <= 0.1f) fill = Theme.WATER_CRITICAL;
            else if (ratio <= 0.3f) fill = Theme.WATER_LOW;
            
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
        private DroneStatus currentStatus = null;
        private boolean isHardFaulted = false;

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
            this.currentStatus = ds;
            this.isHardFaulted = isHardFaulted;
            
            if (isHardFaulted) {
                stateLabel.setText("OFFLINE");
            } else {
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
            updateBorder();
        }
        
        public void updateBorder() {
            if (currentStatus != null && currentStatus.getState() == DroneState.FAULTED) {
                Color c = isHardFault(currentStatus.getFaultType()) ? Theme.FAULT_HARD : Theme.FAULT_SOFT;
                Color borderC = flashState750 ? c : Theme.BORDER_DEFAULT;
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderC, 1),
                    new EmptyBorder(8, 8, 8, 8)
                ));
            } else if (isHardFaulted) {
                Color borderC = flashState750 ? Theme.FAULT_HARD : Theme.BORDER_DEFAULT;
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderC, 1),
                    new EmptyBorder(8, 8, 8, 8)
                ));
            } else {
                setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_DEFAULT, 1),
                    new EmptyBorder(8, 8, 8, 8)
                ));
            }
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