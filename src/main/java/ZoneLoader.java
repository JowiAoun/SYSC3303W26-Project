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
 *
 * Coordinates are interpreted as meters when using {@link #loadZonesMeters(String, double)}.
 */
public final class ZoneLoader {
    /** Default simulation resolution: one grid cell = 100 m. */
    public static final double DEFAULT_CELL_SIZE_METERS = 100.0;

    /**
     * Grid cells reserved at the top-left for zone 0 (refuel / base). Fire zones from the CSV are
     * shifted right and down by this amount so zone 1’s corner is not (0,0) and fires draw past the pad.
     */
    public static final int BASE_PAD_COLS = 2;
    public static final int BASE_PAD_ROWS = 2;

    private ZoneLoader() {}

    /**
     * CSV parsed to rectangles in meters (or abstract units); includes overall bounding box.
     */
    private record ParsedCsv(List<ParsedLine> lines, double minX, double minY, double maxX, double maxY) {}

    /**
     * Grid size derived from meters-per-cell plus zone file bounds, and mapped zone list.
     */
    public record MeterGrid(int cols, int rows, List<ZoneDef> zones) {}

    /**
     * Load zones; CSV coordinates are meters. Grid dimensions are ceil(span / cellSizeMeters).
     */
    public static MeterGrid loadZonesMeters(String csvPath) {
        return loadZonesMeters(csvPath, DEFAULT_CELL_SIZE_METERS);
    }

    /**
     * Load zones; CSV coordinates are meters. Each cell covers {@code cellSizeMeters} per axis.
     */
    public static MeterGrid loadZonesMeters(String csvPath, double cellSizeMeters) {
        if (cellSizeMeters <= 0) {
            throw new IllegalArgumentException("cellSizeMeters must be positive: " + cellSizeMeters);
        }
        ParsedCsv csv = parseCsv(csvPath);
        double spanX = Math.max(csv.maxX - csv.minX, 1e-9);
        double spanY = Math.max(csv.maxY - csv.minY, 1e-9);
        int cols = Math.max(1, (int) Math.ceil(spanX / cellSizeMeters));
        int rows = Math.max(1, (int) Math.ceil(spanY / cellSizeMeters));

        List<ZoneDef> zones = new ArrayList<>();
        for (ParsedLine parsed : csv.lines()) {
            int zoneId = parsed.zoneId;
            double[] start = parsed.start;
            double[] end = parsed.end;

            double startX = Math.min(start[0], end[0]);
            double startY = Math.min(start[1], end[1]);
            double endX = Math.max(start[0], end[0]);
            double endY = Math.max(start[1], end[1]);

            int startCol = (int) Math.floor((startX - csv.minX) / cellSizeMeters);
            int startRow = (int) Math.floor((startY - csv.minY) / cellSizeMeters);
            int endColEx = (int) Math.ceil((endX - csv.minX) / cellSizeMeters);
            int endRowEx = (int) Math.ceil((endY - csv.minY) / cellSizeMeters);

            // Clamp to grid bounds. endColEx/endRowEx use [startCol+1, cols] as their range
            // to guarantee each zone is at least 1 cell wide/tall.
            startCol = clamp(startCol, 0, cols - 1);
            startRow = clamp(startRow, 0, rows - 1);
            endColEx = clamp(endColEx, startCol + 1, cols);
            endRowEx = clamp(endRowEx, startRow + 1, rows);

            int widthCols = Math.max(1, endColEx - startCol);
            int heightRows = Math.max(1, endRowEx - startRow);

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

        return applyBaseTopLeftPad(csv, cellSizeMeters, cols, rows, zones);
    }

    /**
     * Drops any zone 0 from the file, shifts remaining zones by the base pad, injects canonical zone 0
     * at (0,0), and expands the grid so layout stays rectangular.
     */
    private static MeterGrid applyBaseTopLeftPad(
            ParsedCsv csv, double cellSizeMeters, int cols, int rows, List<ZoneDef> zones) {
        // Remove any zone 0 from the CSV (we inject our own canonical base).
        // Shift all fire zones right/down by the pad size so zone 0 occupies the top-left corner.
        List<ZoneDef> shifted = new ArrayList<>();
        for (ZoneDef z : zones) {
            if (z.id == 0) {
                continue;
            }
            shifted.add(new ZoneDef(
                    z.id,
                    z.startCol + BASE_PAD_COLS,
                    z.startRow + BASE_PAD_ROWS,
                    z.widthCols,
                    z.heightRows,
                    z.startX,
                    z.startY,
                    z.endX,
                    z.endY,
                    z.baseColor));
        }

        int paddedCols = cols + BASE_PAD_COLS;
        int paddedRows = rows + BASE_PAD_ROWS;

        double baseEndX = csv.minX + BASE_PAD_COLS * cellSizeMeters;
        double baseEndY = csv.minY + BASE_PAD_ROWS * cellSizeMeters;
        ZoneDef base = new ZoneDef(
                0,
                0,
                0,
                BASE_PAD_COLS,
                BASE_PAD_ROWS,
                csv.minX,
                csv.minY,
                baseEndX,
                baseEndY,
                new Color(220, 220, 220));

        List<ZoneDef> withBase = new ArrayList<>();
        withBase.add(base);
        withBase.addAll(shifted);
        return new MeterGrid(paddedCols, paddedRows, withBase);
    }

    /**
     * load zones from a csv file and map to the grid (legacy: pass explicit cols/rows).
     */
    public static List<ZoneDef> loadZones(String csvPath, int cols, int rows) {
        ParsedCsv csv = parseCsv(csvPath);
        double spanX = Math.max(csv.maxX - csv.minX, 1e-9);
        double spanY = Math.max(csv.maxY - csv.minY, 1e-9);
        // Scale coordinates proportionally when CSV values exceed the explicit grid size;
        // otherwise treat them as direct cell indices.
        boolean scaleToGrid = csv.maxX > cols || csv.maxY > rows;

        List<ZoneDef> zones = new ArrayList<>();
        for (ParsedLine parsed : csv.lines()) {
            int zoneId = parsed.zoneId;
            double[] start = parsed.start;
            double[] end = parsed.end;

            double startX = Math.min(start[0], end[0]);
            double startY = Math.min(start[1], end[1]);
            double endX = Math.max(start[0], end[0]);
            double endY = Math.max(start[1], end[1]);

            int startCol;
            int startRow;
            int widthCols;
            int heightRows;
            if (scaleToGrid) {
                int endCol = (int) Math.ceil((endX - csv.minX) / spanX * cols);
                int endRow = (int) Math.ceil((endY - csv.minY) / spanY * rows);
                startCol = (int) Math.floor((startX - csv.minX) / spanX * cols);
                startRow = (int) Math.floor((startY - csv.minY) / spanY * rows);
                startCol = clamp(startCol, 0, cols - 1);
                startRow = clamp(startRow, 0, rows - 1);
                endCol = clamp(endCol, startCol + 1, cols);
                endRow = clamp(endRow, startRow + 1, rows);
                widthCols = Math.max(1, endCol - startCol);
                heightRows = Math.max(1, endRow - startRow);
            } else {
                startCol = (int) startX;
                startRow = (int) startY;
                widthCols = Math.max(1, (int) (endX - startX));
                heightRows = Math.max(1, (int) (endY - startY));
            }

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

        return zones;
    }

    private static ParsedCsv parseCsv(String csvPath) {
        List<ParsedLine> parsedLines = new ArrayList<>();

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

                ParsedLine parsed = parseLine(trimmed);
                if (parsed != null) {
                    parsedLines.add(parsed);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read zone file: " + ex.getMessage(), ex);
        }

        if (parsedLines.isEmpty()) {
            throw new RuntimeException("Zone file contains no valid zones: " + csvPath);
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (ParsedLine p : parsedLines) {
            double[] s = p.start;
            double[] e = p.end;
            double sx0 = Math.min(s[0], e[0]);
            double sx1 = Math.max(s[0], e[0]);
            double sy0 = Math.min(s[1], e[1]);
            double sy1 = Math.max(s[1], e[1]);
            minX = Math.min(minX, sx0);
            maxX = Math.max(maxX, sx1);
            minY = Math.min(minY, sy0);
            maxY = Math.max(maxY, sy1);
        }

        return new ParsedCsv(parsedLines, minX, minY, maxX, maxY);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
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
        // floorMod handles negative zone IDs gracefully (always returns a valid index).
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
