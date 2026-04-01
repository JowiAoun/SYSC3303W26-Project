import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TacticalMapPanel extends JPanel {
    private BufferedImage mapImage;
    private List<ZoneDef> zones;
    private Map<String, FireEvent> activeFires = new ConcurrentHashMap<>();
    private Map<String, Long> fireExtinguishedFrame = new ConcurrentHashMap<>();
    
    private Map<Integer, DroneStatus> droneStatuses = new ConcurrentHashMap<>();
    private Map<Integer, Double> takeoffScales = new ConcurrentHashMap<>();
    
    private javax.swing.Timer animationTimer;
    private int radarAngle = 0;
    private long frameCount = 0;
    private long bootFrameCount = 0;
    private static final long BOOT_DURATION = 45; // ~1.35s

    public TacticalMapPanel(List<ZoneDef> zones) {
        this.zones = zones;
        SpriteManager.init();
        mapImage = new BufferedImage(Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION, BufferedImage.TYPE_INT_ARGB);
        setPreferredSize(new Dimension(800, 800));
        
        animationTimer = new javax.swing.Timer(Theme.FRAME_DELAY_MS, e -> {
            radarAngle = (radarAngle + 2) % 360;
            frameCount++;
            repaint();
        });
        animationTimer.start();
    }

    private void renderMap() {
        Graphics2D g = mapImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Layer 1 - Background
        if (SpriteManager.mapSprite != null) {
            g.drawImage(SpriteManager.mapSprite, 0, 0, Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION, null);
        } else {
            g.setColor(Theme.BG_MAIN);
            g.fillRect(0, 0, Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION);
        }

        // Layer 2 - Zone fills (Thermal pixels & Extinguish flash)
        // Layer 2 - Drawing precise active fires
        for (FireEvent fire : activeFires.values()) {
            ZoneDef zone = getZoneById(fire.getZoneId());
            if (zone == null) continue;
            
            FireEvent.Severity sev = fire.getSeverity();
            int[] targetCell = PathPlanner.fireTargetCell(zone, fire.getTime());
            
            int spotX = targetCell[0] * Theme.ZONE_PIXEL_SIZE + (Theme.ZONE_PIXEL_SIZE / 2);
            int spotY = targetCell[1] * Theme.ZONE_PIXEL_SIZE + (Theme.ZONE_PIXEL_SIZE / 2);
            int radius = (sev == FireEvent.Severity.HIGH) ? 30 : (sev == FireEvent.Severity.MODERATE ? 20 : 10);
            
            if (SpriteManager.fireSprites != null) {
                int fireFrame = (int) ((frameCount / 3) % SpriteManager.fireSprites.length);
                BufferedImage fImg = SpriteManager.fireSprites[fireFrame];
                int fw = radius * 3;
                int fh = radius * 3;
                // Render at target cell, shifted +28px down to place fire beneath drone visually
                g.drawImage(fImg, spotX - fw/2, spotY - fh/2 + 28, fw, fh, null);
            } else {
                g.setColor(new Color(42, 10, 10, 200)); 
                g.fillOval(spotX - radius, spotY - radius, radius*2, radius*2);
            }
        }

        // Layer 3 - Grid lines (Removed as per map sprite integration)

        // Layer 4 - Drones & Base
        ZoneDef baseZone = getZoneById(0);
        if (baseZone != null && SpriteManager.truckSprites != null) {
            int cx = baseZone.startCol * Theme.ZONE_PIXEL_SIZE + (baseZone.widthCols * Theme.ZONE_PIXEL_SIZE) / 2 + 5;
            int cy = baseZone.startRow * Theme.ZONE_PIXEL_SIZE + (baseZone.heightRows * Theme.ZONE_PIXEL_SIZE) / 2 + 30;
            int truckFrame = (int) ((frameCount / 10) % SpriteManager.truckSprites.length);
            BufferedImage tImg = SpriteManager.truckSprites[truckFrame];
            int tw = 90, th = 60; 
            g.drawImage(tImg, cx - tw/2, cy - th/2, tw, th, null);
        }

        for (DroneStatus ds : droneStatuses.values()) {
            double animScale = takeoffScales.getOrDefault(ds.getDroneId(), 0.0);
            boolean atBaseIdle = (ds.getState() == DroneState.IDLE || ds.getState() == DroneState.REFILLING) && ds.getZoneId() == 0;

            if (atBaseIdle) {
                if (animScale > 0.0) {
                    animScale -= 0.05;
                    if (animScale <= 0.0) animScale = 0.0;
                }
            } else {
                if (animScale < 1.0) {
                    animScale += 0.05;
                    if (animScale > 1.0) animScale = 1.0;
                }
            }
            takeoffScales.put(ds.getDroneId(), animScale);

            if (animScale <= 0.0 && atBaseIdle) {
                continue; // Fully landed and hidden inside base truck
            }

            if (ds.getState() == DroneState.IDLE && ds.getZoneId() != 0) {
                continue;
            }
            
            Color dColor = Theme.DRONE_IDLE;
            boolean isHard = (ds.getFaultType() == FaultType.NOZZLE_JAM);
            switch(ds.getState()) {
                case EN_ROUTE: dColor = Theme.DRONE_OUTBOUND; break;
                case EXTINGUISHING: dColor = Theme.DRONE_FIGHTING; break;
                case RETURNING: dColor = Theme.DRONE_RETURNING; break;
                case REFILLING: dColor = Theme.DRONE_REFILLING; break;
                case IDLE: dColor = Theme.DRONE_IDLE; break;
                case FAULTED: 
                    dColor = isHard ? Theme.FAULT_HARD : Theme.FAULT_SOFT;
                    break;
            }

            int centerX = ds.getCurrentCol() * Theme.ZONE_PIXEL_SIZE + Theme.ZONE_PIXEL_SIZE / 2;
            int centerY = ds.getCurrentRow() * Theme.ZONE_PIXEL_SIZE + Theme.ZONE_PIXEL_SIZE / 2;

            // Draw Pulse Glow (Trail removed)
            if (ds.getState() == DroneState.EXTINGUISHING || ds.getState() == DroneState.REFILLING) {
                int pulseRadius = 20 + (int)(Math.sin(frameCount * 0.2) * 10);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
                g.setColor(dColor);
                g.fillOval(centerX - pulseRadius, centerY - pulseRadius, pulseRadius*2, pulseRadius*2);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }

            // Direction calculation
            double targetX = centerX;
            double targetY = centerY;
            if (ds.getState() == DroneState.EN_ROUTE || ds.getState() == DroneState.RETURNING) {
                ZoneDef targetZone = getZoneById(ds.getZoneId());
                if (targetZone != null) {
                    targetX = targetZone.startCol * Theme.ZONE_PIXEL_SIZE + (targetZone.widthCols * Theme.ZONE_PIXEL_SIZE) / 2.0;
                    targetY = targetZone.startRow * Theme.ZONE_PIXEL_SIZE + (targetZone.heightRows * Theme.ZONE_PIXEL_SIZE) / 2.0;
                }
            }
            double angle = Math.atan2(targetY - centerY, targetX - centerX);
            if (targetX == centerX && targetY == centerY) {
                angle = -Math.PI / 2; // Default point UP if no target direction
            }

            // Draw Drone Sprite or Chevron
            if (SpriteManager.droneSprites != null) {
                int frame = 4; // default to dry frame (full tank)
                if (ds.getRemainingLiters() <= 0) {
                    frame = 7; // empty tank
                }
                
                if (ds.getState() == DroneState.EXTINGUISHING) {
                    int[] extFrames = {0, 3, 1, 2, 6, 5};
                    frame = extFrames[(int) ((frameCount / 4) % extFrames.length)];
                } else if (ds.getState() == DroneState.EN_ROUTE) {
                    frame = 4; 
                } else if (ds.getState() == DroneState.RETURNING) {
                    frame = 7; 
                }
                
                if (frame >= SpriteManager.droneSprites.length) {
                    frame = 0;
                }
                
                BufferedImage dImg = SpriteManager.droneSprites[frame];
                double scale = 0.12 * animScale;
                int dw = (int) (dImg.getWidth() * scale);
                int dh = (int) (dImg.getHeight() * scale);
                int yOffset = 0;
                g.drawImage(dImg, centerX - dw / 2, (int) (centerY - (32 * animScale)) - yOffset, dw, dh, null);
            } else {
                AffineTransform oldTransform = g.getTransform();
                g.translate(centerX, centerY);
                g.rotate(angle);
                
                g.setColor(dColor);
                int[] cx = {15, -10, -5, -10};
                int[] cy = {0, -12, 0, 12};
                
                // Scale chevron visually
                for (int i=0; i<4; i++) {
                    cx[i] = (int)(cx[i] * animScale);
                    cy[i] = (int)(cy[i] * animScale);
                }
                
                g.fillPolygon(cx, cy, 4);
                
                g.setTransform(oldTransform);
            }

            // Draw spark effect if faulted
            if (ds.getState() == DroneState.FAULTED) {
                // Update sparkles every 3 frames (slower flicker)
                Random r = new Random((frameCount / 3) + ds.getDroneId());
                int numSparks = 1 + r.nextInt(3); // 1 to 3 sparks instead of 4-8
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setStroke(new BasicStroke(1.5f));
                int sparkCenterY = (int)(centerY - (32 * animScale));
                for (int i = 0; i < numSparks; i++) {
                    g2.setColor(r.nextBoolean() ? Color.YELLOW : Color.WHITE);
                    int x1 = centerX - 15 + r.nextInt(31);
                    int y1 = sparkCenterY - 15 + r.nextInt(31);
                    int x2 = x1 - 8 + r.nextInt(17);
                    int y2 = y1 - 8 + r.nextInt(17);
                    g2.drawLine(x1, y1, x2, y2);
                }
                g2.dispose();
            }
        }

        // Layer 5 - Radar sweep (Filled arc with gradient fade effect)
        int radCenter = Theme.MAP_RESOLUTION / 2;
        int radRadius = Theme.MAP_RESOLUTION;
        
        for (int i = 0; i < 30; i++) {
            float alpha = 0.4f * (1.0f - (i / 30.0f));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(Theme.RADAR_SWEEP);
            g.fillArc(radCenter - radRadius, radCenter - radRadius,
                      radRadius * 2, radRadius * 2,
                      -radarAngle + i, 2);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        // Leading edge line
        g.setColor(Theme.TEXT_BRIGHT);
        g.drawArc(radCenter - radRadius, radCenter - radRadius, radRadius * 2, radRadius * 2, -radarAngle, 1);

        // Layer 6 - Scanlines
        g.setColor(Theme.SCANLINE);
        g.setStroke(new BasicStroke(1));
        for (int y = 0; y < Theme.MAP_RESOLUTION; y += 3) {
            g.drawLine(0, y, Theme.MAP_RESOLUTION, y);
        }

        // Layer 7 - CRT Boot Sequence Overlay
        if (bootFrameCount < BOOT_DURATION) {
            bootFrameCount++;
            float alpha = 1.0f - ((float)bootFrameCount / BOOT_DURATION);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            
            // Background blanking
            g.setColor(Theme.BG_MAIN);
            g.fillRect(0, 0, Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION);
            
            // Boot text
            g.setColor(Theme.TEXT_BRIGHT);
            g.setFont(Theme.FONT_TITLE.deriveFont(32f));
            String text = "INITIALIZING TACTICAL DISPLAY...";
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(text);
            g.drawString(text, (Theme.MAP_RESOLUTION - width) / 2, Theme.MAP_RESOLUTION / 2);
            
            // Random noise scanlines
            Random r = new Random(frameCount);
            g.setColor(Theme.SCANLINE);
            for (int i = 0; i < 150; i++) {
                int y = r.nextInt(Theme.MAP_RESOLUTION);
                g.drawLine(0, y, Theme.MAP_RESOLUTION, y);
            }
            
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

        g.dispose();
    }

    private ZoneDef getZoneById(int id) {
        for (ZoneDef z : zones) {
            if (z.id == id) return z;
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderMap();
        g.drawImage(mapImage, 0, 0, getWidth(), getHeight(), null);
    }

    public synchronized void updateDrone(DroneStatus status) {
        droneStatuses.put(status.getDroneId(), status);
    }

    public synchronized void addActiveFire(FireEvent event) {
        activeFires.put(event.getTime(), event);
        fireExtinguishedFrame.remove(event.getTime());
    }

    public synchronized void removeActiveFire(FireEvent event) {
        activeFires.remove(event.getTime());
        fireExtinguishedFrame.put(event.getTime(), frameCount);
    }
    
    public int getActiveFiresCount() {
        return activeFires.size();
    }

    public int[] screenToGrid(int screenX, int screenY) {
        int col = screenX * Theme.GRID_COLS / getWidth();
        int row = screenY * Theme.GRID_ROWS / getHeight();
        return new int[]{col, row};
    }
}
