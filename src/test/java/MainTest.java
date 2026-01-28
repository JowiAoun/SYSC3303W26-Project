import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

public class MainTest {
    String inputPath = "./src/main/resources/data/events.csv";

    @BeforeEach
    public void setup() {
        // Message buffers
        MessageBuffer toScheduler = new MessageBuffer();
        MessageBuffer schedulerToFire = new MessageBuffer();
        MessageBuffer schedulerToDrone = new MessageBuffer();

        // Build subsystems.
        FireIncidentSubsystem fireIncident = new FireIncidentSubsystem(inputPath, toScheduler, schedulerToFire);
        DroneSubsystem drone = new DroneSubsystem(toScheduler, schedulerToDrone);
        Scheduler scheduler = new Scheduler(toScheduler, schedulerToFire, schedulerToDrone);

        // Launch threads.
        Thread fireThread = new Thread(fireIncident, "FireIncidentSubsystem");
        Thread droneThread = new Thread(drone, "DroneSubsystem");
        Thread schedulerThread = new Thread(scheduler, "Scheduler");
    }

    @AfterEach
    public void spacing() {
        System.out.println();
    }

    // Test 1a: Verify Fire Incident Subsystem reads valid input events

    // Test 1b: Verify FIS handles invalid input events

    // Test 2: Verify FIS sends valid input to Scheduler

    // Test 3a: Verify Drone Subsystem contacts Scheduler - Handles NO tasks/fires to put out properly

    // Test 3b: Verify Drone Subsystem contacts Scheduler - Handles HAS tasks/fires to put out properly

    // Test 4a: Verify Scheduler reads messages from FIS and forwards to DS

    // Test 4b: Verify Scheduler reads messages from DS and forwards to FIS

    public static void main(String[] args) {}
}
