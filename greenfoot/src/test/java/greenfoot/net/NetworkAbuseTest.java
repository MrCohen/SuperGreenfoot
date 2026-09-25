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
package greenfoot.net;

import greenfoot.NetClient;
import greenfoot.NetEvent;
import greenfoot.NetServer;
import greenfoot.Network;
import junit.framework.TestCase;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * What a hostile or broken peer can do to a server, and what it cannot.
 * Every case shrinks a limit or timeout (the package-private knobs in
 * {@link Link}) so that it runs in well under a second of waiting, then
 * checks through the public API and a hand-written raw client: a slow
 * handshake is cut off and holds no slot; handshakes in progress are capped
 * and so are the threads; a full server refuses without a thread; pings
 * without reading cannot fill the outbox; a peer that stops taking data is
 * dropped, and a kick or stop still closes it; a reconnect flood cannot fill
 * a paused host; a close is echoed; the protocol is enforced.
 */
public class NetworkAbuseTest extends TestCase
{
    private static final int WAIT_MS = 6000;

    private final List<NetServer> servers = new ArrayList<>();
    private final List<NetClient> clients = new ArrayList<>();
    private final List<RawClient> raws = new ArrayList<>();

    @Override
    protected void setUp() throws Exception
    {
        resetKnobs();
        waitFor("earlier links to be gone", () -> Link.liveCount() == 0);
    }

    @Override
    protected void tearDown() throws Exception
    {
        for (RawClient r : raws) {
            r.close();
        }
        for (NetClient c : clients) {
            c.close();
        }
        for (NetServer s : servers) {
            s.stop();
        }
        Network.closeAll();
        Link.awaitAllClosed(3000);
        resetKnobs();
    }

    private static void resetKnobs()
    {
        Link.maxOutboxBytes = Link.MAX_OUTBOX_BYTES;
        Link.maxInboxMessages = Link.MAX_INBOX_MESSAGES;
        Link.maxInboxChars = Link.MAX_INBOX_CHARS;
        Link.pingIntervalMs = Link.PING_INTERVAL_MS;
        Link.idleTimeoutMs = Link.IDLE_TIMEOUT_MS;
        Link.connectTimeoutMs = Link.CONNECT_TIMEOUT_MS;
        Link.handshakeTimeoutMs = Link.HANDSHAKE_TIMEOUT_MS;
        Link.writeTimeoutMs = Link.WRITE_TIMEOUT_MS;
        Link.closeGraceMs = Link.CLOSE_GRACE_MS;
        Link.maxHandshakes = Link.MAX_HANDSHAKES;
        Link.maxHandshakesPerAddress = Link.MAX_HANDSHAKES_PER_ADDRESS;
        Link.serverMaxInboxMessages = Link.SERVER_MAX_INBOX_MESSAGES;
        Link.serverMaxInboxChars = Link.SERVER_MAX_INBOX_CHARS;
    }

    private NetServer server(int max)
    {
        NetServer s = Network.startServer(0, max);
        assertNull("server should start: " + s.getError(), s.getError());
        servers.add(s);
        return s;
    }

    private NetClient client(NetServer s)
    {
        NetClient c = Network.connect("localhost:" + s.getPort());
        clients.add(c);
        return c;
    }

