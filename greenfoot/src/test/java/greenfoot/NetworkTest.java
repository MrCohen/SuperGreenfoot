/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2026 SuperGreenfoot contributors

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 This file is subject to the Classpath exception as provided in the
 LICENSE.txt file that accompanied this code.
 */
package greenfoot;

import junit.framework.TestCase;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * The network module: a real server and real clients on the loopback
 * interface, checked against the contract the exemplar game relies on -
 * events in order per connection, sends that never block, bounded queues
 * that drop rather than stall, ids never reused, reasons in words, and
 * byte counts from the wire. The JDK's own WebSocket client is used as an
 * independent check that the server really speaks WebSocket.
 */
public class NetworkTest extends TestCase
{
    private static final int WAIT_MS = 8000;

    private final List<NetServer> servers = new ArrayList<>();
    private final List<NetClient> clients = new ArrayList<>();

    @Override
    protected void setUp()
    {
        // Another test class may have left a paused Simulation behind: these cases are about a running host.
        greenfoot.net.Link.setHostPausedCheck(() -> false);
    }

    @Override
    protected void tearDown()
    {
        for (NetClient c : clients) {
            c.close();
        }
        for (NetServer s : servers) {
            s.stop();
        }
        Network.closeAll();
    }

    private NetServer server(int max)
    {
        NetServer s = Network.startServer(0, max);
        assertNull("server should start: " + s.getError(), s.getError());
        assertTrue(s.getPort() > 0);
        servers.add(s);
        return s;
    }

    private NetClient client(NetServer s)
    {
        NetClient c = Network.connect("localhost:" + s.getPort());
        clients.add(c);
        return c;
    }

