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
    private Map<Integer, Boolean> zoneFireActive = new ConcurrentHashMap<>();
    private Map<Integer, FireEvent.Severity> zoneSeverities = new ConcurrentHashMap<>();
    private Map<Integer, Long> zoneExtinguishedFrame = new ConcurrentHashMap<>();
    
    private Map<Integer, DroneStatus> droneStatuses = new ConcurrentHashMap<>();
    private Map<Integer, List<Point2D.Double>> droneTrails = new ConcurrentHashMap<>();
    private static final int MAX_TRAIL_LENGTH = 8;
    
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
            updateTrails();
            repaint();
        });
        animationTimer.start();
    }

    private void updateTrails() {
        if (frameCount % 3 != 0) return; // Add to trail every 3rd frame
        for (Map.Entry<Integer, DroneStatus> entry : droneStatuses.entrySet()) {
            DroneStatus ds = entry.getValue();
            if (ds.getState() == DroneState.IDLE) continue;
            
            double cx = ds.getCurrentCol() * Theme.ZONE_PIXEL_SIZE + Theme.ZONE_PIXEL_SIZE / 2.0;
            double cy = ds.getCurrentRow() * Theme.ZONE_PIXEL_SIZE + Theme.ZONE_PIXEL_SIZE / 2.0;
            
            List<Point2D.Double> trail = droneTrails.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            if (trail.isEmpty() || trail.get(0).distance(cx, cy) > 5) {
                trail.add(0, new Point2D.Double(cx, cy));
                if (trail.size() > MAX_TRAIL_LENGTH) {
                    trail.remove(trail.size() - 1);
                }
            }
        }
    }

    private void renderMap() {
        Graphics2D g = mapImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Layer 1 - Background
        g.setColor(Theme.BG_MAIN);
        g.fillRect(0, 0, Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION);

        // Layer 2 - Zone fills (Thermal pixels & Extinguish flash)
        for (ZoneDef zone : zones) {
            int pxX = zone.startCol * Theme.ZONE_PIXEL_SIZE;
            int pxY = zone.startRow * Theme.ZONE_PIXEL_SIZE;
            int pxW = zone.widthCols * Theme.ZONE_PIXEL_SIZE;
            int pxH = zone.heightRows * Theme.ZONE_PIXEL_SIZE;

            boolean hasFire = zoneFireActive.getOrDefault(zone.id, false);
            Long extFrame = zoneExtinguishedFrame.get(zone.id);
            
            // Base Zone Highlight (Transparent overlay)
            if (hasFire) {
                g.setColor(new Color(255, 10, 10, 50)); 
                g.fillRect(pxX, pxY, pxW, pxH);
            } else {
                g.setColor(new Color(Theme.ZONE_SAFE.getRed(), Theme.ZONE_SAFE.getGreen(), Theme.ZONE_SAFE.getBlue(), 30));
                g.fillRect(pxX, pxY, pxW, pxH);
            }
            
            // Fire Spot processing
            if (hasFire) {
                FireEvent.Severity sev = zoneSeverities.get(zone.id);
                // Fixed spot per zone
                Random spotRand = new Random(zone.id * 738L);
                int margin = Math.min(pxW, pxH) / 4; 
                int spotX = pxX + margin + spotRand.nextInt(Math.max(1, pxW - 2*margin));
                int spotY = pxY + margin + spotRand.nextInt(Math.max(1, pxH - 2*margin));
                int radius = (sev == FireEvent.Severity.HIGH) ? 45 : (sev == FireEvent.Severity.MODERATE ? 30 : 15);
                
                // Thermal base circle
                if (SpriteManager.fireSprites != null) {
                    int fireFrame = (int) ((frameCount / 3) % SpriteManager.fireSprites.length);
                    BufferedImage fImg = SpriteManager.fireSprites[fireFrame];
                    int fw = radius * 3;
                    int fh = radius * 3;
                    g.drawImage(fImg, spotX - fw/2, spotY - fh/2, fw, fh, null);
                } else {
                    g.setColor(new Color(42, 10, 10, 200)); 
                    g.fillOval(spotX - radius, spotY - radius, radius*2, radius*2);
                    
                    // Thermal hot pixels
                    Random r = new Random(frameCount + zone.id);
                    int numPixels = (sev == FireEvent.Severity.HIGH) ? 150 : (sev == FireEvent.Severity.MODERATE ? 80 : 30);
                    for (int i=0; i<numPixels; i++) {
                        double ang = r.nextDouble() * 2 * Math.PI;
                        double currRad = Math.sqrt(r.nextDouble()) * radius;
                        int x = (int)(spotX + currRad * Math.cos(ang));
                        int y = (int)(spotY + currRad * Math.sin(ang));
                        int size = 3 + r.nextInt(5);
                        g.setColor(r.nextBoolean() ? Theme.FIRE_HIGH : Theme.FIRE_MODERATE);
                        g.fillRect(x - size/2, y - size/2, size, size);
                    }
                }
            } else if (extFrame != null && (frameCount - extFrame) < 15) {
                Random spotRand = new Random(zone.id * 738L);
                int margin = Math.min(pxW, pxH) / 4; 
                int spotX = pxX + margin + spotRand.nextInt(Math.max(1, pxW - 2*margin));
                int spotY = pxY + margin + spotRand.nextInt(Math.max(1, pxH - 2*margin));
                int radius = 35;
                // Flash green spot
                g.setColor(Theme.TEXT_BRIGHT);
                g.fillOval(spotX - radius, spotY - radius, radius*2, radius*2);
            }
        }

        // Layer 3 - Grid lines
        g.setColor(Theme.GRID_LINE_FAINT);
        for (int i = 0; i <= Theme.GRID_COLS; i++) {
            g.drawLine(i * Theme.ZONE_PIXEL_SIZE, 0, i * Theme.ZONE_PIXEL_SIZE, Theme.MAP_RESOLUTION);
        }
        for (int i = 0; i <= Theme.GRID_ROWS; i++) {
            g.drawLine(0, i * Theme.ZONE_PIXEL_SIZE, Theme.MAP_RESOLUTION, i * Theme.ZONE_PIXEL_SIZE);
        }
        
        g.setColor(Theme.GRID_LINE_ZONE);
        g.setStroke(new BasicStroke(3));
        for (ZoneDef zone : zones) {
            int pxX = zone.startCol * Theme.ZONE_PIXEL_SIZE;
            int pxY = zone.startRow * Theme.ZONE_PIXEL_SIZE;
            int pxW = zone.widthCols * Theme.ZONE_PIXEL_SIZE;
            int pxH = zone.heightRows * Theme.ZONE_PIXEL_SIZE;
            g.drawRect(pxX, pxY, pxW, pxH);
        }

        // Layer 4 - Drones & Trails
        for (DroneStatus ds : droneStatuses.values()) {
            if (ds.getState() == DroneState.IDLE && ds.getZoneId() != 0) {
                continue;
            }
            
            if (ds.getState() == DroneState.FAULTED && (frameCount % 30 < 15)) {
                continue; // Blink faulted drones
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

            // Draw Trail
            List<Point2D.Double> trail = droneTrails.get(ds.getDroneId());
            if (trail != null && ds.getState() != DroneState.IDLE) {
                for (int i = 0; i < trail.size(); i++) {
                    Point2D.Double p = trail.get(i);
                    float alpha = 0.5f * (1.0f - ((float)i / MAX_TRAIL_LENGTH));
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g.setColor(dColor);
                    g.fillOval((int)p.x - 6, (int)p.y - 6, 12, 12);
                }
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }

            // Draw Pulse Glow
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

            // Draw Drone Sprite, Chevron, or Base Truck
            if ((ds.getState() == DroneState.IDLE || ds.getState() == DroneState.REFILLING) 
                && ds.getZoneId() == 0 && SpriteManager.truckSprites != null) {
                int truckFrame = (int) ((frameCount / 10) % SpriteManager.truckSprites.length);
                BufferedImage tImg = SpriteManager.truckSprites[truckFrame];
                int tw = 60, th = 40; 
                g.drawImage(tImg, centerX - tw/2, centerY - th/2, tw, th, null);
                
            } else if (SpriteManager.droneSprites != null) {
                int droneFrame = (int) ((frameCount / 2) % 4); 
                BufferedImage dImg = SpriteManager.droneSprites[droneFrame];
                
                AffineTransform oldTransform = g.getTransform();
                g.translate(centerX, centerY);
                g.rotate(angle - Math.PI / 2); // drones point DOWN in sprite
                
                int dw = 32, dh = 32;
                g.drawImage(dImg, -dw/2, -dh/2, dw, dh, null);
                
                g.setTransform(oldTransform);
            } else {
                AffineTransform oldTransform = g.getTransform();
                g.translate(centerX, centerY);
                g.rotate(angle);
                
                g.setColor(dColor);
                int[] cx = {15, -10, -5, -10};
                int[] cy = {0, -12, 0, 12};
                g.fillPolygon(cx, cy, 4);
                
                g.setTransform(oldTransform);
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

    public synchronized void setZoneFire(int zoneId, boolean active, FireEvent.Severity severity) {
        zoneFireActive.put(zoneId, active);
        if (active) {
            zoneSeverities.put(zoneId, severity);
            zoneExtinguishedFrame.remove(zoneId);
        } else {
            zoneSeverities.remove(zoneId);
            zoneExtinguishedFrame.put(zoneId, frameCount);
        }
    }

    public int[] screenToGrid(int screenX, int screenY) {
        int col = screenX * Theme.GRID_COLS / getWidth();
        int row = screenY * Theme.GRID_ROWS / getHeight();
        return new int[]{col, row};
    }
}
