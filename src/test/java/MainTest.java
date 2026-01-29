import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

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

    // Launch threads.
    private Thread fireThread;
    private Thread droneThread;
    private Thread schedulerThread;

    @BeforeEach
    public void setup() {
        inputPath = "./src/test/resources/data/events.csv";

        // Message buffers
        toScheduler = new MessageBuffer();
        schedulerToFire = new MessageBuffer();
        schedulerToDrone = new MessageBuffer();

        // Build subsystems.
        fireIncident = new FireIncidentSubsystem(inputPath, toScheduler, schedulerToFire);
        drone = new DroneSubsystem(toScheduler, schedulerToDrone);
        scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone);

        // Launch threads.
        fireThread = new Thread(fireIncident, "FireIncidentSubsystem");
        droneThread = new Thread(drone, "DroneSubsystem");
        schedulerThread = new Thread(scheduler, "Scheduler");
    }

    @AfterEach
    public void spacing() {
        System.out.println();
    }

    // Test 1a: Verify Fire Incident Subsystem reads valid input events
    @Test
    @Order(1)
    public void test_1a() {
        System.out.println("Test 1a: Verify Fire Incident Subsystem reads valid input events");
        // FYI fireIncident.readInputEvent is a dummy method
        assertTrue(fireIncident.readInputEvent);
        System.out.printf("Expecting: true, got %s", fireIncident.readInputEvent);
    }

    // Test 1b: Verify FIS handles invalid input events
    @Test
    @Order(2)
    public void test_1b() {
        System.out.println("Test 1b: Verify FIS handles invalid input events");
        // invalid_events_data.csv -> a csv file containing invalid input
        inputPath = "./src/test/resources/data/invalid_events_data.csv";
        assertFalse(fireIncident.readInputEvent);
        System.out.printf("Expecting: true, got %s", fireIncident.readInputEvent);
    }

    // Test 2: Verify FIS sends valid input to Scheduler
    @Test
    @Order(3)
    public void test_2() {
        System.out.println("Test 2: Verify FIS sends valid input to Scheduler");
    }

    // Test 3a: Verify Drone Subsystem contacts Scheduler - Handles NO tasks/fires to put out properly
    @Test
    @Order(4)
    public void test_3a() {
        System.out.println("Test 3a: Verify Drone Subsystem contacts Scheduler - Handles NO tasks/fires to put out properly");
    }

    // Test 3b: Verify Drone Subsystem contacts Scheduler - Handles HAS tasks/fires to put out properly
    @Test
    @Order(5)
    public void test_3b() {
        System.out.println("Test 3b: Verify Drone Subsystem contacts Scheduler - Handles HAS tasks/fires to put out properly");
    }

    // Test 4a: Verify Scheduler reads messages from FIS and forwards to DS
    @Test
    @Order(6)
    public void test_4a() {
        System.out.println("Test 4a: Verify Scheduler reads messages from FIS and forwards to DS");
    }

    // Test 4b: Verify Scheduler reads messages from DS and forwards to FIS
    @Test
    @Order(7)
    public void test_4b() {
        System.out.println("Test 4b: Verify Scheduler reads messages from DS and forwards to FIS");
    }

    public static void main(String[] args) {}
}
