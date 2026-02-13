import java.awt.Color;

/**
 * ZoneDef.java
 *
 * Data object defining a rectangular fire zone.
 * Includes grid layout, world coordinates, and visual theme.
 */
public class ZoneDef {
    public final int id;
    public final int startCol, startRow;
    public final int widthCols, heightRows;
    public final double startX, startY;
    public final double endX, endY;
    public final Color baseColor;

    public ZoneDef(int id,
                   int startCol,
                   int startRow,
                   int widthCols,
                   int heightRows,
                   double startX,
                   double startY,
                   double endX,
                   double endY,
                   Color baseColor) {
        this.id = id;
        this.startCol = startCol;
        this.startRow = startRow;
        this.widthCols = widthCols;
        this.heightRows = heightRows;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.baseColor = baseColor;
    }

    public double getCenterX() {
        return (startX + endX) / 2.0;
    }

    public double getCenterY() {
        return (startY + endY) / 2.0;
    }
}