    private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timed out waiting for " + what);
            }
            Thread.sleep(5);
        }
    }

    /** Poll until an event arrives, or fail. */
    private static NetEvent next(NetServer s) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            NetEvent e = s.poll();
            if (e != null) {
                return e;
            }
            Thread.sleep(2);
        }
        fail("no event from the server");
        return null;
    }

    private static NetEvent next(NetClient c) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            NetEvent e = c.poll();
            if (e != null) {
                return e;
            }
            Thread.sleep(2);
        }
        fail("no event from the client");
        return null;
    }

    private static void assertEvent(int type, String text, NetEvent e)
    {
        assertNotNull(e);
        assertEquals("event type of " + e, type, e.getType());
        if (text != null) {
            assertEquals("event text of " + e, text, e.getText());
        }
    }

    // ---- the basics ----

    public void testMessagesBothWaysInOrder() throws Exception
    {
        NetServer s = server(8);
        NetClient c = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(c));
        assertTrue(c.isConnected());
        assertEquals(NetClient.CONNECTED, c.getStatus());
        NetEvent joined = next(s);
        assertEvent(NetEvent.CONNECTED, "", joined);
        int id = joined.getConnectionId();
        assertEquals(1, s.getConnectionCount());

        for (int i = 0; i < 50; i++) {
            c.send("up " + i);
        }
        for (int i = 0; i < 50; i++) {
            NetEvent e = next(s);
            assertEvent(NetEvent.MESSAGE, "up " + i, e);
            assertEquals(id, e.getConnectionId());
        }
        for (int i = 0; i < 50; i++) {
            if (i % 2 == 0) {
                s.send(id, "down " + i);
            }
            else {
                s.broadcast("down " + i);
            }
        }
        for (int i = 0; i < 50; i++) {
            assertEvent(NetEvent.MESSAGE, "down " + i, next(c));
        }
        // Unicode survives, whole.
        c.send("héllo ☃ 日本語");
        assertEvent(NetEvent.MESSAGE, "héllo ☃ 日本語", next(s));

        // The counters are the same bytes seen from both ends of the wire.
        waitFor("bytes to settle", () -> s.getBytesReceived() == c.getBytesSent()
                && c.getBytesReceived() == s.getBytesSent());
        assertTrue("bytes counted", s.getBytesSent() > 0 && s.getBytesReceived() > 0);
        assertEquals(0, s.getPendingBytes(id));
        assertEquals(0, c.getPendingBytes());
    }

    public void testKickDeliversLastMessageThenReason() throws Exception
    {
        NetServer s = server(8);
        NetClient c = client(s);
        int id = next(s).getConnectionId();
        assertEvent(NetEvent.CONNECTED, "", next(c));

        s.send(id, "KICK;the game is full");
        s.kick(id, "the game is full");
        assertEvent(NetEvent.MESSAGE, "KICK;the game is full", next(c));
        assertEvent(NetEvent.DISCONNECTED, "the game is full", next(c));
        waitFor("client closed", () -> c.getStatus() == NetClient.CLOSED);
        assertEquals("the game is full", c.getError());
        assertNull("nothing after DISCONNECTED", c.poll());

        NetEvent left = next(s);
        assertEvent(NetEvent.DISCONNECTED, "the game is full", left);
        assertEquals(id, left.getConnectionId());
        assertEquals(0, s.getConnectionCount());

        // Ids are never reused.
        NetClient c2 = client(s);
        NetEvent joined2 = next(s);
        assertEvent(NetEvent.CONNECTED, "", joined2);
        assertTrue("new id " + joined2.getConnectionId() + " after " + id, joined2.getConnectionId() > id);
        assertEvent(NetEvent.CONNECTED, "", next(c2));
    }

    public void testClientCloseReachesServerWithReason() throws Exception
    {
        NetServer s = server(8);
        NetClient c = client(s);
        int id = next(s).getConnectionId();
        assertEvent(NetEvent.CONNECTED, "", next(c));
        c.send("BYE");
        c.close("leaving");
        assertEvent(NetEvent.MESSAGE, "BYE", next(s));
        NetEvent left = next(s);
        assertEvent(NetEvent.DISCONNECTED, "leaving", left);
        assertEquals(id, left.getConnectionId());
        assertEvent(NetEvent.DISCONNECTED, "leaving", next(c));
    }

    public void testServerStopTellsEveryone() throws Exception
    {
        NetServer s = server(8);
        NetClient a = client(s);
        NetClient b = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(a));
        assertEvent(NetEvent.CONNECTED, "", next(b));
        assertEvent(NetEvent.CONNECTED, "", next(s));
        assertEvent(NetEvent.CONNECTED, "", next(s));
        s.stop();
        assertFalse(s.isListening());
        assertEvent(NetEvent.DISCONNECTED, "server stopped", next(a));
        assertEvent(NetEvent.DISCONNECTED, "server stopped", next(b));
        assertEvent(NetEvent.DISCONNECTED, "server stopped", next(s));
        assertEvent(NetEvent.DISCONNECTED, "server stopped", next(s));
        NetClient late = client(s);
        waitFor("late client to fail", () -> late.getStatus() == NetClient.FAILED);
        assertTrue(late.getError(), late.getError().contains("refused"));
    }

    // ---- failures, in words ----

    public void testConnectFailuresAreExplained() throws Exception
    {
        NetServer probe = server(1);
        int freePort = probe.getPort();
        probe.stop();
        NetClient refused = Network.connect("localhost:" + freePort);
        clients.add(refused);
        waitFor("refused", () -> refused.getStatus() == NetClient.FAILED);
        assertTrue(refused.getError(), refused.getError().contains("connection refused"));
        assertNull(refused.poll());

        NetClient bad = Network.connect("not an address at all");
        assertEquals(NetClient.FAILED, bad.getStatus());
        assertTrue(bad.getError(), bad.getError().startsWith("not an address"));

        NetClient noPort = Network.connect("localhost");
        assertEquals(NetClient.FAILED, noPort.getStatus());

        NetClient noHost = Network.connect("no-such-host.invalid:7777");
        clients.add(noHost);
        waitFor("no such host", () -> noHost.getStatus() == NetClient.FAILED);
        assertTrue(noHost.getError(), noHost.getError().startsWith("no such host"));
    }

    public void testPortInUseIsReported() throws Exception
    {
        NetServer first = server(8);
        NetServer second = Network.startServer(first.getPort());
        assertFalse(second.isListening());
        assertEquals(-1, second.getPort());
        assertTrue(second.getError(), second.getError().contains("already in use"));
        NetServer third = Network.startServer(-5);
        assertTrue(third.getError(), third.getError().contains("not a valid port"));
    }

    public void testServerFullIsToldWhy() throws Exception
    {
        NetServer s = server(1);
        NetClient a = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(a));
        assertEvent(NetEvent.CONNECTED, "", next(s));
        NetClient b = client(s);
        waitFor("b to be refused", () -> b.getStatus() == NetClient.FAILED);
        assertEquals("the server is full", b.getError());
        assertNull("a refused client never reports CONNECTED", b.poll());
        assertNull("the server never reports a refused connection", s.poll());
        assertEquals(1, s.getConnectionCount());

        // Room again once someone leaves.
        a.close("done");
        assertEvent(NetEvent.DISCONNECTED, "done", next(s));
        NetClient c = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(c));
        assertEvent(NetEvent.CONNECTED, "", next(s));
    }

    public void testSendNullIsExplained() throws Exception
    {
        NetServer s = server(8);
        NetClient c = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(c));
        int id = next(s).getConnectionId();
        try {
            c.send(null);
            fail("null should be refused");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("null"));
        }
        try {
            s.broadcast(null);
            fail("null should be refused");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("null"));
        }
        s.send(id, "still fine");
        assertEvent(NetEvent.MESSAGE, "still fine", next(c));
    }

    public void testCloseAllFreesThePort() throws Exception
    {
        NetServer s = server(8);
        int port = s.getPort();
        NetClient c = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(c));
        Network.closeAll();
        assertFalse(s.isListening());
        waitFor("client closed by reset", () -> c.getStatus() == NetClient.CLOSED);
        NetServer again = Network.startServer(port);
        servers.add(again);
        assertNull(again.getError(), again.getError());
        assertEquals(port, again.getPort());
    }

    // ---- bounds ----

    public void testMessageTooLongIsRefusedBeforeSending() throws Exception
    {
        NetServer s = server(8);
        NetClient c = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(c));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i <= Network.MAX_MESSAGE_LENGTH; i++) {
            big.append('x');
        }
        try {
            c.send(big.toString());
            fail("should refuse a message over the limit");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("too long"));
        }
        // Exactly at the limit is fine.
        c.send(big.substring(1));
        assertEvent(NetEvent.CONNECTED, "", next(s));
        NetEvent e = next(s);
        assertEvent(NetEvent.MESSAGE, null, e);
        assertEquals(Network.MAX_MESSAGE_LENGTH, e.getText().length());
    }

    public void testFloodingClientIsDropped() throws Exception
    {
        NetServer s = server(8);
        NetClient c = client(s);
        assertEvent(NetEvent.CONNECTED, "", next(c));
        assertEvent(NetEvent.CONNECTED, "", next(s));
        // The server does not poll while 3000 messages pour in.
        for (int i = 0; i < 3000; i++) {
            c.send("m" + i);
        }
        NetEvent gone = next(c);
        assertEvent(NetEvent.DISCONNECTED, null, gone);
        assertTrue(gone.getText(), gone.getText().startsWith("flooding"));
        int delivered = 0;
        NetEvent e = next(s);
        while (e.getType() == NetEvent.MESSAGE) {
            assertEquals("m" + delivered, e.getText());
            delivered++;
            e = next(s);
        }
        assertEvent(NetEvent.DISCONNECTED, null, e);
        assertTrue(e.getText(), e.getText().startsWith("flooding"));
        assertTrue("delivered " + delivered, delivered >= 2000 && delivered < 3000);
    }

    public void testSlowReaderIsDroppedNotWaitedFor() throws Exception
    {
        NetServer s = server(8);
        RawClient raw = new RawClient(s.getPort());
        int id = next(s).getConnectionId();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 60000; i++) {
            big.append('y');
        }
        String message = big.toString();
        // The raw client never reads. The socket buffers fill, then the outbox,
        // and the server must give up on it rather than block in send().
        long start = System.currentTimeMillis();
        NetEvent gone = null;
        for (int i = 0; i < 400 && gone == null; i++) {
            s.send(id, message);
            gone = s.poll();
        }
        long took = System.currentTimeMillis() - start;
        while (gone == null && System.currentTimeMillis() - start < WAIT_MS) {
            gone = s.poll();
            Thread.sleep(2);
        }
        assertNotNull("the slow reader should have been dropped", gone);
        assertEvent(NetEvent.DISCONNECTED, null, gone);
        assertTrue(gone.getText(), gone.getText().startsWith("send buffer full"));
        assertTrue("400 sends of 60 kB took " + took + " ms: send() must not block", took < 3000);
        raw.close();
    }

    // ---- the wire ----

    public void testFragmentsPingAndCloseFromARawClient() throws Exception
    {
        NetServer s = server(8);
        RawClient raw = new RawClient(s.getPort());
        int id = next(s).getConnectionId();
        raw.frame(0x1, false, "one ");
        raw.frame(0x0, false, "message ");
        raw.frame(0x0, true, "in three");
        assertEvent(NetEvent.MESSAGE, "one message in three", next(s));
        raw.frame(0x9, true, "ping!");
        assertEquals("pong with the ping's payload", "ping!", raw.readPayload(0xA));
        s.send(id, "to raw");
        assertEquals("to raw", raw.readPayload(0x1));
        raw.close(1000, "raw done");
        assertEvent(NetEvent.DISCONNECTED, "raw done", next(s));
        // The server echoes the close (RFC 6455 5.5.1) and only then hangs up.
        assertEquals("close echo", 1000, raw.readCloseCode());
        assertEquals("then end of stream", -1, raw.in.read());
        raw.close();
    }

    public void testPlainHttpGetsAnAnswerNotAConnection() throws Exception
    {
        NetServer s = server(8);
        try (Socket sock = new Socket("localhost", s.getPort())) {
            sock.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            sock.getOutputStream().flush();
            byte[] buf = new byte[4096];
            int n = sock.getInputStream().read(buf);
            String reply = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
            assertTrue(reply, reply.startsWith("HTTP/1.1 200") && reply.contains("SuperGreenfoot game server"));
        }
        Thread.sleep(100);
        assertNull("a web page request is not a connection", s.poll());
        assertEquals(0, s.getConnectionCount());
    }

    public void testJdkWebSocketClientTalksToOurServer() throws Exception
    {
        NetServer s = server(8);
        List<String> received = new CopyOnWriteArrayList<>();
        List<String> closed = new CopyOnWriteArrayList<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder partial = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last)
            {
                partial.append(data);
                if (last) {
                    received.add(partial.toString());
                    partial.setLength(0);
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int code, String reason)
            {
                closed.add(code + " " + reason);
                return null;
            }
        };
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + s.getPort() + "/"), listener)
                .get(WAIT_MS, TimeUnit.MILLISECONDS);
        int id = next(s).getConnectionId();
        ws.sendText("from the JDK", true).get(WAIT_MS, TimeUnit.MILLISECONDS);
        assertEvent(NetEvent.MESSAGE, "from the JDK", next(s));
        s.send(id, "from SuperGreenfoot");
        waitFor("the JDK client to hear back", () -> received.size() == 1);
        assertEquals("from SuperGreenfoot", received.get(0));
        s.kick(id, "bye from the server");
        waitFor("the JDK client to be closed", () -> closed.size() == 1);
        assertEquals("1000 bye from the server", closed.get(0));
        assertEvent(NetEvent.DISCONNECTED, "bye from the server", next(s));
    }

    // ---- addresses ----

    public void testNormalizeAddress()
    {
        assertEquals("ws://192.168.1.5:7777/", Network.normalizeAddress("192.168.1.5", 7777));
        assertEquals("ws://192.168.1.5:8000/", Network.normalizeAddress(" 192.168.1.5:8000 ", 7777));
        assertEquals("ws://play.example.org:7777/", Network.normalizeAddress("ws://play.example.org", 7777));
        assertEquals("ws://play.example.org:80/", Network.normalizeAddress("ws://play.example.org", 0));
        assertEquals("wss://play.example.org:443/game", Network.normalizeAddress("wss://play.example.org/game", 7777));
        assertEquals("wss://play.example.org:9000/", Network.normalizeAddress("wss://play.example.org:9000", 7777));
        assertEquals("ws://[::1]:7777/", Network.normalizeAddress("[::1]", 7777));
        assertEquals("ws://[fe80::1]:7000/", Network.normalizeAddress("[fe80::1]:7000", 7777));
        assertNull(Network.normalizeAddress("", 7777));
        assertNull(Network.normalizeAddress("host:notaport", 7777));
        assertNull(Network.normalizeAddress("ftp://host:21", 7777));
        assertNull(Network.normalizeAddress("host:70000", 7777));
        assertNull(Network.normalizeAddress("localhost", 0));
        assertNull("no control characters", Network.normalizeAddress("host\r\nX-Injected: yes", 7777));
        assertNull("no control characters", Network.normalizeAddress("ws://host:7777/\tpath", 7777));
        assertTrue(Network.canHost());
        assertFalse(Network.isDedicatedServer());
        assertNotNull(Network.getLocalAddresses());
    }

    /**
     * A bare WebSocket client written by hand: the handshake and masked
     * frames, nothing more, so the server can be checked frame by frame.
     */
    private static final class RawClient
    {
        private final Socket socket;
        private final OutputStream out;
        final DataInputStream in;
        private final Random random = new Random(1);

        RawClient(int port) throws IOException
        {
            socket = new Socket("localhost", port);
            out = socket.getOutputStream();
            in = new DataInputStream(socket.getInputStream());
            String key = "dGhlIHNhbXBsZSBub25jZQ==";
            out.write(("GET /chat HTTP/1.1\r\nHost: localhost:" + port + "\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            String head = readHead();
            if (!head.startsWith("HTTP/1.1 101") || !head.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")) {
                throw new IOException("bad handshake reply: " + head);
            }
        }

        private String readHead() throws IOException
        {
            StringBuilder b = new StringBuilder();
            while (!b.toString().endsWith("\r\n\r\n")) {
                b.append((char) in.readUnsignedByte());
            }
            return b.toString();
        }

        void frame(int opcode, boolean fin, String text) throws IOException
        {
            frame(opcode, fin, text.getBytes(StandardCharsets.UTF_8));
        }

        void frame(int opcode, boolean fin, byte[] payload) throws IOException
        {
            byte[] key = new byte[4];
            random.nextBytes(key);
            out.write((fin ? 0x80 : 0) | opcode);
            if (payload.length < 126) {
                out.write(0x80 | payload.length);
            }
            else {
                out.write(0x80 | 126);
                out.write(payload.length >> 8);
                out.write(payload.length);
            }
            out.write(key);
            for (int i = 0; i < payload.length; i++) {
                out.write(payload[i] ^ key[i & 3]);
            }
            out.flush();
        }

        /** Read one frame, check its opcode, and return its payload as text. */
        String readPayload(int expectedOpcode) throws IOException
        {
            return new String(readFrame(expectedOpcode), StandardCharsets.UTF_8);
        }

        byte[] readFrame(int expectedOpcode) throws IOException
        {
            int b0 = in.readUnsignedByte();
            int b1 = in.readUnsignedByte();
            assertEquals("opcode", expectedOpcode, b0 & 0x0F);
            assertEquals("server frames are unmasked", 0, b1 & 0x80);
            int len = b1 & 0x7F;
            if (len == 126) {
                len = in.readUnsignedShort();
            }
            byte[] payload = new byte[len];
            in.readFully(payload);
            return payload;
        }

        /** Read a close frame and return its status code. */
        int readCloseCode() throws IOException
        {
            byte[] payload = readFrame(0x8);
            return payload.length < 2 ? 1005 : ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        }

        void close(int code, String reason) throws IOException
        {
            byte[] text = reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + text.length];
            payload[0] = (byte) (code >> 8);
            payload[1] = (byte) code;
            System.arraycopy(text, 0, payload, 2, text.length);
            frame(0x8, true, payload);
        }

        void close() throws IOException
        {
            socket.close();
        }
    }
}
