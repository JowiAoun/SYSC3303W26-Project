import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
/**
 * Scheduler.java
 *
 * State-machine-driven coordinator.
 * It buffers incoming fire events and assigns them to drones on request.
 * Supports multiple drones, each tracked independently via maps.
 */
public class Scheduler implements Runnable {
    private final MessageBuffer fromSubsystems;
    private final MessageBuffer toFireIncident;
    private final MessageBuffer toDrone;
    private final DatagramSocket socket;
    private final Queue<FireEvent> pending = new ArrayDeque<>();

    private int totalEvents = 0;
    private int completed = 0;
    private boolean fireIncidentDone = false;
    private boolean shutdownSentToFire = false;

    // Track multiple drones' statuses, addresses, and ports.
    private final Map<Integer, DroneStatus> droneStatuses = new HashMap<>();
    private final Map<Integer, InetAddress> droneAddresses = new HashMap<>();
    private final Map<Integer, Integer> dronePorts = new HashMap<>();
    private final Set<Integer> shutdownSentToDrones = new HashSet<>();

    private final FireDroneGUI gui;

    // State machine
    private SchedulerState currentState = SchedulerState.IDLE;

    /**
     * @param fromSubsystems shared buffer of incoming messages
     * @param toFireIncident buffer to send acknowledgments back
     * @param toDrone buffer to send assignments to the drone
     * @param gui reference to the main GUI window
     */
    public Scheduler(MessageBuffer fromSubsystems,
                     MessageBuffer toFireIncident,
                     MessageBuffer toDrone,
                     FireDroneGUI gui) throws SocketException {
        this.fromSubsystems = fromSubsystems;
        this.toFireIncident = toFireIncident;
        this.toDrone = toDrone;
        this.gui = gui;
        this.socket = new DatagramSocket(SwarmNetwork.SCHEDULER_PORT);
    }

    public int getTotalEvents() { return totalEvents; }

    SchedulerState getCurrentState() { return currentState; }

    public DatagramSocket getSocket() { return socket; }

    public void closeSocket() { socket.close(); }

    @Override
    public void run() {
        System.out.println("[Scheduler] Started.");

        try {
            while (currentState != SchedulerState.SHUTTING_DOWN) {
                SwarmNetwork.ReceivedMessage msg = receiveSubsystemMessage();
                handleIncomingMessage(msg);

                // Try to dispatch to ALL available idle drones
                while (canDispatchPendingEvent()) {
                    dispatchPendingEvent();
                }
                if (!canDispatchPendingEvent()) {
                    checkAndSendReturnToBase();
                }

                sendShutdownToDroneIfComplete();
                sendShutdownToFireIfComplete();
                evaluateTransition();
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Scheduler] Interrupted.", e);
        }

        System.out.println("[Scheduler] Finished.");
    }

    // ── State machine ───────────────────────────────────────────────

    /**
     * Centralized transition logic. Called after every message is processed.
     * Determines next state based on: drone statuses, pending queue, completion flags.
     */
    void evaluateTransition() {
        if (shouldTerminate()) {
            currentState = SchedulerState.SHUTTING_DOWN;
            System.out.println("[Scheduler] State -> SHUTTING_DOWN");
            return;
        }

        boolean anyDroneBusy = false;
        for (DroneStatus s : droneStatuses.values()) {
            if (s.getState() != DroneState.IDLE) {
                anyDroneBusy = true;
                break;
            }
        }
        boolean hasPending = !pending.isEmpty();

        if (hasPending && anyDroneBusy) {
            currentState = SchedulerState.AWAITING_DRONE;
        } else if (anyDroneBusy) {
            currentState = SchedulerState.DRONE_ACTIVE;
        } else {
            currentState = SchedulerState.IDLE;
        }
    }

    // ── Message processing ──────────────────────────────────────────

    /**
     * Reads one message from the shared buffer.
     */
    SwarmNetwork.ReceivedMessage receiveSubsystemMessage() throws Exception {
        return SwarmNetwork.receiveMessageWithSource(socket);
    }

    /**
     * Handles a message from either subsystem.
     */
    void handleIncomingMessage(SwarmNetwork.ReceivedMessage rm) throws Exception {
        Message msg = rm.getMessage();
        switch (msg.getType()) {
            case FIRE_EVENT:
            case SHUTDOWN:
                handleFireIncidentMessage(msg);
                break;
            case DRONE_READY:
            case DRONE_COMPLETED:
            case DRONE_STATUS_UPDATE:
                handleDroneMessage(msg, rm.getAddress(), rm.getPort());
                break;
            default:
                System.out.println("[Scheduler] Unknown message type: " + msg.getType());
        }
    }

