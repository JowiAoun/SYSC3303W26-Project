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

    private static void printPacket(String prefix, String action, String peerLabel, DatagramPacket p, String containing, byte[] rawBytes) {
        System.out.println("\n" + prefix + " " + action);
        System.out.println(prefix + " " + peerLabel + ": " + p.getAddress() + ":" + p.getPort());
        System.out.println(prefix + " Length: " + p.getLength());
        System.out.println(prefix + " Containing: " + containing);
        System.out.println(prefix + " Bytes: " + Arrays.toString(rawBytes));
    }

    // Drone Subsystem (Client) - Send request packet
    public static void sendReq(DatagramSocket socket, InetAddress addr, int port, Message msg) throws Exception {
        byte[] msgBytes = msg.toBytes();
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, addr, port);
        printPacket("[Drone]", "Request", "to Scheduler", packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);
        socket.send(packet);
    }

    // Scheduler (Host) - Forward request from Drone to FIS
    public static void forwardReq(DatagramSocket socket, InetAddress addr, int port, Message msg) throws Exception {
        byte[] msgBytes = msg.toBytes();
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, addr, port);
        printPacket("[Scheduler]", "Forwarded", "to FIS", packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);
        socket.send(packet);
    }

    // Drone/Scheduler/FIS - Receive sent/forwarded packets
    public static Message receiveMsg(DatagramSocket socket, String dstId, String srcId) throws Exception {
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] msgBytes = Arrays.copyOfRange(
                packet.getData(),
                packet.getOffset(),
                packet.getOffset()+ packet.getLength());
        Message msg = Message.fromBytes(msgBytes, msgBytes.length);
        printPacket("[" + dstId + "]", "Received", "from " + srcId, packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);
        return msg;
    }

    // Fire Incident Subsystem (Server) - send response packet to Scheduler
    public static void sendResp(DatagramSocket socket, InetAddress addr, int port, Message respMsg) throws Exception {
        byte[] msgBytes = respMsg.toBytes();
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, addr, port);
        printPacket("[FIS]", "Response", "to Scheduler", packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);
        socket.send(packet);
    }

    // Scheduler - Forward response from FIS to Drone
    public static void forwardResp(DatagramSocket socket, InetAddress addr, int port, Message msg) throws Exception {
        byte[] msgBytes = msg.toBytes();
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, addr, port);
        printPacket("[Scheduler]", "Forwarded", "to Drone", packet, new String(msgBytes, StandardCharsets.UTF_8), msgBytes);
        socket.send(packet);
    }

    // FIS - handle a request packet
    public static String handleReq(String reqStr) {
        reqStr = reqStr.trim();
        // TODO: 'if (reqStr.startsWith(DroneState))' code blocks

        return "DEBUG: " + reqStr;
//        return "ERROR: INVALID_REQUEST";
    }


}
