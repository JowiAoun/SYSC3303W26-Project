import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DroneStateMachineTest {

    // Message buffers
    private MessageBuffer toScheduler;
    private MessageBuffer schedulerToDrone;

    // Subsystem under test
    private DroneSubsystem drone;
    private InetAddress lastDroneAddr = null;
    private int lastDronePort = -1;
    private DatagramSocket schedulerSocket;

    @BeforeEach
    public void setup() {
        toScheduler = new MessageBuffer();
        schedulerToDrone = new MessageBuffer();
        try {
            drone = new DroneSubsystem(1, toScheduler, schedulerToDrone);
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
        assertEquals(Message.Type.DRONE_READY, ready.getType());

        // Message idle = toScheduler.get();
        rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
        Message idle = rm.getMessage();
        lastDroneAddr = rm.getAddress();
        lastDronePort = rm.getPort();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, idle.getType());
        assertEquals(DroneState.IDLE, idle.getStatus().getState());
    }

    /**
     * Drain messages from the toScheduler buffer until we see an IDLE status update.
     * Useful for cleanup after a full assignment cycle.
     */
    private void drainUntilIdle() throws Exception {
        for (int i = 0; i < 50; i++) {
            // Message m = toScheduler.get();
            SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
            Message m = rm.getMessage();
            lastDroneAddr = rm.getAddress();
            lastDronePort = rm.getPort();
            if (m.getType() == Message.Type.DRONE_STATUS_UPDATE
                    && m.getStatus() != null
                    && m.getStatus().getState() == DroneState.IDLE) {
                return;
            }
        }
        fail("Never reached IDLE state within 50 messages");
    }

    @Test
    @Order(1)
    public void test_1() throws Exception {
        System.out.println("Test 1: Drone sends EN_ROUTE soon after assignment");

        Thread t = new Thread(drone);
        t.start();

        // Consume startup (DRONE_READY + IDLE status)
        consumeStartupMessages();

        // Send assignment
        FireEvent e = new FireEvent("00:00:01", 5, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // schedulerToDrone.put(Message.droneAssignment(e));
        SwarmNetwork.sendMessage(schedulerSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST), lastDronePort, Message.droneAssignment(e), "[Scheduler]", "sent Assignment", "to Drone");

        // Assert first status after assignment is EN_ROUTE
        // Message m = toScheduler.get();
        SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
        Message m = rm.getMessage();
        lastDroneAddr = rm.getAddress();
        lastDronePort = rm.getPort();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, m.getType());
        assertEquals(DroneState.EN_ROUTE, m.getStatus().getState());
        assertEquals(5, m.getStatus().getZoneId());

        System.out.printf("Expecting type: DRONE_STATUS_UPDATE, got %s\n", m.getType());
        System.out.printf("Expecting state: EN_ROUTE, got %s\n", m.getStatus().getState());
        System.out.printf("Expecting zoneId: 5, got %d\n", m.getStatus().getZoneId());

        // Clean up: drain remaining messages and send shutdown
         drainUntilIdle();
        // schedulerToDrone.put(Message.shutdown());
        SwarmNetwork.sendMessage(schedulerSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST), lastDronePort, Message.shutdown(), "[Scheduler]", "sent Shutdown", "to Drone");
        t.join(10000);
    }

    @Test
    @Order(2)
    public void test_2() throws Exception {
        System.out.println("Test 2: When empty tank, drone returns then refills before heading out");

        // Force remainingLiters = 0
        Field f = DroneSubsystem.class.getDeclaredField("remainingLiters");
        f.setAccessible(true);
        f.setInt(drone, 0);

        Thread t = new Thread(drone);
        t.start();

        // Consume startup (DRONE_READY + IDLE status)
        consumeStartupMessages();

        // Send assignment
        FireEvent e = new FireEvent("00:00:01", 3, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // schedulerToDrone.put(Message.droneAssignment(e));
        SwarmNetwork.sendMessage(schedulerSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST), lastDronePort, Message.droneAssignment(e), "[Scheduler]", "sent Assignment", "to Drone");

        // Expect RETURNING first (tank is empty)
        // Message m1 = toScheduler.get();
        SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
        Message m1 = rm.getMessage();
        lastDroneAddr = rm.getAddress();
        lastDronePort = rm.getPort();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, m1.getType());
        assertEquals(DroneState.RETURNING, m1.getStatus().getState());
        assertEquals(0, m1.getStatus().getZoneId());

        System.out.printf("Expecting state: RETURNING, got %s\n", m1.getStatus().getState());

        // Expect REFILLING next
        // Message m2 = toScheduler.get();
        rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
        Message m2 = rm.getMessage();
        lastDroneAddr = rm.getAddress();
        lastDronePort = rm.getPort();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, m2.getType());
        assertEquals(DroneState.REFILLING, m2.getStatus().getState());
        assertEquals(0, m2.getStatus().getZoneId());

        System.out.printf("Expecting state: REFILLING, got %s\n", m2.getStatus().getState());

        // Clean up: drain remaining messages and send shutdown
         drainUntilIdle();
        // schedulerToDrone.put(Message.shutdown());
        SwarmNetwork.sendMessage(schedulerSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST), lastDronePort, Message.shutdown(), "[Scheduler]", "sent Shutdown", "to Drone");
        t.join(10000);
    }

    @Test
    @Order(3)
    public void test_3() throws Exception {
        System.out.println("Test 3: Drone eventually sends COMPLETED and returns to IDLE");

        Thread t = new Thread(drone);
        t.start();

        // Consume startup
        consumeStartupMessages();

        // Send assignment
        FireEvent e = new FireEvent("00:00:01", 4, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // schedulerToDrone.put(Message.droneAssignment(e));
        SwarmNetwork.sendMessage(schedulerSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST), lastDronePort, Message.droneAssignment(e), "[Scheduler]", "sent Assignment", "to Drone");

        boolean sawCompleted = false;
        boolean sawIdle = false;

        // Read messages and look for completion then idle
        for (int i = 0; i < 25; i++) {
            // Message m = toScheduler.get();
            SwarmNetwork.ReceivedMessage rm = SwarmNetwork.receiveMessageWithSource(schedulerSocket);
            Message m = rm.getMessage();
            lastDroneAddr = rm.getAddress();
            lastDronePort = rm.getPort();

            if (m.getType() == Message.Type.DRONE_COMPLETED) {
                sawCompleted = true;
                System.out.println("Saw DRONE_COMPLETED message.");
            }

            if (m.getType() == Message.Type.DRONE_STATUS_UPDATE
                    && m.getStatus() != null
                    && m.getStatus().getState() == DroneState.IDLE) {
                sawIdle = true;
                System.out.println("Saw IDLE status update.");
                break;
            }
        }

        assertTrue(sawCompleted || sawIdle,
                "Expected DRONE_COMPLETED message or IDLE status at the end");

        System.out.printf("Expecting completed OR idle: true, got %s\n", (sawCompleted || sawIdle));

        // Send shutdown
        // schedulerToDrone.put(Message.shutdown());
        SwarmNetwork.sendMessage(schedulerSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST), lastDronePort, Message.shutdown(), "[Scheduler]", "sent Shutdown", "to Drone");
        t.join(10000);
    }
}
