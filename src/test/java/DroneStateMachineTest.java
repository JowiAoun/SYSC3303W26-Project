import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DroneStateMachineTest {

    // Message buffers
    private MessageBuffer toScheduler;
    private MessageBuffer schedulerToDrone;

    // Subsystem under test
    private DroneSubsystem drone;

    @BeforeEach
    public void setup() {
        toScheduler = new MessageBuffer();
        schedulerToDrone = new MessageBuffer();
        drone = new DroneSubsystem(toScheduler, schedulerToDrone);
    }

    @AfterEach
    public void spacing() {
        System.out.println();
    }

    @Test
    @Order(1)
    public void test_1() throws Exception {
        System.out.println("Test 1: Drone sends EN_ROUTE soon after assignment");

        // 1. Build assignment message
        FireEvent e = new FireEvent("00:00:01", 5, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        Message assignment = Message.droneAssignment(e);

        // 2. Run assignment processing in a thread (like your sample)
        Thread t = new Thread(() -> {
            try {
                drone.processAssignment(assignment);
            } catch (InterruptedException ignored) { }
        });

        t.start();

        // 3. Assert first message to scheduler is EN_ROUTE
        Message m = toScheduler.get();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, m.getType());
        assertEquals(DroneState.EN_ROUTE, m.getStatus().getState());
        assertEquals(5, m.getStatus().getZoneId());

        System.out.printf("Expecting type: DRONE_STATUS_UPDATE, got %s\n", m.getType());
        System.out.printf("Expecting state: EN_ROUTE, got %s\n", m.getStatus().getState());
        System.out.printf("Expecting zoneId: 5, got %d\n", m.getStatus().getZoneId());

        t.interrupt();
        t.join();
    }

    @Test
    @Order(2)
    public void test_2() throws Exception {
        System.out.println("Test 2: When empty tank, drone returns then refills");

        // 1. Force remainingLiters = 0 (student-style reflection access)
        Field f = DroneSubsystem.class.getDeclaredField("remainingLiters");
        f.setAccessible(true);
        f.setInt(drone, 0);

        // 2. Build assignment
        FireEvent e = new FireEvent("00:00:01", 3, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        Message assignment = Message.droneAssignment(e);

        Thread t = new Thread(() -> {
            try {
                drone.processAssignment(assignment);
            } catch (InterruptedException ignored) { }
        });

        t.start();

        // 3. Expect RETURNING first
        Message m1 = toScheduler.get();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, m1.getType());
        assertEquals(DroneState.RETURNING, m1.getStatus().getState());
        assertEquals(0, m1.getStatus().getZoneId());

        System.out.printf("Expecting state: RETURNING, got %s\n", m1.getStatus().getState());

        // 4. Expect REFILLING next
        Message m2 = toScheduler.get();
        assertEquals(Message.Type.DRONE_STATUS_UPDATE, m2.getType());
        assertEquals(DroneState.REFILLING, m2.getStatus().getState());
        assertEquals(0, m2.getStatus().getZoneId());

        System.out.printf("Expecting state: REFILLING, got %s\n", m2.getStatus().getState());

        t.interrupt();
        t.join();
    }

    @Test
    @Order(3)
    public void test_3() throws Exception {
        System.out.println("Test 3: Drone eventually sends COMPLETED or returns to IDLE");

        // 1. Build assignment
        FireEvent e = new FireEvent("00:00:01", 4, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        Message assignment = Message.droneAssignment(e);

        Thread t = new Thread(() -> {
            try {
                drone.processAssignment(assignment);
            } catch (InterruptedException ignored) { }
        });

        t.start();

        boolean sawCompleted = false;
        boolean sawIdle = false;

        // 2. Read a bunch of messages and accept either completed or final idle
        for (int i = 0; i < 25; i++) {
            Message m = toScheduler.get();

            if (m.getType() == Message.Type.DRONE_COMPLETED) {
                sawCompleted = true;
                System.out.println("Saw DRONE_COMPLETED message.");
                break;
            }

            if (m.getType() == Message.Type.DRONE_STATUS_UPDATE &&
                    m.getStatus() != null &&
                    m.getStatus().getState() == DroneState.IDLE) {
                sawIdle = true;
                System.out.println("Saw IDLE status update.");
                break;
            }
        }

        assertTrue(sawCompleted || sawIdle,
                "Expected DRONE_COMPLETED message or IDLE status at the end");

        System.out.printf("Expecting completed OR idle: true, got %s\n", (sawCompleted || sawIdle));

        t.interrupt();
        t.join();
    }
}
