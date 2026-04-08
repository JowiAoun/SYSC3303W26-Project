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

        // Bresenham variables: dx/dy = axis deltas, sx/sy = step direction (+1 or -1),
        // err = accumulated error used to decide when to step diagonally.
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
     * Compute the target cell for a zone — the top-left corner (startCol, startRow).
     * This matches where fires and zone labels are rendered on the GUI.
     * @return int[2] {col, row}
     */
    public static int[] zoneCenterCell(ZoneDef zone) {
        return new int[]{zone.startCol, zone.startRow};
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
            // Find the first zone containing this cell. Break after the first match
            // because zones don't overlap — checking further would be wasted work.
            for (ZoneDef z : zones) {
                if (col >= z.startCol && col < z.startCol + z.widthCols &&
                        row >= z.startRow && row < z.startRow + z.heightRows) {
                    // Only record when zone changes to avoid consecutive duplicates.
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
