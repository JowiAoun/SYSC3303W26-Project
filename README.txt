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
This project implements a Firefighting Drone System as specified in the SYSC 3033A
Winter 2026 Project Specification.
Iteration 1 focuses on establishing clear communication between three main subsystems:
1. Fire Incident Subsystem (Producer/Client)
2. Scheduler (Server/Mediator)
3. Drone Subsystem (Consumer/Client)

FILES INCLUDED
--------------------------------------------------------------------------------
src/main/java/
  - Main.java: Entry point. Starts all subsystem threads.
  - FireIncidentSubsystem.java: Reads fire events from input and sends to Scheduler.
  - Scheduler.java: Receives events and coordinates with Drones.
  - DroneSubsystem.java: Simulates drone operations.
  - FireEvent.java: Data structure representing a fire event.
  - Message*.java: Communication helpers.
  - GUI/*.java: Initial GUI structure components.

src/main/resources/data/
  - events.csv: Input file containing fire detected and drone request events.

docs/
  - project-specification.pdf: Project requirements.
  - diagrams/: UML Class and Sequence diagrams.

HOW TO RUN (IntelliJ IDEA)
--------------------------------------------------------------------------------
1. Open the project in IntelliJ IDEA.
2. Navigate to 'src/main/java/Main.java'.
3. Locate the green "Play" button (Run 'Main') next to the 'public class Main'
   line or the 'main' method.
4. Click the green "Play" button.

Alternatively, right-click 'Main.java' in the Project view and select "Run 'Main'".

SETUP INSTRUCTIONS
--------------------------------------------------------------------------------
1. Ensure Java SDK is properly configured in Project Structure.
2. Mark 'src' as Sources Root if not already detected.
3. Ensure 'src/main/resources' is marked as Resources Root.

TESTING INSTRUCTIONS
--------------------------------------------------------------------------------
The system will run automatically upon start.
1. The FireIncidentSubsystem reads events from 'events.csv'.
2. Observe the console output. You should see logs indicating:
   - FireIncidentSubsystem sending events to Scheduler.
   - Scheduler receiving events and forwarding/scheduling.
   - DroneSubsystem receiving tasks.
3. The program concludes after processing all events in the file.

BREAKDOWN OF RESPONSIBILITIES
--------------------------------------------------------------------------------

Iteration 1
-----------
Zachary: Graphical user interface
Amir: Source code
Thanos: Testing
Jowi: Documentation and diagrams
