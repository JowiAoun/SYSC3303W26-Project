import java.util.LinkedList;
import java.util.Queue;

/**
 * MessageBuffer.java
 *
 * Simple monitor-based FIFO buffer for inter-thread communication.
 * Uses synchronized + wait/notifyAll (like the lecture Box/Buffer examples).
 */
public class MessageBuffer {
    private final Queue<Message> queue = new LinkedList<>();

    /**
     * Add a message and wake waiting threads.
     */
    public synchronized void put(Message message) {
        queue.add(message);
        notifyAll();
    }

    /**
     * Remove and return the next message (blocks while empty).
     */
    public synchronized Message get() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        return queue.remove();
    }
}
