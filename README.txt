SYSC3303 W26 Project
Firefighting Drone System

TEAM #1 MEMBERS
--------------------------------------------------------------------------------
Zachary Gallant - 101272210
Amir Dedeic - 101266477
Thanos Jia - 101189709
Jowi Aoun - 101272429

*See team responsibility history at the bottom of this text file.

PROJECT DESCRIPTION
--------------------------------------------------------------------------------
This project implements a Firefighting Drone System as specified in the SYSC 3303A
Winter 2026 Project Specification.
Iteration 1 focuses on establishing clear communication between three main subsystems:
1. Fire Incident Subsystem (Producer/Client)
2. Scheduler (Server/Mediator)
3. Drone Subsystem (Consumer/Client)

UML Diagrams for Iteration 1 can be found in Diagrams.pdf.

FILES INCLUDED
--------------------------------------------------------------------------------
src/main/java/
  - Main.java: Entry point. Starts all subsystem threads.
  - FireIncidentSubsystem.java: Reads fire events from CSV and sends to Scheduler.
  - Scheduler.java: Receives events and coordinates with Drones.
  - DroneSubsystem.java: Simulates drone operations and reports completion.
  - FireEvent.java: Data structure representing a fire event with severity/type.
  - Message.java: Envelope class for inter-subsystem communication.
  - MessageBuffer.java: Thread-safe FIFO buffer for message passing.
  - GUI/*.java: Initial GUI structure components (FireDroneGUI, ZoneCell, ZoneDef).

src/main/resources/data/
  - events.csv: Input file containing fire detected and drone request events.

src/test/java/
  - MainTest.java: Unit tests for subsystem communication and event processing.

docs/
  - project-specification.pdf: Project requirements.
  - diagrams/: UML Class and Sequence diagrams.
  - Diagrams.pdf: Rendered diagrams.

SETUP INSTRUCTIONS
--------------------------------------------------------------------------------
1. Ensure Java SDK is properly configured in Project Structure.
2. Unzip this directory with 'unzip L3G1_milestone_*.zip'

HOW TO RUN (IntelliJ IDEA)
--------------------------------------------------------------------------------
1. Open the project in IntelliJ IDEA.
2. Navigate to 'src/main/java/Main.java'.
3. Locate the green "Play" button (Run 'Main') next to the 'public class Main'
   line or the 'main' method.
4. Click the green "Play" button.

Alternatively, right-click 'Main.java' in the Project view and select "Run 'Main'".

UNIT TESTING
--------------------------------------------------------------------------------
JUnit 5 was used as the testing framework.
Unit tests written for the project can be found in src/test/java/MainTest.java.
To run all tests in one-shot, in IntelliJ IDEA, right click on "MainTest.java", and click "Run 'MainTest'".

Test 1a: Verify FireIncidentSubsystem properly reads and handles valid input events
Test 1b: Verify FireIncidentSubsystem properly reads and handles invalid input events
Test 2: Verify FireIncidentSubsystem sends valid input to Scheduler
Test 3a: Verify DroneSubsystem contacts Scheduler and handles NO tasks/fires to put out properly
Test 3b: Verify DroneSubsystem contacts Scheduler and handles HAS tasks/fires to put out properly
Test 4a: Verify Scheduler reads messages from FireIncidentSubsystem and forwards to DroneSubsystem
Test 4b: Verify Scheduler reads messages from DroneSubsystem and forwards to FireIncidentSubsystem

BREAKDOWN OF RESPONSIBILITIES
--------------------------------------------------------------------------------

Iteration 1
-----------
Zachary: Graphical user interface
Amir: Source code
Thanos: Testing
Jowi: Documentation and diagrams
