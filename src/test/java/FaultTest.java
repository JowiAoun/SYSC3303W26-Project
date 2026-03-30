import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FaultTest {
    // Subsystem under test
    private DroneSubsystem drone;
    private InetAddress lastDroneAddr = null;
    private int lastDronePort = -1;
    private DatagramSocket schedulerSocket;

    @BeforeEach
    public void setup() {
        try {
            drone = new DroneSubsystem(1);
            schedulerSocket = new DatagramSocket(SwarmNetwork.SCHEDULER_PORT);
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    public void teardown() {
        drone.closeSocket();
        schedulerSocket.close();
        System.out.println();
    }

    /**
     * Consume the two startup messages every drone.run() emits:
     * 1. DRONE_READY
     * 2. DRONE_STATUS_UPDATE (IDLE)
     */
    private void consumeStartupMessages() throws Exception {
        // Message ready = toScheduler.get();
        // sleep for one second
        SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
        Message ready = rm.getMessage();
        lastDroneAddr = rm.getAddress();
        lastDronePort = rm.getPort();
//        assertEquals(Message.Type.DRONE_READY, ready.getType());

        // Message idle = toScheduler.get();
        rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
        Message idle = rm.getMessage();
        lastDroneAddr = rm.getAddress();
        lastDronePort = rm.getPort();
//        assertEquals(Message.Type.DRONE_STATUS_UPDATE, idle.getType());
//        assertEquals(DroneState.IDLE, idle.getStatus().getState());
    }

    /**
     * Wait until a DRONE_STATUS_UPDATE with the expected state is received.
     */
    private Message awaitState(DroneState expectedState, int maxMessages) throws Exception {
        for (int i = 0; i < maxMessages; i++) {
            SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
            Message msg = rm.getMessage();
            lastDroneAddr = rm.getAddress();
            lastDronePort = rm.getPort();

            if (msg.getType() == Message.Type.DRONE_STATUS_UPDATE
                    && msg.getStatus() != null
                    && msg.getStatus().getState() == expectedState) {
                return msg;
            }
        }
        fail("Never saw state " + expectedState + " within " + maxMessages + " messages");
        return null;
    }

    /**
     * Wait until a FAULTED status with the expected fault type is received.
     */
    private Message awaitFault(FaultType expectedFault, int maxMessages) throws Exception {
        for (int i = 0; i < maxMessages; i++) {
            SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
            Message msg = rm.getMessage();
            lastDroneAddr = rm.getAddress();
            lastDronePort = rm.getPort();

            if (msg.getType() == Message.Type.DRONE_STATUS_UPDATE
                    && msg.getStatus() != null
                    && msg.getStatus().getState() == DroneState.FAULTED
                    && msg.getStatus().getFaultType() == expectedFault) {
                return msg;
            }
        }
        fail("Never saw FAULTED with fault " + expectedFault + " within " + maxMessages + " messages");
        return null;
    }

    /**
     * Drain messages until the drone reaches IDLE again.
     */
    private void drainUntilIdle() throws Exception {
        for (int i = 0; i < 500; i++) {
            SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
            Message msg = rm.getMessage();
            lastDroneAddr = rm.getAddress();
            lastDronePort = rm.getPort();

            if (msg.getType() == Message.Type.DRONE_STATUS_UPDATE
                    && msg.getStatus() != null
                    && msg.getStatus().getState() == DroneState.IDLE) {
                return;
            }
        }
        fail("Never reached IDLE state within 500 messages");
    }

    private void sendAssignment(FireEvent event) throws Exception {
        SwarmNetwork.sendMessage(
                schedulerSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                lastDronePort,
                Message.droneAssignment(event),
                "[Scheduler]",
                "sent Assignment",
                "to Drone"
        );
    }

    private void sendShutdown() throws Exception {
        SwarmNetwork.sendMessage(
                schedulerSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                lastDronePort,
                Message.shutdown(),
                "[Scheduler]",
                "sent Shutdown",
                "to Drone"
        );
    }

    @Test
    @Order(1)
    public void test_1_none_fault_normal_flow() throws Exception {
        System.out.println("Test 1: FaultType.NONE -> normal completion");

        Thread t = new Thread(drone);
        t.start();

        consumeStartupMessages();

        FireEvent e = new FireEvent(
                "00:00:01",
                4,
                FireEvent.EventType.FIRE_DETECTED,
                FireEvent.Severity.LOW,
                FaultType.NONE,
                0
        );
        sendAssignment(e);

        Message enRoute = awaitState(DroneState.EN_ROUTE, 200);
        assertEquals(4, enRoute.getStatus().getZoneId());
        assertEquals(FaultType.NONE, enRoute.getStatus().getFaultType());

        boolean sawCompleted = false;
        boolean sawIdle = false;

        for (int i = 0; i < 500; i++) {
            SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
            Message msg = rm.getMessage();
            lastDroneAddr = rm.getAddress();
            lastDronePort = rm.getPort();

            if (msg.getType() == Message.Type.DRONE_COMPLETED) {
                sawCompleted = true;
            }

            if (msg.getType() == Message.Type.DRONE_STATUS_UPDATE
                    && msg.getStatus() != null
                    && msg.getStatus().getState() == DroneState.IDLE) {
                sawIdle = true;
                break;
            }
        }

        assertTrue(sawCompleted || sawIdle, "Expected normal completion or IDLE");

        sendShutdown();
        t.join(10000);
    }

    @Test
    @Order(2)
    public void test_2_stuck_mid_flight_fault() throws Exception {
        System.out.println("Test 2: FaultType.STUCK_MID_FLIGHT -> FAULTED then RETURNING");

        Thread t = new Thread(drone);
        t.start();

        consumeStartupMessages();

        FireEvent e = new FireEvent(
                "00:00:01",
                4,
                FireEvent.EventType.FIRE_DETECTED,
                FireEvent.Severity.LOW,
                FaultType.STUCK_MID_FLIGHT,
                1
        );
        sendAssignment(e);

        Message faulted = awaitFault(FaultType.STUCK_MID_FLIGHT, 400);
        assertEquals(DroneState.FAULTED, faulted.getStatus().getState());

        Message returning = awaitState(DroneState.RETURNING, 200);
        assertEquals(DroneState.RETURNING, returning.getStatus().getState());

        sendShutdown();
        t.join(10000);
    }

    @Test
    @Order(3)
    public void test_3_nozzle_jam_fault() throws Exception {
        System.out.println("Test 3: FaultType.NOZZLE_JAM -> hard fault / shutdown");

        Thread t = new Thread(drone);
        t.start();

        consumeStartupMessages();

        FireEvent e = new FireEvent(
                "00:00:01",
                4,
                FireEvent.EventType.FIRE_DETECTED,
                FireEvent.Severity.LOW,
                FaultType.NOZZLE_JAM,
                0
        );
        sendAssignment(e);

        Message faulted = awaitFault(FaultType.NOZZLE_JAM, 500);
        assertEquals(DroneState.FAULTED, faulted.getStatus().getState());

        t.join(12000);
        assertFalse(t.isAlive(), "Drone thread should terminate after hard fault");
    }

    @Test
    @Order(4)
    public void test_4_arrival_sensor_failure_fault() throws Exception {
        System.out.println("Test 4: FaultType.ARRIVAL_SENSOR_FAILURE -> FAULTED then RETURNING");

        Thread t = new Thread(drone);
        t.start();

        consumeStartupMessages();

        FireEvent e = new FireEvent(
                "00:00:01",
                4,
                FireEvent.EventType.FIRE_DETECTED,
                FireEvent.Severity.LOW,
                FaultType.ARRIVAL_SENSOR_FAILURE,
                0
        );
        sendAssignment(e);

        Message faulted = awaitFault(FaultType.ARRIVAL_SENSOR_FAILURE, 500);
        assertEquals(DroneState.FAULTED, faulted.getStatus().getState());

        Message returning = awaitState(DroneState.RETURNING, 200);
        assertEquals(DroneState.RETURNING, returning.getStatus().getState());

        sendShutdown();
        t.join(10000);
    }

    @Test
    @Order(5)
    public void test_5_corrupted_packet_fault() throws Exception {
        System.out.println("Test 5: Scheduler drops corrupted packet and continues processing");

        // Close fake scheduler socket from setup(), since this test uses a real Scheduler
        schedulerSocket.close();

        Scheduler scheduler = new Scheduler(null);
        Thread schedulerThread = new Thread(scheduler);
        Thread droneThread = new Thread(drone);

        DatagramSocket fireSocket = new DatagramSocket(SwarmNetwork.FIS_PORT);

        schedulerThread.start();
        droneThread.start();

        Thread.sleep(1000);

        FireEvent e = new FireEvent(
                "00:00:01",
                4,
                FireEvent.EventType.FIRE_DETECTED,
                FireEvent.Severity.LOW,
                FaultType.CORRUPTED_MESSAGE,
                0
        );

        // Send fire event to scheduler
        SwarmNetwork.sendMessage(
                fireSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e),
                "[FireIncident]",
                "sent FireEvent",
                "to Scheduler"
        );

        // Tell scheduler input is complete
        SwarmNetwork.sendMessage(
                fireSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                SwarmNetwork.SCHEDULER_PORT,
                Message.shutdown(),
                "[FireIncident]",
                "sent Shutdown",
                "to Scheduler"
        );

        boolean sawAck = false;

        // Wait for scheduler to eventually forward FIRE_ACK back to fireSocket.
        // If this happens, then scheduler successfully ignored the corrupted packet
        // and continued processing later valid packets.
        for (int i = 0; i < 50; i++) {
            Message msg = SwarmNetwork.receiveMessage(fireSocket);
            if (msg.getType() == Message.Type.FIRE_ACK) {
                sawAck = true;
                break;
            }
        }

        assertTrue(sawAck, "Expected FIRE_ACK, meaning scheduler dropped corrupted packet and still completed processing");

        schedulerThread.join(10000);
        droneThread.join(10000);

        fireSocket.close();
        scheduler.closeSocket();
    }

    @Test
    @Order(6)
    public void test_6_scheduler_timeout() throws Exception {
        System.out.println("Test 6: Scheduler detects timed out drone");

        // close fake scheduler socket from setup(), since this test uses a real Scheduler object
        if (schedulerSocket != null && !schedulerSocket.isClosed()) {
            schedulerSocket.close();
        }

        Scheduler scheduler = new Scheduler(null);
        DatagramSocket fireSocket = new DatagramSocket();
        DatagramSocket droneSocket = new DatagramSocket();

        // register fake drone with scheduler
        SwarmNetwork.sendMessage(
                droneSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                SwarmNetwork.SCHEDULER_PORT,
                Message.droneReady(),
                "[Drone 1]",
                "sent Ready",
                "to Scheduler"
        );
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        SwarmNetwork.sendMessage(
                droneSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)),
                "[Drone 1]",
                "sent Drone Status",
                "to Scheduler"
        );
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        // send one fire event
        FireEvent e = new FireEvent(
                "00:00:01",
                4,
                FireEvent.EventType.FIRE_DETECTED,
                FireEvent.Severity.LOW,
                FaultType.NONE,
                0
        );

        SwarmNetwork.sendMessage(
                fireSocket,
                InetAddress.getByName(SwarmNetwork.LOCALHOST),
                SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e),
                "[FireIncident]",
                "sent FireEvent",
                "to Scheduler"
        );
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        // dispatch event to drone
        assertTrue(scheduler.canDispatchPendingEvent(), "Scheduler should dispatch pending event");
        scheduler.dispatchPendingEvent();

        Message assignment = SwarmNetwork.receiveMessage(droneSocket);
        assertEquals(Message.Type.DRONE_ASSIGNMENT, assignment.getType());
        assertEquals(4, assignment.getEvent().getZoneId());

        // wait past the timeout window
        Thread.sleep(11000);

        // run timeout check
        scheduler.checkForTimedOutDrones();
        scheduler.evaluateTransition();

        // After timeout:
        //  1. event should be back in pending
        //  2. drone should now be FAULTED/busy
        //  3. scheduler state should be AWAITING_DRONE
        assertEquals(SchedulerState.AWAITING_DRONE, scheduler.getCurrentState());

        System.out.printf("Expecting state: AWAITING_DRONE, got %s\n", scheduler.getCurrentState());

        fireSocket.close();
        droneSocket.close();
        scheduler.closeSocket();
    }

}
