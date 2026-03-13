import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PacketContentTest {

    private DatagramSocket receiverSocket;
    private DatagramSocket senderSocket;

    @BeforeEach
    public void setup() {
        try {
            receiverSocket = new DatagramSocket(0);
            senderSocket = new DatagramSocket();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    public void teardown() {
        if (senderSocket != null) senderSocket.close();
        if (receiverSocket != null) receiverSocket.close();
        System.out.println();
    }

    @Test
    @Order(1)
    public void test_1() throws Exception {
        System.out.println("Test 1: UDP packet content round-trip");

        // 1. Build a fire event and wrap it in a Message
        FireEvent event = new FireEvent("00:00:01", 7, FireEvent.EventType.FIRE_DETECTED, FireEvent.Severity.MODERATE);
        Message outgoing = Message.fireEvent(event);

        // 2. Send message over UDP to a receiver socket
        SwarmNetwork.sendMessage(senderSocket, InetAddress.getByName(SwarmNetwork.LOCALHOST),
                receiverSocket.getLocalPort(), outgoing, "[Test]", "sent", "to Receiver");

        // 3. Receive message and verify contents
        Message incoming = SwarmNetwork.receiveMessage(receiverSocket);

        assertEquals(Message.Type.FIRE_EVENT, incoming.getType());
        assertNotNull(incoming.getEvent());
        assertEquals(event.getTime(), incoming.getEvent().getTime());
        assertEquals(event.getZoneId(), incoming.getEvent().getZoneId());
        assertEquals(event.getEventType(), incoming.getEvent().getEventType());
        assertEquals(event.getSeverity(), incoming.getEvent().getSeverity());

        System.out.printf("Expecting type: FIRE_EVENT, got %s\n", incoming.getType());
        System.out.printf("Expecting zoneId: %d, got %d\n", event.getZoneId(), incoming.getEvent().getZoneId());
    }
}
