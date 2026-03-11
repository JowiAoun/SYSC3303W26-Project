import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SwarmNetwork {
    public static final int SCHEDULER_PORT = 5000;
    public static final int FIS_PORT = 6000;
    public static final String LOCALHOST = "localhost";
    private static final int BUF_SIZE = 2048;

    /**
     * Wrapper class for a received message plus its sender address/port.
     */
    public static class ReceivedMessage {
        private final Message message;
        private final InetAddress address;
        private final int port;

        public ReceivedMessage(Message message, InetAddress address, int port) {
            this.message = message;
            this.address = address;
            this.port = port;
        }

        public Message getMessage() {
            return message;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }
    }

    /**
     * Shared packet-print helper.
     */
    private static void printPacket(String prefix, String action, String peerLabel, DatagramPacket p, String containing, byte[] rawBytes) {
        System.out.println("\n" + prefix + " " + action);
        System.out.println(prefix + " " + peerLabel + ": " + p.getAddress() + ":" + p.getPort());
        System.out.println(prefix + " Length: " + p.getLength());
        System.out.println(prefix + " Containing: " + containing);
        System.out.println(prefix + " Bytes: " + Arrays.toString(rawBytes));
    }

    /**
     * Generic send helper.
     * Example calls:
     * sendMessage(socket, addr, port, msg, "[Drone]", "Sent", "to Scheduler");
     * sendMessage(socket, addr, port, msg, "[Scheduler]", "Forwarded", "to Drone");
     */
    public static void sendMessage(DatagramSocket socket, InetAddress addr, int port, Message msg, String prefix, String action, String peerLabel) throws Exception {
        byte[] msgBytes = msg.toBytes();
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, addr, port);
        printPacket(prefix, action, peerLabel, packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);
        socket.send(packet);
    }

    /**
     * Receive and return only the decoded Message.
     * Example call:
     * Message msg = receiveMessage(socket, "[Scheduler]", "from Drone");
     */
    public static Message receiveMessage(DatagramSocket socket) throws Exception {
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        byte[] msgBytes = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength());
        Message msg = Message.fromBytes(msgBytes, msgBytes.length);
//        printPacket(prefix, "Received", peerLabel, packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);

        return msg;
    }

    /**
     * Receive and return both the decoded Message and sender info.
     * Useful for Scheduler to know who sent the packet.
     */
    public static ReceivedMessage receiveMessageWithSource(DatagramSocket socket) throws Exception {
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        byte[] msgBytes = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength());
        Message msg = Message.fromBytes(msgBytes, msgBytes.length);
//        printPacket(prefix, "Received", peerLabel, packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);

        return new ReceivedMessage(msg, packet.getAddress(), packet.getPort());
    }

}
