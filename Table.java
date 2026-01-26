/**
 * Java monitor for drone component assembly coordination.
 */
public class Table {
    private int numFrames = 0;
    private int numPropulsion = 0;
    private int numFirmware = 0;
    private int dronesAssembled = 0;
    private static final int MAX_DRONES = 20;

    public synchronized boolean add(Component component) {
        switch (component) {
            case FRAME: numFrames++; break;
            case PROPULSION: numPropulsion++; break;
            case FIRMWARE: numFirmware++; break;
        }
        System.out.println("  [Table] Added " + component + " - " + tableInfo());
        return true;
    }

    public synchronized boolean take(Component component) {
        switch (component) {
            case FRAME: if (numFrames > 0) { numFrames--; return true; } break;
            case PROPULSION: if (numPropulsion > 0) { numPropulsion--; return true; } break;
            case FIRMWARE: if (numFirmware > 0) { numFirmware--; return true; } break;
        }
        return false;
    }

    public synchronized boolean assemble(Component missingComponent) {
        while (!shouldAssemble(missingComponent) && dronesAssembled < MAX_DRONES) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        if (dronesAssembled >= MAX_DRONES) {
            notifyAll();
            return false;
        }
        
        for (Component c : Component.values()) {
            if (c != missingComponent) take(c);
        }
        
        dronesAssembled++;
        System.out.println("  [Table] Components taken, table is now empty - " + tableInfo());
        notifyAll();
        return true;
    }

    private boolean shouldAssemble(Component techComp) {
        switch (techComp) {
            case FRAME: return numPropulsion > 0 && numFirmware > 0;
            case PROPULSION: return numFrames > 0 && numFirmware > 0;
            case FIRMWARE: return numFrames > 0 && numPropulsion > 0;
            default: return false;
        }
    }

    public synchronized boolean has(Component component) {
        switch (component) {
            case FRAME: return numFrames > 0;
            case PROPULSION: return numPropulsion > 0;
            case FIRMWARE: return numFirmware > 0;
            default: return false;
        }
    }

    public synchronized boolean isEmpty() {
        return numFrames == 0 && numPropulsion == 0 && numFirmware == 0;
    }

    public synchronized String tableInfo() {
        return String.format("[Frames: %d, Propulsion: %d, Firmware: %d]", 
                             numFrames, numPropulsion, numFirmware);
    }

    public synchronized int getDronesAssembled() { return dronesAssembled; }
    public synchronized boolean isComplete() { return dronesAssembled >= MAX_DRONES; }

    public synchronized boolean waitUntilEmpty() {
        while (!isEmpty() && dronesAssembled < MAX_DRONES) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return dronesAssembled < MAX_DRONES;
    }

    public synchronized void notifyTechnicians() { notifyAll(); }
}
