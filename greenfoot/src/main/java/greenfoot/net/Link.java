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

import threadchecker.OnThread;
import threadchecker.Tag;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * One WebSocket connection, seen from either end, with its two threads.
 *
 * <p><b>The threading contract.</b> A link owns a reader thread and a writer
 * thread, and they never call out of this package except into its owner's
 * {@link Inbox}: the reader reports events there, and the writer takes
 * frames off this link's outbox. Scenario code only ever meets the queue,
 * through {@code poll()}, and the outbox, through {@code send()}. Neither
 * blocks, so a networked scenario stays single-threaded and inside its act
 * loop.</p>
 *
 * <p><b>Order.</b> Only the reader thread reports events, so for one
 * connection they come out as CONNECTED, then MESSAGEs in the order sent,
 * then one DISCONNECTED, and nothing after that. The watchdog below only
 * ever closes sockets; the reader notices and reports the end.</p>
 *
 * <p><b>Bounds.</b> The outbox holds at most {@link #MAX_OUTBOX_BYTES},
 * counting every queued frame (messages, the one pending pong, the close
 * frame); a send that would overflow it drops the connection instead of
 * blocking. The inbox (events not yet polled) holds at most
 * {@link #MAX_INBOX_MESSAGES} or {@link #MAX_INBOX_CHARS}; a peer that
 * overruns it is dropped as flooding. One message is at most
 * {@link #MAX_MESSAGE_BYTES} of UTF-8.</p>
 *
 * <p><b>Liveness, on one shared watchdog thread.</b> A handshake has
 * {@link #HANDSHAKE_TIMEOUT_MS} in total, from accept. Once connected, the
 * writer pings every {@link #PING_INTERVAL_MS} when it has nothing else to
 * send; the connection is dropped when nothing at all has been heard for
 * {@link #IDLE_TIMEOUT_MS}, when the other side has not taken a single write
 * for {@link #WRITE_TIMEOUT_MS}, and {@link #CLOSE_GRACE_MS} after a close
 * frame was queued if the socket is still open by then (a peer that neither
 * reads nor answers cannot keep a link alive). The watchdog closes the raw
 * TCP socket, which never blocks, even under TLS with a stuck writer.</p>
 */
@OnThread(Tag.Any)
public final class Link
{
    /** The longest message either side accepts, in UTF-8 bytes. */
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;
    /** Unsent bytes a connection may hold before it is dropped. */
    public static final int MAX_OUTBOX_BYTES = 256 * 1024;
    /** Unpolled messages a connection may hold before it is dropped. */
    public static final int MAX_INBOX_MESSAGES = 2000;
    /** Unpolled text a connection may hold before it is dropped, in characters. */
    public static final long MAX_INBOX_CHARS = 1024 * 1024;
    /** A ping goes out after this long without anything to send. */
    public static final int PING_INTERVAL_MS = 5000;
    /** Nothing heard for this long means the other side is gone. */
    public static final int IDLE_TIMEOUT_MS = 20000;
    /** How long to wait for a server to answer a connection attempt. */
    public static final int CONNECT_TIMEOUT_MS = 10000;
    /** How long a handshake may take in total, from the moment the socket was accepted (or opened). */
    public static final int HANDSHAKE_TIMEOUT_MS = 10000;
    /** How long one write may wait for the other side to take data. */
    public static final int WRITE_TIMEOUT_MS = 20000;
    /** After a close frame is queued, how long the socket may stay open. */
    public static final int CLOSE_GRACE_MS = 1500;
    /** Handshakes a server has in progress at once; further sockets are closed unanswered. */
    public static final int MAX_HANDSHAKES = 64;
    /** Handshakes in progress from one address. */
    public static final int MAX_HANDSHAKES_PER_ADDRESS = 8;
    /** Unpolled events a whole server may hold (over every connection, live or gone). */
    public static final int SERVER_MAX_INBOX_MESSAGES = 20000;
    /** Unpolled text a whole server may hold, in characters. */
    public static final long SERVER_MAX_INBOX_CHARS = 16L * 1024 * 1024;
    /** After sending a close, how long to wait for the other side's close before dropping the socket. */
    static final int CLOSE_WAIT_MS = 500;
    /** A refused socket (503) is closed this long after the reply, once its request has been drained. */
    static final int REFUSE_GRACE_MS = 1000;

    // The values in force. Tests in this package shrink them; nothing else writes them.
    static volatile int maxOutboxBytes = MAX_OUTBOX_BYTES;
    static volatile int maxInboxMessages = MAX_INBOX_MESSAGES;
    static volatile long maxInboxChars = MAX_INBOX_CHARS;
    static volatile int pingIntervalMs = PING_INTERVAL_MS;
    static volatile int idleTimeoutMs = IDLE_TIMEOUT_MS;
    static volatile int connectTimeoutMs = CONNECT_TIMEOUT_MS;
    static volatile int handshakeTimeoutMs = HANDSHAKE_TIMEOUT_MS;
    static volatile int writeTimeoutMs = WRITE_TIMEOUT_MS;
    static volatile int closeGraceMs = CLOSE_GRACE_MS;
    static volatile int maxHandshakes = MAX_HANDSHAKES;
    static volatile int maxHandshakesPerAddress = MAX_HANDSHAKES_PER_ADDRESS;
    static volatile int serverMaxInboxMessages = SERVER_MAX_INBOX_MESSAGES;
    static volatile long serverMaxInboxChars = SERVER_MAX_INBOX_CHARS;
    static volatile int timerPeriodMs = 100;

    /** Handshakes a server may have in progress at once (the value in force). */
    public static int maxHandshakes()
    {
        return maxHandshakes;
    }

    /** Handshakes one address may have in progress at once (the value in force). */
    public static int maxHandshakesPerAddress()
    {
        return maxHandshakesPerAddress;
    }

    /** Unpolled events a whole server may hold (the value in force). */
    public static int serverMaxInboxMessages()
    {
        return serverMaxInboxMessages;
    }

    /** Unpolled text a whole server may hold, in characters (the value in force). */
    public static long serverMaxInboxChars()
    {
        return serverMaxInboxChars;
    }

    public static final int CONNECTING = 0;
    public static final int CONNECTED = 1;
    public static final int CLOSED = 2;
    public static final int FAILED = 3;

    private static final byte[] CLOSE_MARKER = new byte[0];
    private static final byte[] PONG_MARKER = new byte[0];

    private final int id;
    private final boolean serverSide;
    private final Inbox inbox;
    private final Addresses.Address address;     // client side only
    private final Function<Link, String> admit;   // server side: asked once the handshake is done; a reason refuses
    private final Runnable whenFinished;         // the owner's bookkeeping, or null
    /** The TCP socket. Closing it never blocks, so every forced close goes through it. */
    private volatile Socket rawSocket;
    /** What we read and write: the TCP socket, or the TLS socket on top of it. */
    private volatile Socket socket;
    private final LinkedBlockingQueue<byte[]> outbox = new LinkedBlockingQueue<>();
    private final AtomicReference<byte[]> pendingPong = new AtomicReference<>();
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicInteger unreadMessages = new AtomicInteger();
    private final AtomicLong unreadChars = new AtomicLong();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final CountDownLatch peerClosed = new CountDownLatch(1);
    private volatile int state = CONNECTING;
    private volatile boolean closedByOwner;
    private volatile String localReason;
    private volatile String peerReason;
    private volatile String error;
    private volatile long lastHeard = System.currentTimeMillis();
    private volatile long handshakeDeadline;     // 0 when none
    private volatile long writeStartedAt;        // 0 when the writer is not inside a write
    private volatile long closeDeadline;         // 0 when none
    private volatile String remoteAddress = "";
    private volatile String remoteHost = "";
    private volatile String origin = "";

    private Link(int id, boolean serverSide, Inbox inbox, Socket socket, Addresses.Address address,
                 Function<Link, String> admit, Runnable whenFinished)
    {
        this.id = id;
        this.serverSide = serverSide;
        this.inbox = inbox;
        this.rawSocket = socket;
        this.socket = socket;
        this.address = address;
        this.admit = admit;
        this.whenFinished = whenFinished;
        long now = System.currentTimeMillis();
        if (socket != null) {
            remoteAddress = describeAddress(socket.getRemoteSocketAddress());
            remoteHost = hostOf(socket.getRemoteSocketAddress());
            handshakeDeadline = now + handshakeTimeoutMs;
        }
        else {
            handshakeDeadline = now + connectTimeoutMs + handshakeTimeoutMs;
        }
    }

    /**
     * Server side: take over an accepted socket. Nothing runs until
     * {@link #start()}, so the owner can record the link first.
     *
     * @param admit         Asked on the reader thread once the handshake is
     *                      done: null admits the connection, anything else is
     *                      the reason it is closed at once (never reported
     *                      as connected).
     * @param whenFinished  Run (on a network thread) once the link is over,
     *                      whether or not it ever connected; may be null.
     */
    public static Link accept(int id, Socket socket, Inbox inbox, Function<Link, String> admit,
                              Runnable whenFinished)
    {
        return new Link(id, true, inbox, socket, null, admit, whenFinished);
    }

    /** Client side: a link that will connect in the background once started. */
    public static Link connect(Addresses.Address address, Inbox inbox, Runnable whenFinished)
    {
        return new Link(0, false, inbox, null, address, null, whenFinished);
    }

    /** Start the reader thread (the writer starts once the handshake is done). */
    public void start()
    {
        watch(this);
        Thread t = new Thread(this::runReader, "SuperGreenfoot-Net-reader-" + id);
        t.setDaemon(true);
        t.start();
    }

    // ---- what the owner calls ----

    public int getId()
    {
        return id;
    }

    public int getState()
    {
        return state;
    }

    /** Why the link failed or closed, in words, or null while it is fine. */
    public String getError()
    {
        return error;
    }

    /** The other end, as address and port. */
    public String getRemoteAddress()
    {
        return remoteAddress;
    }

    /** The other end's address without the port, for per-address bookkeeping. */
    public String getRemoteHost()
    {
        return remoteHost;
    }

    /** The Origin header a browser sent with the handshake, or "" (a desktop scenario sends none). */
    public String getOrigin()
    {
        return origin;
    }

    public long getBytesSent()
    {
        return bytesSent.get();
    }

    public long getBytesReceived()
    {
        return bytesReceived.get();
    }

    public long getPendingBytes()
    {
        return pendingBytes.get();
    }

    /**
     * A text message as a frame for this side to send.
     *
     * @throws IllegalArgumentException if the text is null or longer than {@link #MAX_MESSAGE_BYTES}.
     */
    public static byte[] encodeText(String text, boolean fromServer)
    {
        if (text == null) {
            throw new IllegalArgumentException("nothing to send: the message is null");
        }
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("message too long: " + payload.length
                    + " bytes, the limit is " + MAX_MESSAGE_BYTES);
        }
        return Frames.encode(Frames.OP_TEXT, payload, !fromServer, ThreadLocalRandom.current());
    }

    /**
     * Queue a text message. Never blocks. Silently dropped once the link is
     * closing; drops the whole connection if the outbox is full.
     *
     * @throws IllegalArgumentException if the text is null or longer than {@link #MAX_MESSAGE_BYTES}.
     */
    public void send(String text)
    {
        sendFrame(encodeText(text, serverSide));
    }

    /** Queue an encoded text frame (from {@link #encodeText}); a broadcast shares one frame between links. */
    public void sendFrame(byte[] frame)
    {
        if (closing.get() || finished.get()) {
            return;
        }
        if (pendingBytes.get() + frame.length > maxOutboxBytes) {
            dropNow("send buffer full: the other side is not keeping up");
            return;
        }
        pendingBytes.addAndGet(frame.length);
        outbox.add(frame);
    }

    /**
     * Close politely: everything already queued goes first, then a close frame
     * carrying the reason, which the other side reports as its DISCONNECTED
     * text. The socket is forced closed {@link #CLOSE_GRACE_MS} later if the
     * other side has not taken the frame or answered it by then.
     */
    public void close(String reason)
    {
        if (closing.compareAndSet(false, true)) {
            closedByOwner = true;
            localReason = reason == null ? "" : reason;
            queueClose(Frames.CLOSE_NORMAL, localReason);
            if (socket == null) {
                // Still connecting: nothing to say goodbye to.
                finish("closed");
            }
        }
    }

    /** Drop the connection at once with this reason: no close frame, the socket just closes. */
    public void dropNow(String reason)
    {
        if (closing.compareAndSet(false, true)) {
            localReason = reason;
        }
        closeSocket();
    }

    /** The owner has polled a MESSAGE event of this link: keep the inbox count right. */
    public void polledMessage(String text)
    {
        unreadMessages.decrementAndGet();
        unreadChars.addAndGet(-text.length());
    }

    // ---- the watchdog: one thread for every link in the process ----

    private static final Object TIMER_LOCK = new Object();
    private static final Set<Link> live = ConcurrentHashMap.newKeySet();
    private static final Queue<Refused> refused = new ConcurrentLinkedQueue<>();
    private static boolean timerRunning;

    /** A socket answered with a 503 and no thread: drained and closed a moment later. */
    private static final class Refused
    {
        final Socket socket;
        final long deadline;

        Refused(Socket socket, long deadline)
        {
            this.socket = socket;
            this.deadline = deadline;
        }
    }

    private static void watch(Link link)
    {
        synchronized (TIMER_LOCK) {
            live.add(link);
            ensureTimerLocked();
        }
    }

    private static void ensureTimerLocked()
    {
        if (!timerRunning) {
            timerRunning = true;
            Thread t = new Thread(Link::runTimer, "SuperGreenfoot-Net-timer");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void runTimer()
    {
        while (true) {
            try {
                Thread.sleep(timerPeriodMs);
            }
            catch (InterruptedException e) {
                synchronized (TIMER_LOCK) {
                    timerRunning = false;
                }
                return;
            }
            long now = System.currentTimeMillis();
            for (Link link : live) {
                try {
                    link.check(now);
                }
                catch (RuntimeException e) {
                    // one link's trouble must not stop the watchdog
                }
            }
            for (Iterator<Refused> it = refused.iterator(); it.hasNext();) {
                Refused r = it.next();
                if (now >= r.deadline) {
                    it.remove();
                    closeRefused(r.socket);
                }
            }
            synchronized (TIMER_LOCK) {
                if (live.isEmpty() && refused.isEmpty()) {
                    timerRunning = false;
                    return;
                }
            }
        }
    }

    /** The watchdog's look at this link: close the socket if a deadline has passed. */
    private void check(long now)
    {
        long hd = handshakeDeadline;
        if (hd != 0 && state == CONNECTING) {
            if (now >= hd) {
                handshakeDeadline = 0;
                String where = address == null ? remoteAddress : address.host + ":" + address.port;
                dropNow(serverSide ? "the handshake took too long"
                        : socket == null ? "timed out connecting to " + where
                        : "timed out waiting for " + where + " to answer");
            }
            return;
        }
        if (state == CONNECTED && !closing.get() && now - lastHeard > idleTimeoutMs) {
            dropNow("timed out: nothing heard for " + (idleTimeoutMs / 1000) + " seconds");
            return;
        }
        long ws = writeStartedAt;
        if (ws != 0 && now - ws > writeTimeoutMs) {
            dropNow("send timed out: the other side stopped taking data");
            return;
        }
        long cd = closeDeadline;
        if (cd != 0 && now >= cd) {
            closeSocket();      // the close frame had its chance
        }
    }

    /**
     * Refuse an accepted socket without a thread: a 503 whose reason phrase
     * is the reason, then the socket is drained and closed by the watchdog a
     * moment later (closing at once could turn the reply into a reset).
     */
    public static void refuseSocket(Socket socket, String reason)
    {
        try {
            OutputStream out = socket.getOutputStream();
            out.write(Handshake.refusal(reason));
            out.flush();
            socket.shutdownOutput();
        }
        catch (IOException e) {
            closeRefused(socket);
            return;
        }
        synchronized (TIMER_LOCK) {
            refused.add(new Refused(socket, System.currentTimeMillis() + REFUSE_GRACE_MS));
            ensureTimerLocked();
        }
    }

    private static void closeRefused(Socket socket)
    {
        try {
            InputStream in = socket.getInputStream();
            int n;
            while ((n = in.available()) > 0) {
                if (in.read(new byte[Math.min(n, 4096)]) < 0) {
                    break;
                }
            }
        }
        catch (IOException e) {
            // closed already
        }
        try {
            socket.close();
        }
        catch (IOException e) {
            // closed already
        }
    }

    /**
     * Wait until every link in the process has closed its socket (a
     * dedicated server leaving, so that its close frames go out).
     *
     * @return True when they all have; false when the wait ran out.
     */
    public static boolean awaitAllClosed(long millis)
    {
        long deadline = System.currentTimeMillis() + millis;
        while (!live.isEmpty()) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(10);
            }
            catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

    /** How many links in the process still have a socket (for tests). */
    static int liveCount()
    {
        return live.size();
    }

    // ---- ending ----

    /** Queue our close frame, then the marker that tells the writer to hang up after it. */
    private void queueClose(int code, String reason)
    {
        byte[] frame = Frames.encode(Frames.OP_CLOSE, Frames.closePayload(code, reason),
                !serverSide, ThreadLocalRandom.current());
        pendingBytes.addAndGet(frame.length);
        outbox.add(frame);
        outbox.add(CLOSE_MARKER);
        closeDeadline = System.currentTimeMillis() + closeGraceMs;
    }

    /** Send a close frame with a protocol reason; the writer hangs up after it. */
    private void refuse(int code, String reason)
    {
        if (closing.compareAndSet(false, true)) {
            localReason = reason;
            queueClose(code, reason);
        }
    }

    /** Close the TCP socket. Never blocks, so it is safe from any thread. */
    private void closeSocket()
    {
        Socket s = rawSocket;
        if (s != null) {
            try {
                s.close();
            }
            catch (IOException e) {
                // already closed
            }
        }
        live.remove(this);
    }

    /** The one place a link ends: state, error, and (if it ever connected) the DISCONNECTED event. */
    private void finish(String reason)
    {
        finish(reason, true);
    }

    /**
     * @param hangUp  Close the socket now. False when a close frame is still to
     *                go out, in which case the writer hangs up after sending it
     *                (or the watchdog does, at the close deadline).
     */
    private void finish(String reason, boolean hangUp)
    {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        boolean wasConnected = state == CONNECTED;
        error = reason;
        state = wasConnected || closedByOwner ? CLOSED : FAILED;
        if (hangUp) {
            closeSocket();
        }
        else if (closeDeadline == 0) {
            closeDeadline = System.currentTimeMillis() + closeGraceMs;
        }
        if (wasConnected) {
            inbox.disconnected(id, reason);
        }
        if (whenFinished != null) {
            whenFinished.run();
        }
    }

    /**
     * Keep reading and discarding until the other side hangs up or a moment
     * passes, so our close frame is not lost to a reset caused by unread
     * data. The writer closes the socket once the other side's close arrives
     * or the wait is over.
     */
    private void drainQuietly(InputStream in)
    {
        try {
            socket.setSoTimeout(CLOSE_WAIT_MS);
            byte[] buf = new byte[4096];
            while (in.read(buf) >= 0) {
                // discard
            }
        }
        catch (IOException e) {
            // closed or timed out: either way we are done
        }
    }

    // ---- reader thread ----

    private void runReader()
    {
        DataInputStream in;
        try {
            in = openAndShakeHands();
        }
        catch (IOException e) {
            finish(describe(e));
            return;
        }
        if (finished.get()) {
            // close() ended the link while the handshake was finishing.
            closeSocket();
            return;
        }
        String refusal = admit == null ? null : admit.apply(this);
        if (refusal != null) {
            // Never reported as connected: say why and go.
            refuse(Frames.CLOSE_TRY_AGAIN_LATER, refusal);
            startWriter();          // sends the close frame, then hangs up
            finish(refusal, false);
            drainQuietly(in);
            peerClosed.countDown();
            return;
        }
        state = CONNECTED;
        handshakeDeadline = 0;
        lastHeard = System.currentTimeMillis();
        inbox.connected(id);
        startWriter();

        ByteArrayOutputStream partial = null;
        try {
            while (true) {
                Frames.Frame frame = Frames.read(in, serverSide, MAX_MESSAGE_BYTES);
                bytesReceived.addAndGet(frame.wireBytes);
                lastHeard = System.currentTimeMillis();
                switch (frame.opcode) {
                    case Frames.OP_TEXT:
                    case Frames.OP_CONTINUATION:
                        if (frame.opcode == Frames.OP_TEXT) {
                            if (partial != null) {
                                throw new Frames.ProtocolException("new message inside a fragmented one");
                            }
                        }
                        else if (partial == null) {
                            throw new Frames.ProtocolException("continuation frame with nothing to continue");
                        }
                        if (frame.fin && partial == null) {
                            deliver(frame.payload);
                        }
                        else {
                            if (partial == null) {
                                partial = new ByteArrayOutputStream();
                            }
                            if (partial.size() + frame.payload.length > MAX_MESSAGE_BYTES) {
                                throw new Frames.ProtocolException("message too long (the limit is "
                                        + MAX_MESSAGE_BYTES + " bytes)", Frames.CLOSE_TOO_BIG);
                            }
                            partial.write(frame.payload, 0, frame.payload.length);
                            if (frame.fin) {
                                deliver(partial.toByteArray());
                                partial = null;
                            }
                        }
                        break;
                    case Frames.OP_BINARY:
                        throw new Frames.ProtocolException("binary messages are not accepted", 1003);
                    case Frames.OP_PING:
                        queuePong(frame.payload);
                        break;
                    case Frames.OP_PONG:
                        break;
                    case Frames.OP_CLOSE:
                        Frames.checkClosePayload(frame.payload);
                        peerReason = Frames.closeReason(frame.payload);
                        if (peerReason.isEmpty()) {
                            peerReason = closing.get() ? localReason : "closed by the other side";
                        }
                        if (closing.compareAndSet(false, true)) {
                            // Their close: echo it, and let the writer hang up after the echo.
                            localReason = peerReason;
                            queueClose(Frames.CLOSE_NORMAL, "");
                        }
                        peerClosed.countDown();
                        finish(peerReason, false);
                        return;
                    default:
                        throw new Frames.ProtocolException("unknown frame type " + frame.opcode);
                }
                if (finished.get()) {
                    return;
                }
            }
        }
        catch (Frames.ProtocolException e) {
            String reason = e.getMessage();
            refuse(e.closeCode, reason);
            finish(reason, false);
            drainQuietly(in);
        }
        catch (SocketTimeoutException e) {
            finish(reasonOr("timed out: nothing heard for " + (idleTimeoutMs / 1000) + " seconds"));
        }
        catch (IOException e) {
            finish(reasonOr("connection lost"));
        }
        finally {
            peerClosed.countDown();
        }
    }

    /** The reason we already have (a close or drop of our own), or this one. */
    private String reasonOr(String reason)
    {
        String r = localReason;
        return r != null ? r : reason;
    }

    private void deliver(byte[] payload) throws Frames.ProtocolException
    {
        if (unreadMessages.get() >= maxInboxMessages || unreadChars.get() >= maxInboxChars) {
            throw new Frames.ProtocolException("flooding: too many messages not yet read", Frames.CLOSE_TRY_AGAIN_LATER);
        }
        String text = Frames.decodeText(payload);
        unreadMessages.incrementAndGet();
        unreadChars.addAndGet(text.length());
        if (!inbox.message(id, text)) {
            unreadMessages.decrementAndGet();
            unreadChars.addAndGet(-text.length());
            throw new Frames.ProtocolException("the server is not reading messages (is the host's game paused?)",
                    Frames.CLOSE_TRY_AGAIN_LATER);
        }
    }

    /**
     * Answer a ping. At most one pong is ever pending: a newer ping replaces
     * the answer to an older one (RFC 6455 section 5.5.3 allows it), so a
     * peer that pings without reading cannot fill the outbox.
     */
    private void queuePong(byte[] payload)
    {
        byte[] pong = Frames.encode(Frames.OP_PONG, payload, !serverSide, ThreadLocalRandom.current());
        byte[] old = pendingPong.getAndSet(pong);
        pendingBytes.addAndGet(pong.length - (old == null ? 0 : old.length));
        if (old == null) {
            outbox.add(PONG_MARKER);
        }
    }

    /**
     * Open the socket (client side), do the handshake, and return the framed
     * input stream. Throws with a reason in words.
     */
    private DataInputStream openAndShakeHands() throws IOException
    {
        Socket s = socket;
        if (!serverSide) {
            s = openClientSocket();
            socket = s;
            if (closing.get()) {
                // close() was called while we were connecting.
                s.close();
                throw new IOException("closed");
            }
        }
        s.setTcpNoDelay(true);
        s.setSoTimeout(handshakeTimeoutMs);
        InputStream raw = new BufferedInputStream(s.getInputStream());
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        if (serverSide) {
            Handshake.Head request = Handshake.readHead(raw);
            bytesReceived.addAndGet(request.wireBytes);
            String o = request.header("Origin");
            origin = o == null ? "" : o;
            bytesSent.addAndGet(Handshake.answerClient(request, out));
        }
        else {
            String key = Handshake.newKey(ThreadLocalRandom.current());
            bytesSent.addAndGet(Handshake.askServer(out, address.host, address.port, address.secure, address.path, key));
            Handshake.Head reply = Handshake.readHead(raw);
            bytesReceived.addAndGet(reply.wireBytes);
            Handshake.checkServer(reply, key);
        }
        s.setSoTimeout(idleTimeoutMs * 2);
        return new DataInputStream(raw);
    }

    private Socket openClientSocket() throws IOException
    {
        Socket plain = new Socket();
        rawSocket = plain;      // so the watchdog can cut a connect short
        try {
            plain.connect(new InetSocketAddress(address.host, address.port), connectTimeoutMs);
            remoteAddress = describeAddress(plain.getRemoteSocketAddress());
            remoteHost = hostOf(plain.getRemoteSocketAddress());
            if (!address.secure) {
                return plain;
            }
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket(plain, address.host, address.port, true);
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(params);
            ssl.setSoTimeout(handshakeTimeoutMs);
            ssl.startHandshake();
            return ssl;
        }
        catch (IOException e) {
            plain.close();
            throw e;
        }
    }

    /** An IOException from connecting, in words a player can act on. */
    private String describe(IOException e)
    {
        String where = address == null ? remoteAddress : address.host + ":" + address.port;
        if (localReason != null) {
            return localReason;
        }
        if (e instanceof UnknownHostException) {
            return "no such host: " + (address == null ? where : address.host);
        }
        if (e instanceof ConnectException) {
            return "connection refused at " + where + " (is the server running, and the port right?)";
        }
        if (e instanceof SocketTimeoutException) {
            return state == CONNECTING && socket == null
                    ? "timed out connecting to " + where
                    : "timed out waiting for " + where + " to answer";
        }
        if (e instanceof SSLException) {
            return "secure connection to " + where + " failed: " + e.getMessage();
        }
        if (e instanceof Frames.ProtocolException) {
            return e.getMessage();
        }
        String msg = e.getMessage();
        return "could not connect to " + where + (msg == null ? "" : ": " + msg);
    }

    private static String describeAddress(SocketAddress a)
    {
        return a == null ? "" : a.toString();
    }

    private static String hostOf(SocketAddress a)
    {
        if (a instanceof InetSocketAddress && ((InetSocketAddress) a).getAddress() != null) {
            return ((InetSocketAddress) a).getAddress().getHostAddress();
        }
        return a == null ? "" : a.toString();
    }

    // ---- writer thread ----

    private void startWriter()
    {
        Thread t = new Thread(this::runWriter, "SuperGreenfoot-Net-writer-" + id);
        t.setDaemon(true);
        t.start();
    }

    private void runWriter()
    {
        Socket s = socket;
        OutputStream out;
        try {
            out = new BufferedOutputStream(s.getOutputStream());
        }
        catch (IOException e) {
            dropNow(reasonOr("connection lost"));
            return;
        }
        try {
            while (true) {
                byte[] frame = outbox.poll(pingIntervalMs, TimeUnit.MILLISECONDS);
                boolean counted = true;
                if (frame == null) {
                    if (rawSocket.isClosed()) {
                        return;
                    }
                    // Nothing to say: a ping keeps the other side's idle timer quiet (ours is the watchdog's).
                    frame = Frames.encode(Frames.OP_PING, new byte[0], !serverSide, ThreadLocalRandom.current());
                    counted = false;
                }
                else if (frame == CLOSE_MARKER) {
                    // Our close frame has gone; give the other side a moment to answer, then hang up.
                    try {
                        peerClosed.await(CLOSE_WAIT_MS, TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException e) {
                        // fall through
                    }
                    closeSocket();
                    return;
                }
                else if (frame == PONG_MARKER) {
                    frame = pendingPong.getAndSet(null);
                    if (frame == null) {
                        continue;
                    }
                }
                writeStartedAt = System.currentTimeMillis();
                Frames.write(out, frame);
                writeStartedAt = 0;
                bytesSent.addAndGet(frame.length);
                if (counted) {
                    pendingBytes.addAndGet(-frame.length);
                }
            }
        }
        catch (IOException e) {
            dropNow(reasonOr("connection lost"));
        }
        catch (InterruptedException e) {
            dropNow(reasonOr("closed"));
        }
    }
}
