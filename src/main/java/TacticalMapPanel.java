import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TacticalMapPanel extends JPanel {
    private BufferedImage mapImage;
    private List<ZoneDef> zones;
    private Map<Integer, Boolean> zoneFireActive = new ConcurrentHashMap<>();
    private Map<Integer, FireEvent.Severity> zoneSeverities = new ConcurrentHashMap<>();
    private Map<Integer, DroneStatus> droneStatuses = new ConcurrentHashMap<>();
    private Timer animationTimer;
    private int radarAngle = 0;
    private long frameCount = 0;

    public TacticalMapPanel(List<ZoneDef> zones) {
        this.zones = zones;
        mapImage = new BufferedImage(Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION, BufferedImage.TYPE_INT_ARGB);
        setPreferredSize(new Dimension(800, 800));
        
        animationTimer = new Timer(Theme.FRAME_DELAY_MS, e -> {
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
        g.setColor(Theme.BG_MAIN);
        g.fillRect(0, 0, Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION);

        // Layer 2 - Zone fills
        for (ZoneDef zone : zones) {
            int pxX = zone.startCol * Theme.ZONE_PIXEL_SIZE;
            int pxY = zone.startRow * Theme.ZONE_PIXEL_SIZE;
            int pxW = zone.widthCols * Theme.ZONE_PIXEL_SIZE;
            int pxH = zone.heightRows * Theme.ZONE_PIXEL_SIZE;

            boolean hasFire = zoneFireActive.getOrDefault(zone.id, false);
            if (hasFire) {
                FireEvent.Severity sev = zoneSeverities.get(zone.id);
                Color fireColor = Theme.FIRE_MODERATE;
                if (sev == FireEvent.Severity.HIGH) fireColor = Theme.FIRE_HIGH;
                else if (sev == FireEvent.Severity.LOW) fireColor = Theme.FIRE_LOW;
                
                // Simple flicker
                if ((frameCount + zone.id) % 3 == 0) {
                    fireColor = fireColor.darker();
                }
                g.setColor(fireColor);
            } else {
                g.setColor(Theme.ZONE_SAFE);
            }
            g.fillRect(pxX, pxY, pxW, pxH);
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

        // Layer 4 - Drones
        for (DroneStatus ds : droneStatuses.values()) {
            if (ds.getState() == DroneState.IDLE && ds.getZoneId() != 0) {
                continue;
            }
            
            int centerX = ds.getCurrentCol() * Theme.ZONE_PIXEL_SIZE + Theme.ZONE_PIXEL_SIZE / 2;
            int centerY = ds.getCurrentRow() * Theme.ZONE_PIXEL_SIZE + Theme.ZONE_PIXEL_SIZE / 2;
            
            Color dColor = Theme.DRONE_IDLE;
            switch(ds.getState()) {
                case EN_ROUTE: dColor = Theme.DRONE_OUTBOUND; break;
                case EXTINGUISHING: dColor = Theme.DRONE_FIGHTING; break;
                case RETURNING: dColor = Theme.DRONE_RETURNING; break;
                case REFILLING: dColor = Theme.DRONE_REFILLING; break;
                case IDLE: dColor = Theme.DRONE_IDLE; break;
                case FAULTED: 
                    dColor = (ds.getFaultType() == FaultType.NOZZLE_JAM) ? Theme.FAULT_HARD : Theme.FAULT_SOFT;
                    break;
            }
            g.setColor(dColor);
            
            int r = 24; // size of drone blip on 2000px map
            int[] xPoints = {centerX, centerX + r, centerX, centerX - r};
            int[] yPoints = {centerY - r, centerY, centerY + r, centerY};
            g.fillPolygon(xPoints, yPoints, 4);
        }

        // Layer 5 - Radar sweep
        g.setColor(Theme.RADAR_SWEEP);
        g.fillArc(Theme.MAP_RESOLUTION/2 - Theme.MAP_RESOLUTION, Theme.MAP_RESOLUTION/2 - Theme.MAP_RESOLUTION,
                  Theme.MAP_RESOLUTION*2, Theme.MAP_RESOLUTION*2,
                  -radarAngle, 30);

        // Layer 6 - Scanlines
        g.setColor(Theme.SCANLINE);
        g.setStroke(new BasicStroke(1));
        for (int y = 0; y < Theme.MAP_RESOLUTION; y += 3) {
            g.drawLine(0, y, Theme.MAP_RESOLUTION, y);
        }

        g.dispose();
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
        } else {
            zoneSeverities.remove(zoneId);
        }
    }

    public int[] screenToGrid(int screenX, int screenY) {
        int col = screenX * Theme.GRID_COLS / getWidth();
        int row = screenY * Theme.GRID_ROWS / getHeight();
        return new int[]{col, row};
    }
}
