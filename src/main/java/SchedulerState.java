/**
 * SchedulerState.java
 *
 * Represents the various states the Scheduler can be in.
 */
public enum SchedulerState {
    IDLE,            // No pending events, drone idle or not yet connected
    AWAITING_DRONE,  // Has pending fire events but drone is busy
    DRONE_ACTIVE,    // Drone is executing a task
    SHUTTING_DOWN    // All events processed, sending shutdown signals
}
