/**
 * Represents the various fault types the system can have
 */
public enum FaultType {
    NONE,
    STUCK_MID_FLIGHT,
    NOZZLE_JAM,
    PACKET_LOSS,
    CORRUPTED_MESSAGE
}