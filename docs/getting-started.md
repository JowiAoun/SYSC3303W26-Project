# Getting started

Build, run, and test Firefly, the firefighting drone swarm. For an overview of the project, see
the main [README](../README.md).

## Requirements

- **Java 21** (JDK)
- **IntelliJ IDEA** (the project is set up for it) or **Maven**

The two dependencies, FlatLaf and JUnit 5, are declared in `pom.xml` and are pulled in
by Maven or IntelliJ automatically.

## Run the full simulation (one process)

This is the all in one launcher that opens the dispatcher console shown in the README.

**In IntelliJ IDEA:**

1. Open the project.
2. Open `src/main/java/Main.java`.
3. Click the green Run button next to `public class Main`, or right click `Main.java`
   in the Project view and choose **Run 'Main'**.

It starts the Scheduler, the Fire Incident Subsystem, and 10 drones, then opens the GUI.
The fire events come from `src/main/resources/data/events.csv` and the zone layout from
`src/main/resources/data/zones.csv`.

## Run as separate processes (distributed, over UDP)

Build the project first (in IntelliJ, use Build Project, or run `mvn compile`). Then start
each subsystem in its own terminal.

**Linux or macOS:**

```bash
# Terminal 1: Scheduler
java -cp target/classes SchedulerMain --port=5000 --drones=3

# Terminal 2..N: one Drone per terminal
java -cp target/classes DroneMain --id=1 --schedulerHost=localhost --schedulerPort=5000
java -cp target/classes DroneMain --id=2 --schedulerHost=localhost --schedulerPort=5000
java -cp target/classes DroneMain --id=3 --schedulerHost=localhost --schedulerPort=5000

# Last terminal: Fire Incident Subsystem
java -cp target/classes FireIncidentMain \
  --input=./src/main/resources/data/events.csv \
  --schedulerHost=localhost --schedulerPort=5000 --localPort=6000
```

**Windows (PowerShell):** use the same commands with `target\classes` and
`.\src\main\resources\data\events.csv`.

## Input files

**`events.csv`** drives the fires. Each row is `time, zoneId, eventType, severity,
faultType, faultDelayTime`:

```
time,zoneId,eventType,severity,faultType,faultDelayTime
14:03:15.000,3,FIRE_DETECTED,High,NONE,0
14:10:00.000,4,DRONE_REQUEST,Moderate,NONE,0
```

- `eventType` is `FIRE_DETECTED` or `DRONE_REQUEST`.
- `severity` is `Low`, `Moderate`, or `High`, which need 10 L, 20 L, and 30 L of agent.
- `faultType` injects a fault: `NONE`, `STUCK_MID_FLIGHT`, `NOZZLE_JAM`,
  `ARRIVAL_SENSOR_FAILURE`, or `CORRUPTED_MESSAGE`.

**`zones.csv`** sets the zone rectangles as `Zone ID, Zone Start, Zone End`.

## Run the tests

JUnit 5 tests live in `src/test/java`.

- **IntelliJ:** right click the `src/test/java` folder and choose **Run 'All Tests'**.
- **Maven:** run `mvn test`.

The tests cover input parsing, message passing over UDP, the drone state machine,
scheduler dispatch, and each fault type.
