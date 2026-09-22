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

import greenfoot.NetEvent;
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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One WebSocket connection, seen from either end, with its two threads.
 *
 * <p><b>The threading contract.</b> A link owns a reader thread and a writer
 * thread, and they never call out of this package: the reader puts
 * {@link NetEvent}s on the owner's queue, and the writer takes frames off
 * this link's outbox. Scenario code only ever meets the queue, through
 * {@code poll()}, and the outbox, through {@code send()}. Neither blocks, so
 * a networked scenario stays single-threaded and inside its act loop.</p>
 *
 * <p><b>Order.</b> Only the reader thread adds events, so for one connection
 * they come out as CONNECTED, then MESSAGEs in the order sent, then one
 * DISCONNECTED, and nothing after that.</p>
 *
 * <p><b>Bounds.</b> The outbox holds at most {@link #MAX_OUTBOX_BYTES}; a send
 * that would overflow it drops the connection instead of blocking. The
 * inbox (events not yet polled) holds at most {@link #MAX_INBOX_MESSAGES}
 * or {@link #MAX_INBOX_BYTES}; a peer that overruns it is dropped as
 * flooding. One message is at most {@link #MAX_MESSAGE_BYTES} of UTF-8.</p>
 *
 * <p><b>Liveness.</b> The writer pings every {@link #PING_INTERVAL_MS} when
 * it has nothing else to send, and drops the connection when nothing at all
 * has been heard for {@link #IDLE_TIMEOUT_MS}.</p>
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
    /** Unpolled bytes a connection may hold before it is dropped. */
    public static final long MAX_INBOX_BYTES = 1024 * 1024;
    /** A ping goes out after this long without anything to send. */
    public static final int PING_INTERVAL_MS = 5000;
    /** Nothing heard for this long means the other side is gone. */
    public static final int IDLE_TIMEOUT_MS = 20000;
    /** How long to wait for a server to answer a connection attempt. */
    public static final int CONNECT_TIMEOUT_MS = 10000;
    /** How long a handshake may take. */
    static final int HANDSHAKE_TIMEOUT_MS = 10000;
    /** After sending a close, how long to wait for the other side's close before dropping the socket. */
    static final int CLOSE_WAIT_MS = 500;

    public static final int CONNECTING = 0;
    public static final int CONNECTED = 1;
    public static final int CLOSED = 2;
    public static final int FAILED = 3;

    private static final byte[] CLOSE_MARKER = new byte[0];

    private final int id;
    private final boolean serverSide;
    private final Queue<NetEvent> events;
    private final Addresses.Address address;     // client side only
    private final String rejectReason;           // server side: close at once with this reason
    private final Runnable whenFinished;         // the owner's bookkeeping, or null
    private volatile Socket socket;
    private final LinkedBlockingQueue<byte[]> outbox = new LinkedBlockingQueue<>();
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicInteger unreadMessages = new AtomicInteger();
    private final AtomicLong unreadBytes = new AtomicLong();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final CountDownLatch peerClosed = new CountDownLatch(1);
    private volatile int state = CONNECTING;
    private volatile boolean closedByOwner;
    private volatile String localReason;
    private volatile String peerReason;
    private volatile String error;
    private volatile long lastHeard = System.currentTimeMillis();
    private volatile String remoteAddress = "";

    private Link(int id, boolean serverSide, Queue<NetEvent> events, Socket socket,
                 Addresses.Address address, String rejectReason, Runnable whenFinished)
    {
        this.id = id;
        this.serverSide = serverSide;
        this.events = events;
        this.socket = socket;
        this.address = address;
        this.rejectReason = rejectReason;
        this.whenFinished = whenFinished;
        if (socket != null && socket.getRemoteSocketAddress() != null) {
            remoteAddress = socket.getRemoteSocketAddress().toString();
        }
    }

    /**
     * Server side: take over an accepted socket and start its threads.
     *
     * @param rejectReason  Null normally; otherwise the handshake completes and
     *                      the connection is closed at once with this reason,
     *                      and no event is ever reported for it.
     * @param whenFinished  Run (on a network thread) once the link is over,
     *                      whether or not it ever connected; may be null.
     */
    public static Link accept(int id, Socket socket, Queue<NetEvent> events, String rejectReason,
                              Runnable whenFinished)
    {
        Link link = new Link(id, true, events, socket, null, rejectReason, whenFinished);
        link.startReader();
        return link;
    }

    /** Client side: connect in the background and start the threads. */
    public static Link connect(Addresses.Address address, Queue<NetEvent> events)
    {
        Link link = new Link(0, false, events, null, address, null, null);
        link.startReader();
        return link;
    }

    private void startReader()
    {
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

    public String getRemoteAddress()
    {
        return remoteAddress;
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
     * Queue a text message. Never blocks. Silently dropped once the link is
     * closing; drops the whole connection if the outbox is full.
     *
     * @throws IllegalArgumentException if the text is longer than {@link #MAX_MESSAGE_BYTES}.
     */
    public void send(String text)
    {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("message too long: " + payload.length
                    + " bytes, the limit is " + MAX_MESSAGE_BYTES);
        }
        if (closing.get() || finished.get()) {
            return;
        }
        byte[] frame = Frames.encode(Frames.OP_TEXT, payload, !serverSide, ThreadLocalRandom.current());
        if (pendingBytes.get() + frame.length > MAX_OUTBOX_BYTES) {
            drop("send buffer full: the other side is not keeping up");
            return;
        }
        pendingBytes.addAndGet(frame.length);
        outbox.add(frame);
    }

    /**
     * Close politely: everything already queued goes first, then a close frame
     * carrying the reason, which the other side reports as its DISCONNECTED
     * text.
     */
    public void close(String reason)
    {
        if (closing.compareAndSet(false, true)) {
            closedByOwner = true;
            localReason = reason == null ? "" : reason;
            outbox.add(Frames.encode(Frames.OP_CLOSE, Frames.closePayload(1000, localReason),
                    !serverSide, ThreadLocalRandom.current()));
            outbox.add(CLOSE_MARKER);
            if (socket == null) {
                // Still connecting: nothing to say goodbye to.
                finish("closed");
            }
        }
    }

    /** The owner has polled an event of this link: keep the inbox count right. */
    public void polled(NetEvent event)
    {
        if (event.getType() == NetEvent.MESSAGE) {
            unreadMessages.decrementAndGet();
            unreadBytes.addAndGet(-event.getText().length());
        }
    }

    // ---- failure ----

    /** Drop the connection at once with this reason (no close frame). */
    private void drop(String reason)
    {
        if (closing.compareAndSet(false, true)) {
            localReason = reason;
        }
        closeSocket();
    }

    /** Send a close frame with a protocol reason, then drop. */
    private void refuse(int code, String reason)
    {
        if (closing.compareAndSet(false, true)) {
            localReason = reason;
            outbox.add(Frames.encode(Frames.OP_CLOSE, Frames.closePayload(code, reason),
                    !serverSide, ThreadLocalRandom.current()));
            outbox.add(CLOSE_MARKER);
        }
    }

    private void closeSocket()
    {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            }
            catch (IOException e) {
                // already closed
            }
        }
    }

    /** The one place a link ends: state, error, and (if it ever connected) the DISCONNECTED event. */
    private void finish(String reason)
    {
        finish(reason, true);
    }

    /**
     * @param hangUp  Close the socket now. False when a close frame is still to
     *                go out, in which case the writer hangs up after sending it.
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
        if (wasConnected) {
            events.add(new NetEvent(NetEvent.DISCONNECTED, id, reason));
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
        if (rejectReason != null) {
            // Never reported as connected: say why and go.
            refuse(1013, rejectReason);
            startWriter();          // sends the close frame, then hangs up
            error = rejectReason;
            state = CLOSED;
            drainQuietly(in);
            peerClosed.countDown();
            return;
        }
        state = CONNECTED;
        lastHeard = System.currentTimeMillis();
        events.add(new NetEvent(NetEvent.CONNECTED, id, ""));
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
                                        + MAX_MESSAGE_BYTES + " bytes)");
                            }
                            partial.write(frame.payload, 0, frame.payload.length);
                            if (frame.fin) {
                                deliver(partial.toByteArray());
                                partial = null;
                            }
                        }
                        break;
                    case Frames.OP_BINARY:
                        throw new Frames.ProtocolException("binary messages are not accepted");
                    case Frames.OP_PING:
                        outbox.add(Frames.encode(Frames.OP_PONG, frame.payload, !serverSide, ThreadLocalRandom.current()));
                        break;
                    case Frames.OP_PONG:
                        break;
                    case Frames.OP_CLOSE:
                        peerReason = Frames.closeReason(frame.payload);
                        if (peerReason.isEmpty()) {
                            peerReason = closing.get() ? localReason : "closed by the other side";
                        }
                        if (closing.compareAndSet(false, true)) {
                            localReason = peerReason;
                            outbox.add(Frames.encode(Frames.OP_CLOSE, Frames.closePayload(1000, ""),
                                    !serverSide, ThreadLocalRandom.current()));
                            outbox.add(CLOSE_MARKER);
                        }
                        peerClosed.countDown();
                        finish(peerReason);
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
            int code = reason.startsWith("message too long") ? 1009 : 1002;
            refuse(code, reason);
            finish(reason, false);
            drainQuietly(in);
        }
        catch (SocketTimeoutException e) {
            finish(reasonOr("timed out: nothing heard for " + (IDLE_TIMEOUT_MS / 1000) + " seconds"));
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
        if (unreadMessages.get() >= MAX_INBOX_MESSAGES || unreadBytes.get() >= MAX_INBOX_BYTES) {
            throw new Frames.ProtocolException("flooding: too many messages not yet read");
        }
        String text = new String(payload, StandardCharsets.UTF_8);
        unreadMessages.incrementAndGet();
        unreadBytes.addAndGet(text.length());
        events.add(new NetEvent(NetEvent.MESSAGE, id, text));
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
        s.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
        InputStream raw = new BufferedInputStream(s.getInputStream());
        OutputStream out = new BufferedOutputStream(s.getOutputStream());
        if (serverSide) {
            Handshake.Head request = Handshake.readHead(raw);
            bytesReceived.addAndGet(request.wireBytes);
            bytesSent.addAndGet(Handshake.answerClient(request, out));
        }
        else {
            String key = Handshake.newKey(ThreadLocalRandom.current());
            bytesSent.addAndGet(Handshake.askServer(out, address.host, address.port, address.secure, address.path, key));
            Handshake.Head reply = Handshake.readHead(raw);
            bytesReceived.addAndGet(reply.wireBytes);
            Handshake.checkServer(reply, key);
        }
        s.setSoTimeout(IDLE_TIMEOUT_MS * 2);
        return new DataInputStream(raw);
    }

    private Socket openClientSocket() throws IOException
    {
        Socket plain = new Socket();
        try {
            plain.connect(new InetSocketAddress(address.host, address.port), CONNECT_TIMEOUT_MS);
            if (!address.secure) {
                return plain;
            }
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket(plain, address.host, address.port, true);
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(params);
            ssl.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
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
            drop(reasonOr("connection lost"));
            return;
        }
        try {
            while (true) {
                byte[] frame = outbox.poll(PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    if (System.currentTimeMillis() - lastHeard > IDLE_TIMEOUT_MS) {
                        drop("timed out: nothing heard for " + (IDLE_TIMEOUT_MS / 1000) + " seconds");
                        return;
                    }
                    frame = Frames.encode(Frames.OP_PING, new byte[0], !serverSide, ThreadLocalRandom.current());
                }
                if (frame == CLOSE_MARKER) {
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
                Frames.write(out, frame);
                bytesSent.addAndGet(frame.length);
                if ((frame[0] & 0x0F) == Frames.OP_TEXT) {
                    pendingBytes.addAndGet(-frame.length);
                }
            }
        }
        catch (IOException e) {
            drop(reasonOr("connection lost"));
        }
        catch (InterruptedException e) {
            drop(reasonOr("closed"));
        }
    }
}
