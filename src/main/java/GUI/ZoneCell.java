package GUI;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;

public class ZoneCell extends JLabel {
    private final int col, row;
    private final ZoneDef zone;
    private Color baseBg;
    private FireDroneGUI.CellState state = FireDroneGUI.CellState.EMPTY;
    private static final int THICK_BORDER = 2;
    private static final int THIN_BORDER = 1;

    public ZoneCell(int col, int row, ZoneDef zone, String initialText) {
        super(initialText, SwingConstants.LEFT);
        this.col = col;
        this.row = row;
        this.zone = zone;
        setOpaque(true);
        setFont(getFont().deriveFont(10f));
        setPreferredSize(new Dimension(36, 36));

        if (zone != null) baseBg = zone.baseColor;
        else baseBg = new Color(240,240,240);

        setBackground(baseBg);
        setState(FireDroneGUI.CellState.EMPTY);// apply appearance for default state
    }

    public void updateBorder(int gridCols, int gridRows, List<ZoneDef> zones) {
        //start with thin borders on all sides
        int top = THIN_BORDER;
        int left = THIN_BORDER;
        int bottom = THIN_BORDER;
        int right = THIN_BORDER;

        if (zone != null) {
            //check top edge of the zone
            if (row == zone.startRow) {
                ZoneDef neighbor = findZoneForCell(col, row - 1, zones);
                top = THICK_BORDER;
            }
            //check left edge of the zone
            if (col == zone.startCol) {
                ZoneDef neighbor = findZoneForCell(col - 1, row, zones);
                left = THICK_BORDER;
            }
            //check bottom edge of the zone
            if (row == zone.startRow + zone.heightRows - 1) {
                ZoneDef neighbor = findZoneForCell(col, row + 1, zones);
                bottom = THICK_BORDER;
            }
            //check right edge of the zone
            if (col == zone.startCol + zone.widthCols - 1) {
                ZoneDef neighbor = findZoneForCell(col + 1, row, zones);
                right = THICK_BORDER;
            }
        } else {
            // cell is not part of any zone
            if (col == 0) left = 2;
            if (row == 0) top = 2;
            if (col == gridCols - 1) right = 2;
            if (row == gridRows - 1) bottom = 2;
        }
        setBorder(BorderFactory.createMatteBorder(top, left, bottom, right, new Color(150,150,150)));
    }

    private ZoneDef findZoneForCell(int col, int row, List<ZoneDef> zones) {
        for (ZoneDef z : zones) {
            if (col >= z.startCol && col < z.startCol + z.widthCols &&
                    row >= z.startRow && row < z.startRow + z.heightRows) {
                return z;
            }
        }
        return null;
    }

    public void setState(FireDroneGUI.CellState newState) {
        this.state = newState;
        updateAppearance();
    }

    public FireDroneGUI.CellState getState() { return state; }

    private void updateAppearance() {
        switch (state) {
            case EMPTY:
                setBackground(baseBg);
                setFont(getFont().deriveFont(Font.PLAIN, 10f));
                setHorizontalAlignment(SwingConstants.LEFT);
                break;
            case ACTIVE_FIRE:
                setBackground(new Color(220, 80, 80));
                setText("");
                setFont(getFont().deriveFont(Font.BOLD, 11f));
                setHorizontalAlignment(SwingConstants.CENTER);
                break;
            case EXTINGUISHED:
                setBackground(new Color(120, 200, 120));
                setText("");
                setFont(getFont().deriveFont(Font.BOLD, 11f));
                setHorizontalAlignment(SwingConstants.CENTER);
                break;
            case DRONE_OUTBOUND:
                setBackground(new Color(250, 220, 110));
                setText("D(n)");
                setFont(getFont().deriveFont(Font.BOLD, 11f));
                setHorizontalAlignment(SwingConstants.CENTER);
                break;
            case DRONE_EXTINGUISHED:
                setBackground(new Color(120, 200, 120));
                setText("D(n)");
                setFont(getFont().deriveFont(Font.BOLD, 11f));
                setHorizontalAlignment(SwingConstants.CENTER);
                break;
            case DRONE_RETURNING:
                setBackground(new Color(210, 160, 240));
                setText("D(n)");
                setFont(getFont().deriveFont(Font.BOLD, 11f));
                setHorizontalAlignment(SwingConstants.CENTER);
                break;
            default:
                setBackground(baseBg);
                break;
        }
    }

    public int getCol() { return col; }
    public int getRow() { return row; }
    public ZoneDef getZone() { return zone; }
}