    /**
     * Find an idle drone from the registered drones.
     * @return the drone ID of an idle drone, or null if none available
     */
    private Integer findIdleDrone() {
        for (Map.Entry<Integer, DroneStatus> entry : droneStatuses.entrySet()) {
            if (entry.getValue().getState() == DroneState.IDLE) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Checks if the scheduler can dispatch a pending event.
     */
    boolean canDispatchPendingEvent() {
        return !pending.isEmpty() && findIdleDrone() != null;
    }

    /**
     * Dispatches the next pending event to an idle drone.
     */
    void dispatchPendingEvent() throws Exception {
        Integer droneId = findIdleDrone();
        if (droneId == null) return;

        FireEvent next = pending.peek();
        if (next == null) return;

        DroneStatus status = droneStatuses.get(droneId);
        InetAddress addr = droneAddresses.get(droneId);
        Integer port = dronePorts.get(droneId);

        // If drone is at a remote zone (not base), check if it has enough agent
        if (status.getZoneId() != 0) {
            if (status.getRemainingLiters() < next.getRequiredLiters()) {
                 // Not enough agent for this task — return to base first.
                 sendReturnToBase(droneId);
                 return;
            }
        }

        // If we get here, we can dispatch
        if (addr != null && port != null && port != -1) {
            next = pending.poll(); // remove from queue
            SwarmNetwork.sendMessage(socket, addr, port, Message.droneAssignment(next), "[Scheduler]", "sent Assignment", "to Drone " + droneId);
        } else {
            System.err.println("[Scheduler] - ERROR: Could not send Message to Drone " + droneId + " - addr: " + addr + ", port: " + port);
            return;
        }

        // Optimistically update status to prevent double dispatch
        droneStatuses.put(droneId, new DroneStatus(
                droneId,
                DroneState.EN_ROUTE,
                next.getZoneId(),
                status.getRemainingLiters()
        ));

        String dispatchMsg = "[Scheduler] Dispatched to Drone " + droneId + ": " + next;
        System.out.println(dispatchMsg);
        if (gui != null) {
            gui.appendEvent(dispatchMsg);
        }
    }

    /**
     * Sends a command to a specific drone to return to base.
     */
    void sendReturnToBase(int droneId) throws Exception {
        InetAddress addr = droneAddresses.get(droneId);
        Integer port = dronePorts.get(droneId);

        if (addr != null && port != null && port != -1) {
            SwarmNetwork.sendMessage(socket, addr, port, Message.droneReturnToBase(), "[Scheduler]", "sent RTB", "to Drone " + droneId);
        } else {
            System.err.println("[Scheduler] - ERROR: Could not send RTB to Drone " + droneId + " - addr: " + addr + ", port: " + port);
            return;
        }

        // Optimistically update status
        DroneStatus current = droneStatuses.get(droneId);
        droneStatuses.put(droneId, new DroneStatus(
                droneId,
                DroneState.RETURNING,
                0,
                current != null ? current.getRemainingLiters() : 0
        ));

        String rtbMsg = "[Scheduler] Commanding Drone " + droneId + " to Return to Base.";
        System.out.println(rtbMsg);
        if (gui != null) {
            gui.appendEvent(rtbMsg);
        }
    }

    /**
     * Backward-compatible sendReturnToBase that sends RTB to any idle drone.
     * Used when the caller doesn't specify which drone.
     */
    void sendReturnToBase() throws Exception {
        Integer droneId = findIdleDrone();
        if (droneId != null) {
            sendReturnToBase(droneId);
        }
    }

    /**
     * Checks if any drone should return to base (Idle at remote zone + no pending work).
     */
    void checkAndSendReturnToBase() throws Exception {
        for (Map.Entry<Integer, DroneStatus> entry : droneStatuses.entrySet()) {
            DroneStatus s = entry.getValue();
            if (s.getState() == DroneState.IDLE && s.getZoneId() != 0 && pending.isEmpty()) {
                sendReturnToBase(entry.getKey());
            }
        }
    }

    /**
     * Sends shutdown to all registered Drone subsystems if conditions are met.
     */
    void sendShutdownToDroneIfComplete() throws Exception {
        if (!isProcessingComplete()) return;
        for (Map.Entry<Integer, InetAddress> entry : droneAddresses.entrySet()) {
            int droneId = entry.getKey();
            if (!shutdownSentToDrones.contains(droneId)) {
                Integer port = dronePorts.get(droneId);
                if (port != null && port != -1) {
                    SwarmNetwork.sendMessage(socket, entry.getValue(), port,
                        Message.shutdown(), "[Scheduler]", "sent Shutdown", "to Drone " + droneId);
                } else {
                    System.err.println("[Scheduler] - ERROR: Could not send Shutdown to Drone " + droneId);
                }
                shutdownSentToDrones.add(droneId);
                System.out.println("[Scheduler] Sent shutdown to Drone " + droneId + ".");
            }
        }
    }

    /**
     * Sends shutdown to Fire Incident subsystem if conditions are met.
     */
    void sendShutdownToFireIfComplete() throws Exception {
        if (isProcessingComplete() && !shutdownSentToFire) {
            if (InetAddress.getByName(SwarmNetwork.LOCALHOST) != null) {
                SwarmNetwork.sendMessage(socket, InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.FIS_PORT, Message.shutdown(), "[Scheduler]", "sent Shutdown", "to FireIncident");
            } else {
                System.err.println("[Scheduler] - ERROR: Could not send Message to FireIncidentSubsystem - FIS Address: " + InetAddress.getByName(SwarmNetwork.LOCALHOST) + ", FIS Port: " + SwarmNetwork.FIS_PORT);
            }

            shutdownSentToFire = true;
            System.out.println("[Scheduler] Sent shutdown to Fire Incident.");
        }
    }

    /**
     * Checks whether all events are complete and queues are empty.
     */
    boolean isProcessingComplete() {
        if (!fireIncidentDone || !pending.isEmpty() || completed != totalEvents) return false;
        if (droneStatuses.isEmpty()) return false;
        for (DroneStatus s : droneStatuses.values()) {
            if (s.getState() != DroneState.IDLE) return false;
        }
        return true;
    }

    /**
     * Checks whether the scheduler should terminate.
     */
    boolean shouldTerminate() {
        return isProcessingComplete()
            && !droneAddresses.isEmpty()
            && shutdownSentToDrones.size() >= droneAddresses.size()
            && shutdownSentToFire;
    }

    /**
     * Handle messages from Fire Incident subsystem.
     */
    private void handleFireIncidentMessage(Message message) throws InterruptedException {
        if (message.getType() == Message.Type.FIRE_EVENT) {
            FireEvent event = message.getEvent();
            pending.add(event);
            totalEvents++;
            String msg = "[Scheduler] Received event: " + event;
            System.out.println(msg);

            // Update GUI: New Fire with severity
            if (gui != null) {
                gui.setZoneFire(event.getZoneId(), true, event.getSeverity());
                gui.appendEvent(msg);
            }

        } else if (message.getType() == Message.Type.SHUTDOWN) {
            fireIncidentDone = true;
            String inputCompleteMsg = "[Scheduler] Fire Incident input complete.";
            System.out.println(inputCompleteMsg);
            if (gui != null) {
                gui.appendEvent(inputCompleteMsg);
            }
        }
    }

    /**
     * Handle messages from the Drone subsystem.
     */
    private void handleDroneMessage(Message message, InetAddress addr, int port) throws Exception {
        switch (message.getType()) {
            case DRONE_READY:
                String readyMsg = "[Scheduler] Received Drone Ready Signal";
                System.out.println(readyMsg);
                if (gui != null) {
                    gui.appendEvent(readyMsg);
                }
                break;

            case DRONE_STATUS_UPDATE:
                DroneStatus status = message.getStatus();
                int droneId = status.getDroneId();
                String statusMsg = "[Scheduler] Drone Status Update: " + status;
                System.out.println(statusMsg);

                droneStatuses.put(droneId, status);
                droneAddresses.put(droneId, addr);
                dronePorts.put(droneId, port);

                if (gui != null) {
                    gui.updateDroneStatus(status);
                    gui.appendEvent(statusMsg);
                }
                break;

            case DRONE_COMPLETED:
                completed++;
                if (InetAddress.getByName(SwarmNetwork.LOCALHOST) != null) {
                    SwarmNetwork.sendMessage(socket, InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.FIS_PORT, Message.fireAck(message.getEvent()), "[Scheduler]", "sent FireAck", "to FireIncident");
                } else {
                    System.err.println("[Scheduler] - ERROR: Could not send Message to FireIncidentSubsystem - FIS Address: " + InetAddress.getByName(SwarmNetwork.LOCALHOST) + ", FIS Port: " + SwarmNetwork.FIS_PORT);
                }

                String completedMsg = "[Scheduler] Completion ack forwarded: " + message.getEvent();
                System.out.println(completedMsg);

                // Update GUI: Fire Extinguished
                if (gui != null) {
                    gui.setZoneFire(message.getEvent().getZoneId(), false);
                    gui.appendEvent(completedMsg);
                }
                break;
            default:
                break;
        }
    }
}
