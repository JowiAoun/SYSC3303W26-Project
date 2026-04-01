import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private static final int EXPECTED_ARRIVAL_TIME = 10000;//time before assume drone is stuck
    private static final long DISPATCH_COOLDOWN_MS = 500; // stagger dispatches by 0.5s
    private final DatagramSocket socket;
    private final Queue<FireEvent> pending = new ArrayDeque<>();
    private long lastDispatchTime = 0;

    private int totalEvents = 0;
    private int completed = 0;
    private boolean fireIncidentDone = false;
    private boolean shutdownSentToFire = false;

    // Track multiple drones' statuses, addresses, and ports.
    private final Map<Integer, DroneStatus> droneStatuses = new HashMap<>();
    private final Map<Integer, InetAddress> droneAddresses = new HashMap<>();
    private final Map<Integer, Integer> dronePorts = new HashMap<>();
    private final Set<Integer> shutdownSentToDrones = new HashSet<>();
    private final Map<Integer, FireEvent> activeAssignments = new HashMap<>();
    private final Map<Integer, Long> assignmentDeadlines = new HashMap<>();
    private final Set<Integer> hardFaultedDrones = new HashSet<>();     // drones with permanent faults (returning to base)

    private final FireDroneGUI gui;
    private final List<ZoneDef> zones;
    // Track Fire Incident sender for replies/acknowledgments.
    private InetAddress fireIncidentAddress;
    private Integer fireIncidentPort;

    // State machine
    private SchedulerState currentState = SchedulerState.IDLE;

    /**
     * @param gui reference to the main GUI window
     */
    public Scheduler(FireDroneGUI gui) throws SocketException {
        this(gui, SwarmNetwork.SCHEDULER_PORT, "./src/main/resources/data/zones.csv");
    }

    /**
     * @param schedulerPort UDP port to bind locally
     * @param zonesPath path to zones CSV
     */
    public Scheduler(FireDroneGUI gui,
                     int schedulerPort,
                     String zonesPath) throws SocketException {
        this.gui = gui;
        this.socket = new DatagramSocket(schedulerPort);
        this.socket.setSoTimeout(500);
        this.zones = ZoneLoader.loadZones(zonesPath, 200, 200);
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
                try {
                    SwarmNetwork.ReceivedMessage msg = receiveSubsystemMessage();
                    handleIncomingMessage(msg);
                } catch (SocketTimeoutException e) {
                    //
                } catch (IllegalArgumentException e) {
                    System.out.println("[Scheduler] Dropped corrupted packet: " + e.getMessage());
                }

                checkForTimedOutDrones();

                // Burst dispatch multiple drones if pending fires exist and drones are idle
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
                handleFireIncidentMessage(msg, rm.getAddress(), rm.getPort());
                break;
            case DRONE_READY:
            case DRONE_COMPLETED:
            case DRONE_STATUS_UPDATE:
                handleDroneMessage(msg, rm.getAddress(), rm.getPort());
                break;
            case FAULT_INJECTION:
                handleFaultInjection(msg);
                break;
            default:
                System.out.println("[Scheduler] Unknown message type: " + msg.getType());
        }
    }

    /**
     * Find an idle drone from the registered drones (arbitrary order).
     * Kept as fallback for sendReturnToBase() and checkAndSendReturnToBase().
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
     * Find the closest idle drone to a target zone using Euclidean distance.
     * @return the drone ID of the closest idle drone, or null if none available
     */
    private Integer findClosestIdleDrone(FireEvent event) {
        ZoneDef targetZone = getSchedulerZoneById(event.getZoneId());
        if (targetZone == null) return findIdleDrone(); // fallback

        int[] targetCenter = PathPlanner.fireTargetCell(targetZone, event.getTime());
        Integer bestDrone = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<Integer, DroneStatus> entry : droneStatuses.entrySet()) {
            DroneStatus ds = entry.getValue();
            if (ds.getState() != DroneState.IDLE) continue;

            double dist = PathPlanner.distance(
                    ds.getCurrentCol(), ds.getCurrentRow(),
                    targetCenter[0], targetCenter[1]);

            if (dist < bestDistance) {
                bestDistance = dist;
                bestDrone = entry.getKey();
            }
        }
        return bestDrone;
    }

    /**
     * Look up a zone by ID from the loaded zone list.
     */
    private ZoneDef getSchedulerZoneById(int id) {
        for (ZoneDef z : zones) {
            if (z.id == id) return z;
        }
        return null;
    }

    /**
     * Checks if the scheduler can dispatch a pending event.
     */
    boolean canDispatchPendingEvent() {
        if (pending.isEmpty()) return false;
        return findClosestIdleDrone(pending.peek()) != null;
    }

    /**
     * Dispatches the next pending event to the closest idle drone.
     */
    void dispatchPendingEvent() throws Exception {
        FireEvent next = pending.peek();
        if (next == null) return;

        Integer droneId = findClosestIdleDrone(next);
        if (droneId == null) return;

        DroneStatus status = droneStatuses.get(droneId);
        InetAddress addr = droneAddresses.get(droneId);
        Integer port = dronePorts.get(droneId);

        // Check if drone has any agent left — partial drops are OK (multi-trip design)
        if (status.getRemainingLiters() <= 0) {
            // Empty tank — send back to base for refill
            sendReturnToBase(droneId);
            // Optimistically update status to prevent infinite assignment cycling THIS tick!
            droneStatuses.put(droneId, new DroneStatus(
                    droneId,
                    DroneState.RETURNING,
                    status.getZoneId(),
                    status.getRemainingLiters(),
                    status.getCurrentCol(),
                    status.getCurrentRow()
            ));
            return;
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
                status.getRemainingLiters(),
                status.getCurrentCol(),
                status.getCurrentRow()
        ));
        activeAssignments.put(droneId, next);
        assignmentDeadlines.put(droneId, System.currentTimeMillis() + EXPECTED_ARRIVAL_TIME);

        String dispatchMsg = "[Scheduler] Dispatched to Drone " + droneId + ": " + next;
        System.out.println(dispatchMsg);
        if (gui != null) {
            gui.appendEvent(dispatchMsg);
        }

        // If this drone can't fully cover the fire, re-enqueue so another drone assists
        if (status.getRemainingLiters() < next.getRequiredLiters()) {
            pending.add(next.withoutFault());
            System.out.println("[Scheduler] Fire needs " + next.getRequiredLiters() + "L but Drone " + droneId + " only has " + status.getRemainingLiters() + "L — requesting backup.");
        }
    }

    /**
     * Detect the drones that didn't reach their destination in time.
     */
    public void checkForTimedOutDrones() {
        long now = System.currentTimeMillis();

        for (Integer droneId : new HashSet<>(assignmentDeadlines.keySet())) {
            Long deadline = assignmentDeadlines.get(droneId);
            DroneStatus status = droneStatuses.get(droneId);

            if (status.getState() != DroneState.EN_ROUTE || now <= deadline) {
                continue;
            }

            FireEvent interrupted = activeAssignments.remove(droneId);
            assignmentDeadlines.remove(droneId);

            pending.add(interrupted.withoutFault());

            DroneStatus faultedStatus = new DroneStatus(
                    droneId,
                    DroneState.FAULTED,
                    status.getZoneId(),
                    status.getRemainingLiters(),
                    status.getCurrentCol(),
                    status.getCurrentRow(),
                    FaultType.STUCK_MID_FLIGHT
            );
            droneStatuses.put(droneId, faultedStatus);

            String faultMsg = "[Scheduler] Drone " + droneId + " timed out while travelling, event requeued.";
            System.out.println(faultMsg);
            if (gui != null) {
                gui.updateDroneStatus(faultedStatus);
                gui.appendEvent(faultMsg);
            }
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
                current != null ? current.getRemainingLiters() : 0,
                current != null ? current.getCurrentCol() : 0,
                current != null ? current.getCurrentRow() : 0
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
            sendFireIncidentMessage(Message.shutdown(), "sent Shutdown");
            shutdownSentToFire = true;
            System.out.println("[Scheduler] Sent shutdown to Fire Incident.");
        }
    }

    /**
     * Sends a message to the Fire Incident subsystem using last known address/port.
     * Falls back to default localhost + FIS port if not yet seen.
     */
    private void sendFireIncidentMessage(Message msg, String action) {
        try {
            InetAddress addr = fireIncidentAddress != null
                    ? fireIncidentAddress
                    : InetAddress.getByName(SwarmNetwork.LOCALHOST);
            int port = fireIncidentPort != null ? fireIncidentPort : SwarmNetwork.FIS_PORT;
            SwarmNetwork.sendMessage(socket, addr, port, msg, "[Scheduler]", action, "to FireIncident");
        } catch (Exception e) {
            System.err.println("[Scheduler] - ERROR: Could not send Message to FireIncidentSubsystem - " + e.getMessage());
        }
    }

    /**
     * Checks whether all events are complete and queues are empty.
     */
    boolean isProcessingComplete() {
        // Infinite mode enabled: bypass completion to keep simulation alive natively
        return false;
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
     * Handles a FAULT_INJECTION message received via UDP (from the GUI).
     * Extracts droneId, faultType, and duration from the FireEvent payload,
     * then sends RTB commands to the targeted drone over UDP.
     */
    private void handleFaultInjection(Message message) {
        FireEvent payload = message.getEvent();
        if (payload == null) {
            System.out.println("[Scheduler] Received FAULT_INJECTION with no payload.");
            return;
        }

        int droneId = payload.getZoneId();  // droneId is encoded in zoneId
        FaultType faultType = payload.getFaultType();
        long durationMs = payload.getFaultDelayTime();
        DroneStatus current = droneStatuses.get(droneId);

        if (current == null) {
            System.out.println("[Scheduler] Cannot inject fault: Drone " + droneId + " not registered.");
            return;
        }

        // Requeue the drone's current assignment if any
        FireEvent interrupted = activeAssignments.remove(droneId);
        assignmentDeadlines.remove(droneId);
        if (interrupted != null) {
            pending.add(interrupted.withoutFault());
        }

        DroneStatus faultedStatus = new DroneStatus(
                droneId, DroneState.FAULTED,
                current.getZoneId(), current.getRemainingLiters(),
                current.getCurrentCol(), current.getCurrentRow(),
                faultType
        );
        droneStatuses.put(droneId, faultedStatus);

        if (isHardFault(faultType)) {
            hardFaultedDrones.add(droneId);
            try {
                InetAddress dAddr = droneAddresses.get(droneId);
                Integer dPort = dronePorts.get(droneId);
                if (dAddr != null && dPort != null) {
                    SwarmNetwork.sendMessage(socket, dAddr, dPort,
                            Message.droneReturnToBase(), "[Scheduler]",
                            "sent RTB (hard fault)", "to Drone " + droneId);
                }
            } catch (Exception e) {
                System.out.println("[Scheduler] Could not send RTB to hard-faulted Drone " + droneId);
            }
        } else {
            try {
                InetAddress dAddr = droneAddresses.get(droneId);
                Integer dPort = dronePorts.get(droneId);
                if (dAddr != null && dPort != null) {
                    FireEvent faultPayload = new FireEvent(
                            "00:00:00", 0,
                            FireEvent.EventType.FIRE_DETECTED,
                            FireEvent.Severity.LOW,
                            faultType, durationMs
                    );
                    SwarmNetwork.sendMessage(socket, dAddr, dPort,
                            Message.droneReturnToBase(faultPayload), "[Scheduler]",
                            "sent RTB+fault", "to Drone " + droneId);
                }
            } catch (Exception e) {
                System.out.println("[Scheduler] Could not send fault RTB to Drone " + droneId);
            }
        }

        String msg = "[Scheduler] Fault injected on Drone " + droneId + ": " + faultType;
        System.out.println(msg);
        if (gui != null) {
            gui.updateDroneStatus(faultedStatus);
            gui.appendEvent(msg);
        }
    }

    private boolean isHardFault(FaultType faultType) {
        return faultType == FaultType.NOZZLE_JAM;
    }

    /**
     * Handle messages from Fire Incident subsystem.
     */
    private void handleFireIncidentMessage(Message message, InetAddress addr, int port) throws InterruptedException {
        fireIncidentAddress = addr;
        fireIncidentPort = port;
        if (message.getType() == Message.Type.FIRE_EVENT) {
            FireEvent event = message.getEvent();
            pending.add(event);
            totalEvents++;
            String msg = "[Scheduler] Received event: " + event;
            System.out.println(msg);

            // Update GUI: New Fire with severity
            if (gui != null) {
                if (gui != null) gui.addFireEvent(event);
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
            case DRONE_READY: {
                String readyMsg = "[Scheduler] Received Drone Ready Signal";
                System.out.println(readyMsg);
                if (gui != null) {
                    gui.appendEvent(readyMsg);
                }
                break;
            }

            case DRONE_STATUS_UPDATE: {
                DroneStatus status = message.getStatus();
                int droneId = status.getDroneId();

                // Hard-faulted drones: accept position updates but override state to FAULTED
                if (hardFaultedDrones.contains(droneId)) {
                    // If drone has reached base and is idle, remove it permanently
                    if (status.getState() == DroneState.IDLE && status.getZoneId() == 0) {
                        hardFaultedDrones.remove(droneId);
                        droneStatuses.remove(droneId);
                        droneAddresses.remove(droneId);
                        dronePorts.remove(droneId);
                        shutdownSentToDrones.add(droneId);

                        // Send final faulted-at-base status to GUI (position 0,0 = base)
                        DroneStatus finalStatus = new DroneStatus(
                                droneId, DroneState.FAULTED, 0, status.getRemainingLiters(),
                                0, 0, FaultType.NOZZLE_JAM
                        );
                        String retiredMsg = "[Scheduler] Drone " + droneId + " returned to base and permanently retired.";
                        System.out.println(retiredMsg);
                        if (gui != null) {
                            gui.updateDroneStatus(finalStatus);
                            gui.appendEvent(retiredMsg);
                        }
                    } else {
                        // Override state to FAULTED but use real position
                        DroneStatus overridden = new DroneStatus(
                                droneId, DroneState.FAULTED, status.getZoneId(),
                                status.getRemainingLiters(), status.getCurrentCol(),
                                status.getCurrentRow(), FaultType.NOZZLE_JAM
                        );
                        droneStatuses.put(droneId, overridden);
                        droneAddresses.put(droneId, addr);
                        dronePorts.put(droneId, port);
                        if (gui != null) {
                            gui.updateDroneStatus(overridden);
                        }
                    }
                    break;
                }

                String statusMsg = "[Scheduler] Drone Status Update: " + status;
                System.out.println(statusMsg);

                droneStatuses.put(droneId, status);
                droneAddresses.put(droneId, addr);
                dronePorts.put(droneId, port);

                if (status.getState() == DroneState.EXTINGUISHING || status.getState() == DroneState.IDLE) {
                    assignmentDeadlines.remove(droneId);
                }

                if (status.getState() == DroneState.FAULTED) {
                    FireEvent interrupted = activeAssignments.remove(droneId);
                    assignmentDeadlines.remove(droneId);

                    if (interrupted != null) {
                        pending.add(interrupted.withoutFault());
                    }

                    if (isHardFault(status.getFaultType())) {
                        droneStatuses.remove(droneId);
                        droneAddresses.remove(droneId);
                        dronePorts.remove(droneId);
                        shutdownSentToDrones.add(droneId);

                        String msg = "[Scheduler] Drone " + droneId + " removed from service due to hard fault: " + status.getFaultType();
                        System.out.println(msg);
                        if (gui != null) {
                            gui.appendEvent(msg);
                        }
                    }
                } else if (status.getState() == DroneState.RETURNING) {
                    FireEvent incomplete = activeAssignments.remove(droneId);
                    assignmentDeadlines.remove(droneId);
                    if (incomplete != null) {
                        System.out.println("[Scheduler] Drone " + droneId + " ran out of water. Re-enqueueing remainder of fire: " + incomplete);
                        pending.add(incomplete.withoutFault());
                    }
                }

                if (gui != null) {
                    gui.updateDroneStatus(status);
                    if (status.getState() != DroneState.EN_ROUTE && status.getState() != DroneState.RETURNING) {
                        gui.appendEvent(statusMsg);
                    }
                }
                break;
            }

            case DRONE_COMPLETED: {
                // Guard against unavoidable UDP race: when a fault is injected,
                // the drone may complete its current drop cycle and send DRONE_COMPLETED
                // before it reads the RTB+fault message from the Scheduler.
                // Without this check, the spurious completion would inflate the
                // completed counter and could trigger premature shutdown.
                int senderDroneId = extractDroneIdFromAddr(addr, port);
                if (senderDroneId != -1 && hardFaultedDrones.contains(senderDroneId)) {
                    System.out.println("[Scheduler] Suppressed completion from hard-faulted Drone " + senderDroneId);
                    break;
                }

                completed++;
                if (message.getEvent() != null) {
                    for (Map.Entry<Integer, FireEvent> entry : new HashMap<>(activeAssignments).entrySet()) {
                        FireEvent active = entry.getValue();
                        if (active != null && active.getZoneId() == message.getEvent().getZoneId()
                                && active.getTime().equals(message.getEvent().getTime())) {
                            activeAssignments.remove(entry.getKey());
                            assignmentDeadlines.remove(entry.getKey());
                            break;
                        }
                    }
                }

                boolean fireStillActive = false;
                if (message.getEvent() != null) {
                    for (FireEvent active : activeAssignments.values()) {
                        if (active != null && active.getZoneId() == message.getEvent().getZoneId()
                                && active.getTime().equals(message.getEvent().getTime())) {
                            fireStillActive = true;
                            break;
                        }
                    }
                    if (!fireStillActive) {
                        for (FireEvent p : pending) {
                            if (p.getZoneId() == message.getEvent().getZoneId() && p.getTime().equals(message.getEvent().getTime())) {
                                fireStillActive = true;
                                break;
                            }
                        }
                    }
                }

                sendFireIncidentMessage(Message.fireAck(message.getEvent()), "sent FireAck");

                if (!fireStillActive) {
                    String completedMsg = "[Scheduler] Completion ack forwarded: " + message.getEvent();
                    System.out.println(completedMsg);

                    // Update GUI: Fire Extinguished
                    if (gui != null) {
                        if (gui != null) gui.removeFireEvent(message.getEvent());
                        gui.appendEvent(completedMsg);
                    }

                    // --- Recall en-route drones heading to the now-extinguished fire ---
                    for (Map.Entry<Integer, FireEvent> entry : new HashMap<>(activeAssignments).entrySet()) {
                        int otherDroneId = entry.getKey();
                        FireEvent assignment = entry.getValue();
                        if (assignment != null && assignment.getTime().equals(message.getEvent().getTime())) {
                            DroneStatus otherStatus = droneStatuses.get(otherDroneId);
                            if (otherStatus != null && otherStatus.getState() == DroneState.EN_ROUTE) {
                                try {
                                    activeAssignments.remove(otherDroneId);
                                    assignmentDeadlines.remove(otherDroneId);
                                    sendReturnToBase(otherDroneId);
                                    String recallMsg = "[Scheduler] Recalled Drone " + otherDroneId + " — fire fully extinguished.";
                                    System.out.println(recallMsg);
                                    if (gui != null) gui.appendEvent(recallMsg);
                                } catch (Exception e) {
                                    System.err.println("[Scheduler] Error recalling Drone " + otherDroneId + ": " + e.getMessage());
                                }
                            }
                        }
                    }

                    // --- Purge pending events for the exact same fire in case leftovers existed ---
                    pending.removeIf(p -> p.getTime().equals(message.getEvent().getTime()));
                    
                    // --- Infinite Simulation: Spawn a new random fire 1-6s after extinguishing ---
                    new Thread(() -> {
                        try {
                            int delayMs = 300 + (int)(Math.random() * 900); // 0.3 to 1.2 seconds
                            Thread.sleep(delayMs);
                            
                            // Zones 1 to 4 to avoid base (zone 0)
                            int randomZoneId = 1 + (int)(Math.random() * 4);
                            
                            int severityLevel = 1 + (int)(Math.random() * 3);
                            FireEvent.Severity severity = severityLevel == 1 ? FireEvent.Severity.LOW : 
                                                          severityLevel == 2 ? FireEvent.Severity.MODERATE : 
                                                          FireEvent.Severity.HIGH;
                            
                            java.time.LocalTime now = java.time.LocalTime.now();
                            String timeStr = String.format("%02d:%02d:%02d.%03d", now.getHour(), now.getMinute(), now.getSecond(), now.getNano() / 1000000);
                            FireEvent newFire = new FireEvent(timeStr, randomZoneId, FireEvent.EventType.FIRE_DETECTED, severity, FaultType.NONE, 0);
                            
                            Message fireMsg = Message.fireEvent(newFire);
                            SwarmNetwork.sendMessage(socket, InetAddress.getLocalHost(), socket.getLocalPort(), fireMsg, "[FireSpawner]", "sent", "to self");
                        } catch (Exception e) {
                            System.err.println("[Scheduler] Error naturally spawning new fire: " + e.getMessage());
                        }
                    }).start();
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Finds the drone ID associated with a given address and port.
     */
    private int extractDroneIdFromAddr(InetAddress addr, int port) {
        for (Map.Entry<Integer, InetAddress> entry : droneAddresses.entrySet()) {
            Integer dronePort = dronePorts.get(entry.getKey());
            if (entry.getValue().equals(addr) && dronePort != null && dronePort == port) {
                return entry.getKey();
            }
        }
        return -1;
    }
}
