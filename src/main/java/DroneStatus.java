/**
 * DroneStatus.java
 *
 * An immutable snapshot of a drone's status.
 */
public class DroneStatus {
    private final int droneId;
    private final DroneState state;
    private final int zoneId; // Current zone or target zone
    private final int remainingLiters;
    private final int currentCol;
    private final int currentRow;

    public DroneStatus(int droneId, DroneState state, int zoneId, int remainingLiters, int currentCol, int currentRow) {
        this.droneId = droneId;
        this.state = state;
        this.zoneId = zoneId;
        this.remainingLiters = remainingLiters;
        this.currentCol = currentCol;
        this.currentRow = currentRow;
    }

    /**
     * Backward-compatible constructor (defaults col/row to 0,0).
     */
    public DroneStatus(int droneId, DroneState state, int zoneId, int remainingLiters) {
        this(droneId, state, zoneId, remainingLiters, 0, 0);
    }

    public int getDroneId() { return droneId; }
    public DroneState getState() { return state; }
    public int getZoneId() { return zoneId; }
    public int getRemainingLiters() { return remainingLiters; }
    public int getCurrentCol() { return currentCol; }
    public int getCurrentRow() { return currentRow; }

    @Override
    public String toString() {
        return String.format("Drone %d [%s] Zone:%d Liters:%d Pos:(%d,%d)", droneId, state, zoneId, remainingLiters, currentCol, currentRow);
    }
}
