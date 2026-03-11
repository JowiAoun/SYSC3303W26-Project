import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.net.InetAddress;
import java.net.SocketException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SchedulerDispatchTest {

    // Message buffers
    private MessageBuffer toScheduler;
    private MessageBuffer schedulerToFire;
    private MessageBuffer schedulerToDrone;

    // Subsystem under test
    private Scheduler scheduler;

    @BeforeEach
    public void setup() throws SocketException {
        // Message buffers
        toScheduler = new MessageBuffer();
        schedulerToFire = new MessageBuffer();
        schedulerToDrone = new MessageBuffer();

        // Build scheduler
        scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone, null);
    }

    @AfterEach
    public void teardown() {
        scheduler.closeSocket();
        System.out.println();
    }

    @Test
    @Order(1)
    public void test_1() throws Exception {
        System.out.println("Test 1: Scheduler doesn't dispatch when drone isn't idle");

        // 1. Send fire event (becomes pending)
        FireEvent e = new FireEvent("00:00:01", 4, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // toScheduler.put(Message.fireEvent(e));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e), "[FireIncident]", "sent FireEvent", "to Scheduler");
        SwarmNetwork.ReceivedMessage rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 2. Send drone status: busy (EN_ROUTE)
        // toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.EN_ROUTE, 4, 15)));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.EN_ROUTE, 4, 15)),
                "[FireIncident]", "sent FireEvent", "to Scheduler");
        rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 3. Assert cannot dispatch
        boolean actualValue = scheduler.canDispatchPendingEvent();
        assertFalse(actualValue);
        System.out.printf("Expecting: false, got %s\n", actualValue);

        // 4. Evaluate transition — should be AWAITING_DRONE (pending + drone busy)
        scheduler.evaluateTransition();
        assertEquals(SchedulerState.AWAITING_DRONE, scheduler.getCurrentState());
        System.out.printf("Expecting state: AWAITING_DRONE, got %s\n", scheduler.getCurrentState());
    }

    @Test
    @Order(2)
    public void test_2() throws Exception {
        System.out.println("Test 2: Scheduler dispatches when drone is IDLE and pending event exists");

        // 1. Drone is IDLE
        // toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)),
                "[FireIncident]", "sent FireEvent", "to Scheduler");
        SwarmNetwork.ReceivedMessage rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 2. Fire event arrives
         FireEvent e = new FireEvent("00:00:01", 7, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // toScheduler.put(Message.fireEvent(e));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e), "[FireIncident]", "sent FireEvent", "to Scheduler");
        rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 3. Dispatch
        boolean canDispatch = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatch);
        System.out.printf("Expecting canDispatch: true, got %s\n", canDispatch);

        scheduler.dispatchPendingEvent();

        // 4) Assert message went to drone buffer
        // Message toDrone = schedulerToDrone.get();
        Message toDrone = SwarmNetwork.receiveMessage(scheduler.getSocket());
        assertEquals(Message.Type.DRONE_ASSIGNMENT, toDrone.getType());
        assertEquals(7, toDrone.getEvent().getZoneId());

        System.out.printf("Expecting msg type: DRONE_ASSIGNMENT, got %s\n", toDrone.getType());
        System.out.printf("Expecting zoneId: 7, got %d\n", toDrone.getEvent().getZoneId());

        // 5. After dispatch, drone is EN_ROUTE (optimistic update) -> DRONE_ACTIVE
        scheduler.evaluateTransition();
        assertEquals(SchedulerState.DRONE_ACTIVE, scheduler.getCurrentState());
        System.out.printf("Expecting state: DRONE_ACTIVE, got %s\n", scheduler.getCurrentState());
    }


    @Test
    @Order(3)
    public void test_3() throws Exception {
        System.out.println("Test 3: Scheduler dispatches queued event after drone becomes IDLE");

        // 1. Fire comes first (pending)
        FireEvent e = new FireEvent("00:00:01", 8, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // toScheduler.put(Message.fireEvent(e));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e), "[FireIncident]", "sent FireEvent", "to Scheduler");
        SwarmNetwork.ReceivedMessage rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 2. Drone is busy -> should not dispatch yet
        // toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.EN_ROUTE, 8, 15)));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.EN_ROUTE, 8, 15)),
                "[FireIncident]", "sent FireEvent", "to Scheduler");
        rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        boolean canDispatchNow = scheduler.canDispatchPendingEvent();
        assertFalse(canDispatchNow);
        System.out.printf("Expecting canDispatch: false, got %s\n", canDispatchNow);

        // 3. Drone becomes IDLE -> now it should dispatch
        // toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 0, 15)),
                "[FireIncident]", "sent FireEvent", "to Scheduler");
        rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        boolean canDispatchLater = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatchLater);
        System.out.printf("Expecting canDispatch: true, got %s\n", canDispatchLater);

        scheduler.dispatchPendingEvent();

        // Message toDrone = schedulerToDrone.get();
        Message toDrone = SwarmNetwork.receiveMessage(scheduler.getSocket());
        assertEquals(Message.Type.DRONE_ASSIGNMENT, toDrone.getType());
        assertEquals(8, toDrone.getEvent().getZoneId());

        System.out.printf("Expecting msg type: DRONE_ASSIGNMENT, got %s\n", toDrone.getType());
        System.out.printf("Expecting zoneId: 8, got %d\n", toDrone.getEvent().getZoneId());

        // After dispatch, drone is EN_ROUTE -> DRONE_ACTIVE
        scheduler.evaluateTransition();
        assertEquals(SchedulerState.DRONE_ACTIVE, scheduler.getCurrentState());
    }
    @Test
    @Order(4)
    public void test_4_multi_tasking() throws Exception {
        System.out.println("Test 4: Scheduler dispatches next task to drone at remote zone if agent sufficient");

        // 1. Drone is IDLE at Zone 5 with 10L remaining
        // toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 5, 10)));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 5, 10)),
                "[FireIncident]", "sent FireEvent", "to Scheduler");
        SwarmNetwork.ReceivedMessage rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 2. Fire event arrives (Zone 6, needs 5L)
        FireEvent e = new FireEvent("00:00:01", 6, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // toScheduler.put(Message.fireEvent(e));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e), "[FireIncident]", "sent FireEvent", "to Scheduler");
        rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 3. Dispatch
        boolean canDispatch = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatch, "Should dispatch because 10L (drone) >= 10L (required)");

        scheduler.dispatchPendingEvent();

        // 4. Assert message went to drone buffer
        // Message toDrone = schedulerToDrone.get();
        Message toDrone = SwarmNetwork.receiveMessage(scheduler.getSocket());
        assertEquals(Message.Type.DRONE_ASSIGNMENT, toDrone.getType());
        assertEquals(6, toDrone.getEvent().getZoneId());
    }

    @Test
    @Order(5)
    public void test_5_return_to_base_if_low_agent() throws Exception {
        System.out.println("Test 5: Scheduler commands Return to Base if agent insufficient for next task");

        // 1. Drone is IDLE at Zone 5 with 5L remaining
        // toScheduler.put(Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 5, 5)));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(1, DroneState.IDLE, 5, 5)),
                "[FireIncident]", "sent FireEvent", "to Scheduler");
        SwarmNetwork.ReceivedMessage rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm);

        // 2. Fire event arrives (Zone 6, needs 10L - LOW)
        FireEvent e = new FireEvent("00:00:01", 6, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.LOW);
        // toScheduler.put(Message.fireEvent(e));
        SwarmNetwork.sendMessage(scheduler.getSocket(), InetAddress.getByName(SwarmNetwork.LOCALHOST), SwarmNetwork.SCHEDULER_PORT,
                Message.fireEvent(e), "[FireIncident]", "sent FireEvent", "to Scheduler");
        rm = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(rm); // process fire event

        // 3. Try to dispatch
        // canDispatchPendingEvent() checks if IDLE and pending exists. It returns TRUE.
        boolean canDispatch = scheduler.canDispatchPendingEvent();
        assertTrue(canDispatch, "Scheduler sees drone is IDLE and work exists");

        // 4. dispatchPendingEvent() checks capacity and should send RTB
        scheduler.dispatchPendingEvent();

        // 5. Assert message
        // Message toDrone = schedulerToDrone.get();
        Message toDrone = SwarmNetwork.receiveMessage(scheduler.getSocket());
        assertEquals(Message.Type.DRONE_RETURN_TO_BASE, toDrone.getType());

        // 6. After RTB, drone is RETURNING — should be AWAITING_DRONE (still have pending)
        scheduler.evaluateTransition();
        assertEquals(SchedulerState.AWAITING_DRONE, scheduler.getCurrentState());
    }
}
