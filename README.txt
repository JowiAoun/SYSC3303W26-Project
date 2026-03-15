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

Iteration 2 builds upon the communication infrastructure of Iteration 1 by implementing:
1. Core Scheduling Logic: The Scheduler intelligently dispatches drones, allowing them to service multiple fires in sequence without returning to base if they have sufficient agent.
2. Drone State Machine: The Drone Subsystem implements a full state machine (IDLE, EN_ROUTE, EXTINGUISHING, RETURNING, REFILLING, FAULTED).
3. GUI Integration: The GUI now visualizes the drone's status and location in real-time.

FILES INCLUDED
--------------------------------------------------------------------------------
src/main/java/
  - Main.java: Entry point. Starts all subsystem threads and the GUI.
  - FireIncidentSubsystem.java: Reads fire events from CSV and sends to Scheduler.
  - Scheduler.java: Receives events, tracks drone status, and dispatches assignments.
  - DroneSubsystem.java: Simulates drone operations (travel, fight, refill) using a state machine.
  - DroneState.java: Enum defining possible drone states.
  - DroneStatus.java: Immutable snapshot of drone data (ID, state, location, battery/water).
  - FireEvent.java: Data structure representing a fire event with severity/type.
  - Message.java: Envelope class for inter-subsystem communication.
  - MessageBuffer.java: Thread-safe FIFO buffer for message passing.
  - FireDroneGUI.java: Main GUI window displaying the zone grid and status.
  - ZoneCell.java: GUI component representing a single cell in the grid.
  - ZoneDef.java: Data structure defining the layout of zones on the grid.

HOW IT WORKS
--------------------------------------------------------------------------------
The system operates using three parallel threads and a Swing GUI:

1. **Fire Incident Subsystem**: Reads events from `events.csv` (e.g., FIRE_DETECTED, DRONE_REQUEST) and sends them to the Scheduler.
2. **Scheduler**: Acts as the central brain.
   - Maintains a queue of pending fire events.
   - Tracks the status of the drone (e.g., Is it IDLE? Does it have water?).
   - When the drone is IDLE (at base or remote zone) and has sufficient agent, the Scheduler dispatches the next pending event.
3. **Drone Subsystem**: Simulates the physical drone.
   - **State Machine**: Transitions between states like EN_ROUTE (traveling), EXTINGUISHING (fighting fire), and REFILLING (at base).
   - **Simulation**: Simulates time taken to travel and extinguish fires based on severity and water capacity (15L).
   - **Refilling**: Returns to base only when explicitly commanded by the Scheduler (due to low agent or no pending tasks).
4. **GUI**:
   - Updates in real-time based on messages processed by the Scheduler.
   - Shows active fires (RED cells).
   - Shows drone position and action (e.g., ">>>" for moving, "FIGHT" for extinguishing).

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

HOW TO RUN (Multiple Processes via Command Line)
--------------------------------------------------------------------------------
Build the project in IntelliJ (Ctrl+F9) once before starting the processes.

Linux/Mac (bash):
  mvn -q -DskipTests package
  # Terminal 1 - Scheduler
  java -cp target/classes SchedulerMain --port=5000 --drones=3
  # Terminal 2 - Drone 1
  java -cp target/classes DroneMain --id=1 --schedulerHost=localhost --schedulerPort=5000
  # Terminal 3 - Drone 2
  java -cp target/classes DroneMain --id=2 --schedulerHost=localhost --schedulerPort=5000
  # Terminal 4 - Fire Incident
  java -cp target/classes FireIncidentMain --input=./src/main/resources/data/events.csv --schedulerHost=localhost --schedulerPort=5000 --localPort=6000

Windows (PowerShell):
  # Terminal 1 - Scheduler
  java -cp target\classes SchedulerMain --port=5000 --drones=3
  # Terminal 2 - Drone 1
  java -cp target\classes DroneMain --id=1 --schedulerHost=localhost --schedulerPort=5000
  # Terminal 3 - Drone 2
  java -cp target\classes DroneMain --id=2 --schedulerHost=localhost --schedulerPort=5000
  # Terminal 4 - Fire Incident
  java -cp target\classes FireIncidentMain --input=.\src\main\resources\data\events.csv --schedulerHost=localhost --schedulerPort=5000 --localPort=6000

UNIT TESTING
--------------------------------------------------------------------------------
JUnit 5 was used as the testing framework.
Unit tests written for the project can be found in folder src/test/java/*.
To run all tests in one-shot, in IntelliJ IDEA, right click on "src/test/java" folder, and click "Run 'All Test'".

src/test/java/MainTest.java
  - Test 1a: Verify FireIncidentSubsystem properly reads and handles valid input events
  - Test 1b: Verify FireIncidentSubsystem properly reads and handles invalid
  - Test 2: Verify FireIncidentSubsystem sends valid input to Scheduler
  - Test 3a: Verify DroneSubsystem contacts Scheduler and handles NO tasks/fires to put out properly
  - Test 3b: Verify DroneSubsystem contacts Scheduler and handles HAS tasks/fires to put out properly
  - Test 4a: Verify Scheduler reads messages from FireIncidentSubsystem and forwards to DroneSubsystem
  - Test 4b: Verify Scheduler reads messages from DroneSubsystem and forwards to FireIncidentSubsystem

src/test/java/DroneStateMachineTest.java
  - Test 1: Verify DroneSubsystem sends EN_ROUTE shortly after receiving an assignment
  - Test 2: Verify when tank is empty, drone sends RETURNING then REFILLING
  - Test 3: Verify drone eventually finishes an assignment by sending DRONE_COMPLETED or returning to IDLE

src/test/java/SchedulerDispatchTest.java
  - Test 1: Verify Scheduler does not dispatch a pending event when drone is not IDLE
  - Test 2: Verify Scheduler dispatches when drone is IDLE and a pending event exists, and sends DRONE_ASSIGNMENT to drone buffer
  - Test 3: Verify Scheduler dispatches a queued fire event after drone transitions from busy to IDLE

BREAKDOWN OF RESPONSIBILITIES
--------------------------------------------------------------------------------

Iteration 1
-----------
Zachary: Graphical user interface
Amir: Source code
Thanos: Testing
Jowi: Documentation and Diagrams

Iteration 2
-----------
Zachary: Testing
Amir: GUI
Thanos: Documentation and Diagrams
Jowi: Source code

Iteration 3
-----------
Zachary: Documentation and Diagrams, Source code
Amir: Testing, Source code
Thanos: Source code, Documentation
Jowi: GUI, Source code

