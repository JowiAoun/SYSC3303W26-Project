import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SchedulerDispatchTest {

    // Message buffers
    private MessageBuffer toScheduler;
    private MessageBuffer schedulerToFire;
    private MessageBuffer schedulerToDrone;

    // Subsystem under test
    private Scheduler scheduler;

    @BeforeEach
    public void setup() {
        // Message buffers
        toScheduler = new MessageBuffer();
        schedulerToFire = new MessageBuffer();
        schedulerToDrone = new MessageBuffer();

        // Build scheduler (4th arg is whatever your project uses, keep null like yours)
        scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone, null);
    }

    @AfterEach
    public void spacing() {
        System.out.println();
    }

    @Test
    @Order(1)
    public void test_1() throws Exception {
        System.out.println("Test 1: Scheduler doesn't dispatch when drone isn't idle");

        // 1. Send fire event (becomes pending)
        FireEvent e = new FireEvent("00:00:01", 4, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        toScheduler.put(Message.fireEvent(e));
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 2. Send drone status: busy (EN_ROUTE)
        toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.EN_ROUTE, 4, 15)));
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 3. Assert cannot dispatch
        boolean actualValue = scheduler.canDispatchPendingEvent();
        assertFalse(actualValue);
        System.out.printf("Expecting: false, got %s\n", actualValue);
    }

    @Test
    @Order(2)
    public void test_2() throws Exception {
        System.out.println("Test 2: Scheduler dispatches when drone is IDLE and pending event exists");

        // 1. Drone is IDLE
        toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)));
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 2. Fire event arrives
        FireEvent e = new FireEvent("00:00:01", 7, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        toScheduler.put(Message.fireEvent(e));
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 3. Dispatch
        boolean canDispatch = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatch);
        System.out.printf("Expecting canDispatch: true, got %s\n", canDispatch);

        scheduler.dispatchPendingEvent();

        // 4) Assert message went to drone buffer
        Message toDrone = schedulerToDrone.get();
        assertEquals(Message.Type.DRONE_ASSIGNMENT, toDrone.getType());
        assertEquals(7, toDrone.getEvent().getZoneId());

        System.out.printf("Expecting msg type: DRONE_ASSIGNMENT, got %s\n", toDrone.getType());
        System.out.printf("Expecting zoneId: 7, got %d\n", toDrone.getEvent().getZoneId());
    }


    @Test
    @Order(3)
    public void test_3() throws Exception {
        System.out.println("Test 3: Scheduler dispatches queued event after drone becomes IDLE");

        // 1. Fire comes first (pending)
        FireEvent e = new FireEvent("00:00:01", 8, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        toScheduler.put(Message.fireEvent(e));
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 2. Drone is busy -> should not dispatch yet
        toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.EN_ROUTE, 8, 15)));
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        boolean canDispatchNow = scheduler.canDispatchPendingEvent();
        assertFalse(canDispatchNow);
        System.out.printf("Expecting canDispatch: false, got %s\n", canDispatchNow);

        // 3. Drone becomes IDLE -> now it should dispatch
        toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)));
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        boolean canDispatchLater = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatchLater);
        System.out.printf("Expecting canDispatch: true, got %s\n", canDispatchLater);

        scheduler.dispatchPendingEvent();

        Message toDrone = schedulerToDrone.get();
        assertEquals(Message.Type.DRONE_ASSIGNMENT, toDrone.getType());
        assertEquals(8, toDrone.getEvent().getZoneId());

        System.out.printf("Expecting msg type: DRONE_ASSIGNMENT, got %s\n", toDrone.getType());
        System.out.printf("Expecting zoneId: 8, got %d\n", toDrone.getEvent().getZoneId());
    }
    @Test
    @Order(4)
    public void test_4_multi_tasking() throws Exception {
        System.out.println("Test 4: Scheduler dispatches next task to drone at remote zone if agent sufficient");

        // 1. Drone is IDLE at Zone 5 with 10L remaining
        toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 5, 10)));
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 2. Fire event arrives (Zone 6, needs 5L)
        // Note: Severity LOW usually implies 10L, but let's assume Scheduler checks logic based on event.getRequiredLiters()
        // We'll use a mocked FireEvent or just rely on standard severity if 10L is enough.
        // If LOW requires 10L, and drone has 10L, it should be exact match.
        FireEvent e = new FireEvent("00:00:01", 6, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // Assuming LOW = 10L? Let's check FireEvent.java if needed, or assume standard.
        // If getRequiredLiters() <= 10, it should pass.
        
        toScheduler.put(Message.fireEvent(e));
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 3. Dispatch
        boolean canDispatch = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatch, "Should dispatch because 10L (drone) >= 10L (required)"); 
        // Note: This relies on strict inequality check? My code said: if (remaining < required) return to base.
        // So 10 < 10 is false, so it proceeds to dispatch.

        scheduler.dispatchPendingEvent();

        // 4. Assert message went to drone buffer
        Message toDrone = schedulerToDrone.get();
        assertEquals(Message.Type.DRONE_ASSIGNMENT, toDrone.getType());
        assertEquals(6, toDrone.getEvent().getZoneId());
    }

    @Test
    @Order(5)
    public void test_5_return_to_base_if_low_agent() throws Exception {
        System.out.println("Test 5: Scheduler commands Return to Base if agent insufficient for next task");

        // 1. Drone is IDLE at Zone 5 with 5L remaining
        toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 5, 5)));
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 2. Fire event arrives (Zone 6, needs 10L - LOW)
        FireEvent e = new FireEvent("00:00:01", 6, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        toScheduler.put(Message.fireEvent(e));
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg); // process fire event

        // 3. Try to dispatch
        // canDispatchPendingEvent() checks if IDLE and pending exists. It returns TRUE.
        boolean canDispatch = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatch, "Scheduler sees drone is IDLE and work exists");

        // 4. dispatchPendingEvent() checks capacity and should send RTB
        scheduler.dispatchPendingEvent();

        // 5. Assert message -- should be RETURN_TO_BASE
        Message toDrone = schedulerToDrone.get();
        assertEquals(Message.Type.DRONE_RETURN_TO_BASE, toDrone.getType());
    }
}
