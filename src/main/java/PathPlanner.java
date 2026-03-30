import java.util.ArrayList;
import java.util.List;

/**
 * PathPlanner.java
 *
 * Stateless utility that computes cell-by-cell paths using Bresenham's line algorithm,
 * zone center calculations, and distance metrics.
 */
public class PathPlanner {

    private PathPlanner() {}

    /**
     * Compute a cell-by-cell path from (startCol,startRow) to (endCol,endRow)
     * using Bresenham's line algorithm.
     * @return List of int[2] {col, row} for each cell in the path (inclusive of both endpoints)
     */
    public static List<int[]> computePath(int startCol, int startRow, int endCol, int endRow) {
        List<int[]> path = new ArrayList<>();

        int dx = Math.abs(endCol - startCol);
        int dy = Math.abs(endRow - startRow);
        int sx = startCol < endCol ? 1 : -1;
        int sy = startRow < endRow ? 1 : -1;
        int err = dx - dy;

        int col = startCol;
        int row = startRow;

        while (true) {
            path.add(new int[]{col, row});

            if (col == endCol && row == endRow) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                col += sx;
            }
            if (e2 < dx) {
                err += dx;
                row += sy;
            }
        }

        return path;
    }

    /**
     * Compute the target cell for a zone.
     * Matches the seeded visual randomization of the active fire hotspot in TacticalMapPanel.
     * @return int[2] {col, row}
     */
    public static int[] zoneCenterCell(ZoneDef zone) {
        if (zone.id == 0) {
            // Base zone gets geometric center
            return new int[]{
                zone.startCol + zone.widthCols / 2,
                zone.startRow + zone.heightRows / 2
            };
        }

        // Perfectly sync drone target coordinate with visual fire spot drawn in TacticalMapPanel
        java.util.Random spotRand = new java.util.Random(zone.id * 738L);
        
        int pxW = zone.widthCols * Theme.ZONE_PIXEL_SIZE;
        int pxH = zone.heightRows * Theme.ZONE_PIXEL_SIZE;
        int margin = Math.min(pxW, pxH) / 4; 
        
        int pxX = zone.startCol * Theme.ZONE_PIXEL_SIZE;
        int pxY = zone.startRow * Theme.ZONE_PIXEL_SIZE;
        
        int spotX = pxX + margin + spotRand.nextInt(Math.max(1, pxW - 2 * margin));
        int spotY = pxY + margin + spotRand.nextInt(Math.max(1, pxH - 2 * margin));
        
        return new int[]{
            spotX / Theme.ZONE_PIXEL_SIZE,
            spotY / Theme.ZONE_PIXEL_SIZE
        };
    }

    /**
     * Euclidean distance between two cells.
     */
    public static double distance(int col1, int row1, int col2, int row2) {
        int dc = col1 - col2;
        int dr = row1 - row2;
        return Math.sqrt(dc * dc + dr * dr);
    }

    /**
     * Determine which zones a path passes through.
     * @return ordered list of zone IDs traversed (no duplicates in sequence)
     */
    public static List<Integer> zonesOnPath(List<int[]> path, List<ZoneDef> zones) {
        List<Integer> result = new ArrayList<>();
        int lastZoneId = -1;

        for (int[] cell : path) {
            int col = cell[0];
            int row = cell[1];
            for (ZoneDef z : zones) {
                if (col >= z.startCol && col < z.startCol + z.widthCols &&
                        row >= z.startRow && row < z.startRow + z.heightRows) {
                    if (z.id != lastZoneId) {
                        result.add(z.id);
                        lastZoneId = z.id;
                    }
                    break;
                }
            }
        }

        return result;
    }
}
