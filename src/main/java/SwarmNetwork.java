import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class SwarmNetwork {
    private static final int HOST_PORT = 5000;
    private static final int SERVER_PORT = 6000;
    private static final int BUF_SIZE = 2048;

    private static void printPacket(String prefix, String action, String peerLabel, DatagramPacket p, String containing, byte[] rawBytes) {
        System.out.println("\n" + prefix + " " + action);
        System.out.println(prefix + " " + peerLabel + ": " + p.getAddress() + ":" + p.getPort());
        System.out.println(prefix + " Length: " + p.getLength());
        System.out.println(prefix + " Containing: " + containing);
        System.out.println(prefix + " Bytes: " + Arrays.toString(rawBytes));
    }

    // Client (Drone Subsystem) and Host (Scheduler) Methods
    public static void sendMsg(DatagramSocket socket, InetAddress addr, int port, String msg, String srcId, String dstId) throws Exception {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length, addr, port);
        printPacket("[" + srcId + "]", "Sent", "to " + dstId, packet, msg, msgBytes);
        socket.send(packet);
    }

    public static String receiveResp(DatagramSocket socket, String srcId, String dstId) throws Exception {
        byte[] buf = new byte[BUF_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);
        byte[] respBytes = Arrays.copyOfRange(
                packet.getData(),
                packet.getOffset(),
                packet.getOffset()+ packet.getLength());
        String respStr = new String(respBytes, StandardCharsets.UTF_8).trim();
        printPacket("[" + srcId + "]", "Received", "from " + dstId, packet, respStr, respBytes);
        return respStr;
    }

    // Server (FIS) Methods
    public static String handleReq(String reqStr) {
        reqStr = reqStr.trim();
        // TODO: 'if (reqStr.startsWith(DroneState))' code blocks

        return "ERROR: INVALID_REQUEST";
    }
}
