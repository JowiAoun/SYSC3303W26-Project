import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ZoneLoader.java
 *
 * Loads rectangular zone definitions from a CSV file.
 * Format:
 *   Zone ID,Zone Start,Zone End
 *   1,(0;0),(700;600)
 */
public final class ZoneLoader {
    // simple loader for zone csv files
    private ZoneLoader() {}

    /**
     * load zones from a csv file and map to the grid.
     */
    public static List<ZoneDef> loadZones(String csvPath, int cols, int rows) {
        List<ZoneDef> zones = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.toLowerCase().startsWith("zone")) {
                    continue;
                }

                // parse "id,(x;y),(x;y)" without regex
                ParsedLine parsed = parseLine(trimmed);
                if (parsed == null) {
                    continue;
                }

                int zoneId = parsed.zoneId;
                double[] start = parsed.start;
                double[] end = parsed.end;

                double startX = Math.min(start[0], end[0]);
                double startY = Math.min(start[1], end[1]);
                double endX = Math.max(start[0], end[0]);
                double endY = Math.max(start[1], end[1]);

                int startCol = (int) startX;
                int startRow = (int) startY;
                int widthCols = Math.max(1, (int) (endX - startX));
                int heightRows = Math.max(1, (int) (endY - startY));

                Color color = zoneId == 0 ? new Color(220, 220, 220) : pickColor(zoneId);
                zones.add(new ZoneDef(
                        zoneId,
                        startCol,
                        startRow,
                        widthCols,
                        heightRows,
                        startX,
                        startY,
                        endX,
                        endY,
                        color
                ));
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read zone file: " + ex.getMessage(), ex);
        }

        if (zones.isEmpty()) {
            throw new RuntimeException("Zone file contains no valid zones: " + csvPath);
        }

        return zones;
    }

    /**
     * read a point like "0;0" or "0,0".
     */
    private static double[] parsePoint(String raw) {
        String cleaned = raw.trim();
        String[] parts = cleaned.split("[;,]");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid point: " + raw);
        }
        double x = Double.parseDouble(parts[0].trim());
        double y = Double.parseDouble(parts[1].trim());
        return new double[] { x, y };
    }

    /**
     * pick a color based on zone id.
     */
    private static Color pickColor(int zoneId) {
        Color[] palette = new Color[] {
                new Color(235, 245, 255),
                new Color(235, 255, 235),
                new Color(255, 245, 235),
                new Color(245, 235, 255),
                new Color(235, 250, 245),
                new Color(250, 240, 235),
                new Color(255, 255, 235)
        };
        return palette[Math.floorMod(zoneId, palette.length)];
    }

    /**
     * split one csv line into id, start, end.
     */
    private static ParsedLine parseLine(String line) {
        String[] parts = line.split(",", 3);
        if (parts.length < 3) {
            return null;
        }

        String idPart = parts[0].trim();
        String startPart = stripParens(parts[1].trim());
        String endPart = stripParens(parts[2].trim());

        if (idPart.isEmpty() || startPart.isEmpty() || endPart.isEmpty()) {
            return null;
        }

        int zoneId;
        try {
            zoneId = Integer.parseInt(idPart);
        } catch (NumberFormatException ex) {
            return null;
        }

        double[] start = parsePoint(startPart);
        double[] end = parsePoint(endPart);
        return new ParsedLine(zoneId, start, end);
    }

    /**
     * remove surrounding parentheses if present.
     */
    private static String stripParens(String value) {
        String cleaned = value;
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static final class ParsedLine {
        private final int zoneId;
        private final double[] start;
        private final double[] end;

        private ParsedLine(int zoneId, double[] start, double[] end) {
            this.zoneId = zoneId;
            this.start = start;
            this.end = end;
        }
    }
}
