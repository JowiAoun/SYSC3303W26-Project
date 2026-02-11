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

    public DroneStatus(int droneId, DroneState state, int zoneId, int remainingLiters) {
        this.droneId = droneId;
        this.state = state;
        this.zoneId = zoneId;
        this.remainingLiters = remainingLiters;
    }

    public int getDroneId() { return droneId; }
    public DroneState getState() { return state; }
    public int getZoneId() { return zoneId; }
    public int getRemainingLiters() { return remainingLiters; }

    @Override
    public String toString() {
        return String.format("Drone %d [%s] Zone:%d Liters:%d", droneId, state, zoneId, remainingLiters);
    }
}
