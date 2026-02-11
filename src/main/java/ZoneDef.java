import java.awt.Color;

/**
 * ZoneDef.java
 *
 * Data object defining a rectangular fire zone.
 * Includes dimensions, position, and visual theme.
 */
public class ZoneDef {
    public final int id;
    public final int startCol, startRow;
    public final int widthCols, heightRows;
    public final Color baseColor;

    public ZoneDef(int id, int startCol, int startRow, int widthCols, int heightRows, Color baseColor) {
        this.id = id;
        this.startCol = startCol;
        this.startRow = startRow;
        this.widthCols = widthCols;
        this.heightRows = heightRows;
        this.baseColor = baseColor;
    }
}