    private RawClient raw(NetServer s) throws IOException
    {
        RawClient r = new RawClient(s.getPort());
        raws.add(r);
        return r;
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

    private static int readerThreads()
    {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && t.getName().startsWith("SuperGreenfoot-Net-reader-")) {
                n++;
            }
        }
        return n;
    }

    /** True once the socket is closed from the far side: a read gives end of stream or an error. */
    private static boolean isClosed(Socket socket)
    {
        try {
            socket.setSoTimeout(50);
            return socket.getInputStream().read() < 0;
        }
        catch (java.net.SocketTimeoutException e) {
            return false;
        }
        catch (IOException e) {
            return true;
        }
    }

    // ---- handshakes ----

    public void testSlowHandshakeIsCutOffAndHoldsNoSlot() throws Exception
    {
        Link.handshakeTimeoutMs = 600;
        NetServer s = server(1);
        int before = readerThreads();
        Socket slow = new Socket("localhost", s.getPort());
        Thread dripper = new Thread(() -> {
            try {
                OutputStream out = slow.getOutputStream();
                for (byte b : "GET / HTTP/1.1\r\nHost: x\r\n".getBytes(StandardCharsets.ISO_8859_1)) {
                    out.write(b);
                    out.flush();
                    Thread.sleep(150);      // a byte every 150 ms: never idle long enough for a per-read timeout
                }
            }
            catch (IOException | InterruptedException e) {
                // the server hung up: that is the point
            }
        });
        dripper.setDaemon(true);
        dripper.start();
        Thread.sleep(200);
        assertEquals("a handshake in progress holds no connection slot", 0, s.getConnectionCount());

        // A real client gets the only slot meanwhile.
        NetClient c = client(s);
        assertEquals(NetEvent.CONNECTED, next(c).getType());
        assertEquals(NetEvent.CONNECTED, next(s).getType());

        long start = System.currentTimeMillis();
        waitFor("the slow handshake to be cut off", () -> isClosed(slow));
        long took = System.currentTimeMillis() - start;
        assertTrue("cut off at the total deadline, not per byte (took " + took + " ms)", took < 2000);
        // Left: the real client's two readers (its own, and the server's for it).
        waitFor("its reader thread to end", () -> readerThreads() <= before + 2);
        slow.close();
    }

    public void testHandshakesInProgressAreCappedAndSoAreThreads() throws Exception
    {
        Link.maxHandshakes = 3;
        Link.maxHandshakesPerAddress = 3;
        Link.handshakeTimeoutMs = 700;
        NetServer s = server(8);
        int before = readerThreads();
        List<Socket> silent = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            silent.add(new Socket("localhost", s.getPort()));
        }
        Thread.sleep(300);
        assertTrue("at most 3 reader threads for 12 silent sockets, saw " + (readerThreads() - before),
                readerThreads() <= before + 3);
        // The ones over the cap were closed at once; the three in progress at the deadline.
        waitFor("every silent socket to be closed", () -> silent.stream().allMatch(NetworkAbuseTest::isClosed));
        waitFor("reader threads to go", () -> readerThreads() <= before);
        for (Socket sock : silent) {
            sock.close();
        }
        // And a real player can still join.
        NetClient c = client(s);
        assertEquals(NetEvent.CONNECTED, next(c).getType());
    }

    public void testFullServerIsRefusedWithoutAThread() throws Exception
    {
        NetServer s = server(1);
        NetClient a = client(s);
        assertEquals(NetEvent.CONNECTED, next(a).getType());
        assertEquals(NetEvent.CONNECTED, next(s).getType());
        int before = readerThreads();

        Socket sock = new Socket("localhost", s.getPort());
        sock.getOutputStream().write(("GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        sock.getOutputStream().flush();
        byte[] buf = new byte[4096];
        int n = sock.getInputStream().read(buf);
        String reply = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
        assertTrue(reply, reply.startsWith("HTTP/1.1 503 the server is full"));
        assertEquals("no reader thread for a refusal", before, readerThreads());
        long start = System.currentTimeMillis();
        waitFor("the refused socket to be closed", () -> isClosed(sock));
        assertTrue("closed soon after the reply", System.currentTimeMillis() - start < 3000);
        sock.close();
        assertEquals(1, s.getConnectionCount());
        assertNull("the server never hears of it", s.poll());
    }

    public void testStopAcceptRace() throws Exception
    {
        for (int i = 0; i < 20; i++) {
            NetServer s = Network.startServer(0, 8);
            Socket sock = new Socket("localhost", s.getPort());
            s.stop();
            waitFor("a socket accepted around stop() to be closed (round " + i + ")", () -> isClosed(sock));
            sock.close();
        }
    }

    // ---- a peer that does not read ----

    public void testPingsWithoutReadingStayBounded() throws Exception
    {
        Link.idleTimeoutMs = 1000;
        Link.writeTimeoutMs = 1000;
        NetServer s = server(8);
        RawClient raw = raw(s);
        int id = next(s).getConnectionId();
        byte[] payload = new byte[125];
        long mostPending = 0;
        for (int i = 0; i < 4000; i++) {
            raw.frame(0x9, true, payload);      // ping, never reading the pongs
            if ((i & 63) == 0) {
                mostPending = Math.max(mostPending, s.getPendingBytes(id));
            }
        }
        assertTrue("at most one pong pending, saw " + mostPending + " bytes", mostPending <= 2 + 125);
        NetEvent gone = next(s);
        assertEquals(NetEvent.DISCONNECTED, gone.getType());
        assertTrue(gone.getText(), gone.getText().contains("timed out"));
    }

    public void testWriterStuckOnANonReadingPeerIsDropped() throws Exception
    {
        Link.maxOutboxBytes = 64 * 1024 * 1024;     // the outbox limit must not be what saves us here
        Link.writeTimeoutMs = 700;
        NetServer s = server(8);
        RawClient raw = raw(s);
        int id = next(s).getConnectionId();
        String big = "z".repeat(60000);
        for (int i = 0; i < 200; i++) {
            s.send(id, big);                    // 12 MB into a peer that never reads
        }
        long start = System.currentTimeMillis();
        NetEvent gone = next(s);
        assertEquals(NetEvent.DISCONNECTED, gone.getType());
        assertTrue(gone.getText(), gone.getText().startsWith("send timed out"));
        assertTrue("dropped by the write deadline", System.currentTimeMillis() - start < 4000);
        assertEquals(0, s.getConnectionCount());
    }

    public void testKickForceClosesAStuckPeer() throws Exception
    {
        Link.maxOutboxBytes = 64 * 1024 * 1024;
        Link.closeGraceMs = 400;
        NetServer s = server(8);
        RawClient raw = raw(s);
        int id = next(s).getConnectionId();
        String big = "z".repeat(60000);
        for (int i = 0; i < 100; i++) {
            s.send(id, big);
        }
        Thread.sleep(200);                      // the writer is now stuck on the peer's full buffers
        s.kick(id, "bye");
        long start = System.currentTimeMillis();
        NetEvent gone = next(s);
        assertEquals(NetEvent.DISCONNECTED, gone.getType());
        assertEquals("bye", gone.getText());
        waitFor("the socket to be forced closed", () -> Link.liveCount() == 0);
        assertTrue("within the grace", System.currentTimeMillis() - start < 3000);
    }

    public void testStopForceClosesAStuckPeer() throws Exception
    {
        Link.maxOutboxBytes = 64 * 1024 * 1024;
        Link.closeGraceMs = 400;
        NetServer s = server(8);
        RawClient raw = raw(s);
        int id = next(s).getConnectionId();
        String big = "z".repeat(60000);
        for (int i = 0; i < 100; i++) {
            s.send(id, big);
        }
        Thread.sleep(200);
        s.stop();
        long start = System.currentTimeMillis();
        NetEvent gone = next(s);
        assertEquals(NetEvent.DISCONNECTED, gone.getType());
        assertEquals("server stopped", gone.getText());
        waitFor("the socket to be forced closed", () -> Link.liveCount() == 0);
        assertTrue("within the grace", System.currentTimeMillis() - start < 3000);
    }

    // ---- a host that does not poll ----

    public void testReconnectFloodStaysUnderTheServerCap() throws Exception
    {
        Link.serverMaxInboxMessages = 250;
        NetServer s = server(8);
        String lastError = null;
        for (int cycle = 0; cycle < 6; cycle++) {
            NetClient c = client(s);
            waitFor("cycle " + cycle + " to connect or be refused", () -> c.getStatus() != NetClient.CONNECTING);
            if (c.getStatus() == NetClient.CONNECTED) {
                for (int i = 0; i < 100; i++) {
                    c.send("m" + i);
                }
                c.close("bye");
            }
            waitFor("cycle " + cycle + " to end", () -> c.getStatus() == NetClient.CLOSED
                    || c.getStatus() == NetClient.FAILED);
            lastError = c.getError();
        }
        assertEquals("the server is not reading messages (is the host's game paused?)", lastError);
        int events = 0;
        int messages = 0;
        NetEvent e;
        while ((e = s.poll()) != null) {
            events++;
            if (e.getType() == NetEvent.MESSAGE) {
                messages++;
            }
        }
        assertTrue("events held for a host that never polled: " + events, events <= 250 + 4);
        assertTrue("some messages were kept: " + messages, messages >= 200);
        // Once polled, new players are welcome again.
        NetClient again = client(s);
        assertEquals(NetEvent.CONNECTED, next(again).getType());
    }

    // ---- the wire ----

    public void testCloseIsEchoedThenTheSocketCloses() throws Exception
    {
        NetServer s = server(8);
        RawClient raw = raw(s);
        int id = next(s).getConnectionId();
        raw.close(1000, "going");
        assertEquals(1000, raw.readCloseCode());
        assertEquals("end of stream after the echo", -1, raw.in.read());
        NetEvent gone = next(s);
        assertEquals(id, gone.getConnectionId());
        assertEquals("going", gone.getText());
    }

    public void testProtocolErrorsGetTheRightCloseCode() throws Exception
    {
        NetServer s = server(8);

        RawClient badUtf8 = raw(s);
        next(s);
        badUtf8.frame(0x1, true, new byte[] { (byte) 0xC3, (byte) 0x28 });
        assertEquals("invalid UTF-8 is 1007", 1007, badUtf8.readCloseCode());
        assertTrue(next(s).getText().contains("UTF-8"));

        RawClient longForm = raw(s);
        next(s);
        longForm.frameWithExtendedLength(0x1, "hi".getBytes(StandardCharsets.UTF_8));
        assertEquals("a non-minimal length is 1002", 1002, longForm.readCloseCode());
        next(s);

        RawClient shortClose = raw(s);
        next(s);
        shortClose.frame(0x8, true, new byte[] { 3 });
        assertEquals("a one-byte close payload is 1002", 1002, shortClose.readCloseCode());
        next(s);

        RawClient reservedCode = raw(s);
        next(s);
        reservedCode.close(1005, "");
        assertEquals("a reserved close code is 1002", 1002, reservedCode.readCloseCode());
        next(s);

        RawClient binary = raw(s);
        next(s);
        binary.frame(0x2, true, new byte[] { 1, 2, 3 });
        assertEquals("binary is 1003", 1003, binary.readCloseCode());
        next(s);
    }

    public void testHandshakeIsChecked() throws Exception
    {
        NetServer s = server(8);
        assertTrue(handshakeReply(s, "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
                .startsWith("HTTP/1.1 426"));
        assertTrue(handshakeReply(s, "Sec-WebSocket-Key: tooshort\r\nSec-WebSocket-Version: 13\r\n")
                .startsWith("HTTP/1.1 400"));
        assertTrue(handshakeReply(s, "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 8\r\n")
                .startsWith("HTTP/1.1 426"));
        raw(s);     // the well-formed handshake, kept open
        assertEquals(NetEvent.CONNECTED, next(s).getType());
        assertEquals("only the good handshake connected", 1, s.getConnectionCount());
    }

    private static String handshakeReply(NetServer s, String extraHeaders) throws IOException
    {
        try (Socket sock = new Socket("localhost", s.getPort())) {
            sock.getOutputStream().write(("GET / HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + extraHeaders + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            sock.getOutputStream().flush();
            byte[] buf = new byte[4096];
            int n = sock.getInputStream().read(buf);
            return new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8);
        }
    }

    /** A bare WebSocket client: the handshake and masked frames, nothing more. */
    private static final class RawClient
    {
        private final Socket socket;
        private final OutputStream out;
        final DataInputStream in;
        private final Random random = new Random(7);

        RawClient(int port) throws IOException
        {
            socket = new Socket("localhost", port);
            out = socket.getOutputStream();
            in = new DataInputStream(socket.getInputStream());
            out.write(("GET / HTTP/1.1\r\nHost: localhost:" + port + "\r\nUpgrade: websocket\r\n"
                    + "Connection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            StringBuilder head = new StringBuilder();
            while (!head.toString().endsWith("\r\n\r\n")) {
                head.append((char) in.readUnsignedByte());
            }
            if (!head.toString().startsWith("HTTP/1.1 101")) {
                throw new IOException("bad handshake reply: " + head);
            }
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

        /** A short payload written with the 16-bit length form, which the protocol forbids. */
        void frameWithExtendedLength(int opcode, byte[] payload) throws IOException
        {
            byte[] key = new byte[4];
            random.nextBytes(key);
            out.write(0x80 | opcode);
            out.write(0x80 | 126);
            out.write(payload.length >> 8);
            out.write(payload.length);
            out.write(key);
            for (int i = 0; i < payload.length; i++) {
                out.write(payload[i] ^ key[i & 3]);
            }
            out.flush();
        }

        int readCloseCode() throws IOException
        {
            int b0 = in.readUnsignedByte();
            int b1 = in.readUnsignedByte();
            assertEquals("a close frame", 0x8, b0 & 0x0F);
            int len = b1 & 0x7F;
            byte[] payload = new byte[len];
            in.readFully(payload);
            return len < 2 ? 1005 : ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
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

        void close()
        {
            try {
                socket.close();
            }
            catch (IOException e) {
                // already closed
            }
        }
    }
}
