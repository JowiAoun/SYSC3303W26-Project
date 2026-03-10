import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.net.SocketException;
import java.util.List;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainTest {
    String inputPath;
    // Message buffers
    private MessageBuffer toScheduler;
    private MessageBuffer schedulerToFire;
    private MessageBuffer schedulerToDrone;

    // Build subsystems.
    private FireIncidentSubsystem fireIncident;
    private DroneSubsystem drone;
    private Scheduler scheduler;

    @BeforeEach
    public void setup() throws SocketException {
        inputPath = "./src/test/resources/data/events.csv";

        toScheduler = new MessageBuffer();
        schedulerToFire = new MessageBuffer();
        schedulerToDrone = new MessageBuffer();

        fireIncident = new FireIncidentSubsystem(inputPath, toScheduler, schedulerToFire);
        drone = new DroneSubsystem(toScheduler, schedulerToDrone);
        scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone, null);
    }

    @AfterEach
    public void teardown() {
        fireIncident.closeSocket();
        drone.closeSocket();
        scheduler.closeSocket();
        System.out.println();
    }

    // Test 1a: Verify Fire Incident Subsystem reads valid input events
    @Test
    @Order(1)
    public void test_1a() {
        System.out.println("Test 1a: Verify Fire Incident Subsystem reads valid input events");

        boolean actualValue = fireIncident.hasAtLeastOneValidEvent();
        assertTrue(actualValue);
        System.out.printf("Expecting: true, got %s\n", actualValue);
    }

    // Test 1b: Verify FIS handles invalid input events
    @Test
    @Order(2)
    public void test_1b() {
        System.out.println("Test 1b: Verify FIS handles invalid input events");

        fireIncident.setInputCsvPath("./src/test/resources/data/invalid_events_data.csv");
        boolean actualValue = fireIncident.hasAtLeastOneValidEvent();
        assertFalse(actualValue);
        System.out.printf("Expecting: false, got %s\n", actualValue);
    }

    // Test 2: Verify FIS sends valid input to Scheduler
    @Test
    @Order(3)
    public void test_2() throws InterruptedException {
        System.out.println("Test 2: Verify FIS sends valid input to Scheduler");

        // 1. FIS reads fire event from test input data (or we can rig the FIS to read a set fire event, might be easier)
        fireIncident.setInputCsvPath("./src/test/resources/data/valid_fire_event.csv");
        List<FireEvent> events = fireIncident.loadEventsFromCsv();
        // 2. FIS sends fire event to Scheduler
        int eventsSent = fireIncident.sendEventsToScheduler(events);
        System.out.printf("eventsSent: %d\n", eventsSent);
        // 3. Scheduler reads, handles the sent fire event data
        System.out.printf("Total events BEFORE receiving message: %d\n", scheduler.getTotalEvents());

        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);
        // 4. ASSERT that Scheduler handled the sent data correctly, maybe check some variable/state change?
        System.out.printf("Total events AFTER receiving message: %d\n", scheduler.getTotalEvents());
        assertEquals(1, scheduler.getTotalEvents());
        System.out.printf("Expecting: 1, got %d\n", scheduler.getTotalEvents());

    }

    // Test 3a: Verify Drone Subsystem contacts Scheduler - Handles NO tasks/fires to put out properly
    @Test
    @Order(4)
    public void test_3a() throws InterruptedException {
        System.out.println("Test 3a: Verify Drone Subsystem contacts Scheduler - Handles NO tasks/fires to put out properly");
        fireIncident.setInputCsvPath("./src/test/resources/data/blank_data.csv");

        // 1. DS contacts Scheduler
        drone.sendReadySignal();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        // 2.Scheduler dispatch requires an IDLE status update from the drone.
        drone.sendUserIdlingUpdate();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        // 3. ASSERT Scheduler return message/bool: No tasks for drone
        boolean actualValue = scheduler.canDispatchPendingEvent();
        assertFalse(actualValue);
        System.out.printf("Expecting: false, got %s\n", actualValue);
    }

    // Test 3b: Verify Drone Subsystem contacts Scheduler - Handles HAS tasks/fires to put out properly
    @Test
    @Order(5)
    public void test_3b() throws InterruptedException {
        System.out.println("Test 3b: Verify Drone Subsystem contacts Scheduler - Handles HAS tasks/fires to put out properly");
        fireIncident.setInputCsvPath("./src/test/resources/data/valid_fire_event.csv");
        List<FireEvent> events = fireIncident.loadEventsFromCsv();
        int eventsSent = fireIncident.sendEventsToScheduler(events);
        System.out.printf("eventsSent: %d\n", eventsSent);
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 1. DS contacts Scheduler
        drone.sendReadySignal();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        // 2. Scheduler dispatch requires an IDLE status update from the drone.
        drone.sendUserIdlingUpdate();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());
        // 3. ASSERT Scheduler return message/bool: Has task for drone
        boolean actualValue = scheduler.canDispatchPendingEvent();
        assertTrue(actualValue, "Scheduler should be able to dispatch pending even when drone is IDLE");
        System.out.printf("Expecting: true, got %s\n", actualValue);
    }

    // Test 4a: Verify Scheduler reads messages from FIS and forwards to DS
    @Test
    @Order(6)
    public void test_4a() throws InterruptedException {
        System.out.println("Test 4a: Verify Scheduler reads messages from FIS and forwards to DS");
        fireIncident.setInputCsvPath("./src/test/resources/data/valid_fire_event.csv");
        List<FireEvent> events = fireIncident.loadEventsFromCsv();
        drone.sendReadySignal();
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        // 1. Scheduler dispatch requires an IDLE status update from the drone.
        drone.sendUserIdlingUpdate();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());

        // 2. FIS sends a message to Scheduler
        int eventsSent = fireIncident.sendEventsToScheduler(events);
        System.out.printf("eventsSent: %d\n", eventsSent);
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);
        // 3. Scheduler sends message to DS
        assertTrue(scheduler.canDispatchPendingEvent(), "Scheduler should be able to dispatch before attempting to dispatch");
        scheduler.dispatchPendingEvent();
        // 4. ASSERT DS reception of forwarded message
        msg = drone.receiveMessage();
        boolean actualValue = drone.isAssignment(msg);
        assertTrue(actualValue);
        System.out.printf("Expecting: true, got %s\n", actualValue);
    }

    // Test 4b: Scheduler reads completion and forwards FIRE_ACK to FIS
    @Test
    @Order(7)
    public void test_4b() throws InterruptedException {
        System.out.println("Test 4b: Scheduler forwards DS completion to FIS (FIRE_ACK)");

        fireIncident.setInputCsvPath("./src/test/resources/data/valid_fire_event.csv");
        List<FireEvent> events = fireIncident.loadEventsFromCsv();
        // Drone announces ready to Scheduler.
        drone.sendReadySignal();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());
        // Scheduler dispatch requires an IDLE status update from the drone
        drone.sendUserIdlingUpdate();
        scheduler.handleIncomingMessage(scheduler.receiveSubsystemMessage());
        // FIS sends event to Scheduler
        fireIncident.sendEventsToScheduler(events);
        // Scheduler receives the fire event
        Message msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);
        // Scheduler dispatches to DroneSubsystem.
        assertTrue(scheduler.canDispatchPendingEvent(), "Scheduler should be able to dispatch before attempting to dispatch");
        scheduler.dispatchPendingEvent();
        // DS receive assignment
        Message assignment = drone.receiveMessage();
        assertTrue(drone.isAssignment(assignment));
        FireEvent completedEvent = assignment.getEvent();
        toScheduler.put(Message.droneCompleted(completedEvent));
        // Scheduler receives completion and should forward FIRE_ACK to FIS
        msg = scheduler.receiveSubsystemMessage();
        scheduler.handleIncomingMessage(msg);

        Message ack = fireIncident.receiveMessage();
        assertEquals(Message.Type.FIRE_ACK, ack.getType());
        assertEquals(completedEvent.getZoneId(), ack.getEvent().getZoneId());
        System.out.printf("Expecting msg type: FIRE_ACK, got %s\n", ack.getType());
    }
}
