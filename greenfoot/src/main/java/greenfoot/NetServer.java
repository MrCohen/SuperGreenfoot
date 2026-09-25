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

import greenfoot.net.Inbox;
import greenfoot.net.Link;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A server other copies of your scenario can connect to. Start one with
 * {@link Network#startServer(int)}.
 *
 * <p>Everything happens when you ask: in your act method, call {@link #poll()}
 * until it returns null to see who has connected, what they sent and who has
 * left, and call {@link #send(int, String)} or {@link #broadcast(String)} to
 * talk back. Nothing here blocks, and nothing here ever calls your code. The
 * server speaks WebSocket, so browsers can connect too.</p>
 *
 * <p>Each connection has a number that is never reused while the server
 * runs. For one connection the events come in order: {@code CONNECTED},
 * then its messages in the order they were sent, then {@code DISCONNECTED}.</p>
 *
 * <p><b>Limits.</b> A message is at most {@link Network#MAX_MESSAGE_LENGTH}
 * bytes of UTF-8 text; sending a longer one throws
 * {@code IllegalArgumentException}, and a connection that sends one is
 * dropped. A connection that will not take what you send it (more than
 * 256 KB waiting, or nothing taken for 20 seconds) is dropped rather than
 * letting your game wait, and so is one that sends faster than you poll
 * (more than 2,000 unread messages). A connection nothing has been heard
 * from for 20 seconds is dropped as lost. Every drop shows up as a
 * {@code DISCONNECTED} event with the reason in words.</p>
 *
 * @author SuperGreenfoot contributors
 * @since SuperGreenfoot 0.2.0
 */
@OnThread(Tag.Any)
public final class NetServer
{
    private static final String NOT_READING = "the server is not reading messages (is the host's game paused?)";

    private final ConcurrentLinkedQueue<NetEvent> events = new ConcurrentLinkedQueue<>();
    /** Connections whose handshake is done: the ones that count. */
    private final Map<Integer, Link> links = new ConcurrentHashMap<>();
    /** Sockets still in their handshake; they hold no connection slot. */
    private final Map<Integer, Link> pending = new ConcurrentHashMap<>();
    /** Handshakes in progress per remote address (guarded by lock). */
    private final Map<String, Integer> pendingByHost = new HashMap<>();
    private final Object lock = new Object();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicInteger unpolledEvents = new AtomicInteger();
    private final AtomicLong unpolledChars = new AtomicLong();
    private final AtomicLong retiredSent = new AtomicLong();
    private final AtomicLong retiredReceived = new AtomicLong();
    private final ServerSocket serverSocket;
    private final int port;
    private final boolean localOnly;
    private final String error;
    private volatile int maxConnections;
    private volatile boolean stopped;

    NetServer(int requestedPort, int maxConnections)
    {
        this(requestedPort, maxConnections, null);
    }

    /**
     * @param bindAddress  The interface to listen on, or null for every interface.
     */
    NetServer(int requestedPort, int maxConnections, InetAddress bindAddress)
    {
        this.maxConnections = maxConnections;
        this.localOnly = bindAddress != null && bindAddress.isLoopbackAddress();
        ServerSocket socket = null;
        String problem = null;
        try {
            // The same defaults as new ServerSocket(port) (reuse-address differs per platform; leave it).
            socket = new ServerSocket();
            socket.bind(new InetSocketAddress(bindAddress, requestedPort));
        }
        catch (BindException e) {
            problem = "port " + requestedPort + " is already in use (is another server running?)";
        }
        catch (IllegalArgumentException e) {
            problem = "not a valid port: " + requestedPort;
        }
        catch (IOException e) {
            problem = "could not open port " + requestedPort + ": " + e.getMessage();
        }
        if (problem != null && socket != null) {
            try {
                socket.close();
            }
            catch (IOException e) {
                // nothing to keep
            }
            socket = null;
        }
        serverSocket = socket;
        error = problem;
        port = socket == null ? -1 : socket.getLocalPort();
        if (socket != null) {
            Thread t = new Thread(this::runAccept, "SuperGreenfoot-Net-accept-" + port);
            t.setDaemon(true);
            t.start();
        }
        else {
            stopped = true;
        }
    }

    private void runAccept()
    {
        while (!stopped) {
            Socket client;
            try {
                client = serverSocket.accept();
            }
            catch (IOException e) {
                break;          // the server socket was closed: we are stopping
            }
            if (stopped) {
                closeQuietly(client);   // accepted just as stop() ran
                break;
            }
            String host = hostOf(client);
            String refusal = null;
            boolean overloaded = false;
            synchronized (lock) {
                if (stopped) {
                    // stop() sets the flag before it takes this lock to close what is pending, so a
                    // socket accepted in between is either seen here or was in its snapshot.
                    closeQuietly(client);
                    break;
                }
                if (links.size() >= maxConnections) {
                    refusal = "the server is full";
                }
                else if (unpolledEvents.get() >= Link.serverMaxInboxMessages()
                        || unpolledChars.get() >= Link.serverMaxInboxChars()) {
                    refusal = NOT_READING;
                }
                else if (pending.size() >= Link.MAX_HANDSHAKES
                        || pendingByHost.getOrDefault(host, 0) >= Link.maxHandshakesPerAddress()) {
                    overloaded = true;
                }
                else {
                    int id = nextId.getAndIncrement();
                    pendingByHost.merge(host, 1, Integer::sum);
                    Link link = Link.accept(id, client, new ServerInbox(), this::admit, () -> finished(id, host));
                    pending.put(id, link);
                    link.start();
                }
            }
            if (refusal != null) {
                // No thread for a refusal: a short answer that says why, on this thread.
                Link.refuseSocket(client, refusal);
            }
            else if (overloaded) {
                closeQuietly(client);
            }
        }
    }

    /** Asked on the reader thread once a handshake is done: null admits it, a reason refuses it. */
    private String admit(Link link)
    {
        synchronized (lock) {
            if (pending.remove(link.getId()) != null) {
                handshakeOver(link.getRemoteHost());
            }
            if (stopped) {
                return "server stopped";
            }
            if (links.size() >= maxConnections) {
                return "the server is full";
            }
            if (unpolledEvents.get() >= Link.serverMaxInboxMessages()
                    || unpolledChars.get() >= Link.serverMaxInboxChars()) {
                return NOT_READING;
            }
            links.put(link.getId(), link);
            return null;
        }
    }

    /** A link is over (on its own thread): it no longer counts as a connection, but its bytes still do. */
    private void finished(int id, String host)
    {
        synchronized (lock) {
            if (pending.remove(id) != null) {
                handshakeOver(host);
            }
            Link link = links.remove(id);
            if (link != null) {
                retiredSent.addAndGet(link.getBytesSent());
                retiredReceived.addAndGet(link.getBytesReceived());
            }
        }
    }

    private void handshakeOver(String host)
    {
        Integer n = pendingByHost.get(host);
        if (n != null) {
            if (n <= 1) {
                pendingByHost.remove(host);
            }
            else {
                pendingByHost.put(host, n - 1);
            }
        }
    }

    private static String hostOf(Socket socket)
    {
        if (socket.getInetAddress() != null) {
            return socket.getInetAddress().getHostAddress();
        }
        return "";
    }

    private static void closeQuietly(Socket socket)
    {
        try {
            socket.close();
        }
        catch (IOException e) {
            // already closed
        }
    }

    /** The links report here. Everything counts against the server-wide inbox limit. */
    @OnThread(Tag.Any)
    private final class ServerInbox implements Inbox
    {
        @Override
        public void connected(int id)
        {
            unpolledEvents.incrementAndGet();
            events.add(new NetEvent(NetEvent.CONNECTED, id, ""));
        }

        @Override
        public boolean message(int id, String text)
        {
            if (unpolledEvents.get() >= Link.serverMaxInboxMessages()
                    || unpolledChars.get() >= Link.serverMaxInboxChars()) {
                return false;
            }
            unpolledEvents.incrementAndGet();
            unpolledChars.addAndGet(text.length());
            events.add(new NetEvent(NetEvent.MESSAGE, id, text));
            return true;
        }

        @Override
        public void disconnected(int id, String reason)
        {
            unpolledEvents.incrementAndGet();
            events.add(new NetEvent(NetEvent.DISCONNECTED, id, reason));
        }
    }

    /**
     * The next thing that has happened, or null when there is nothing more
     * right now. Call this from your act method until it returns null.
     */
    public NetEvent poll()
    {
        NetEvent e = events.poll();
        if (e != null) {
            unpolledEvents.decrementAndGet();
            if (e.getType() == NetEvent.MESSAGE) {
                unpolledChars.addAndGet(-e.getText().length());
                Link link = links.get(e.getConnectionId());
                if (link != null) {
                    link.polledMessage(e.getText());
                }
            }
        }
        return e;
    }

    /**
     * Send a message to one connection. Never blocks: the message is queued
     * and goes out in the background, in order. Nothing happens if that
     * connection has gone.
     *
     * @throws IllegalArgumentException if the text is null or longer than {@link Network#MAX_MESSAGE_LENGTH} bytes.
     */
    public void send(int connectionId, String text)
    {
        Link link = links.get(connectionId);
        if (link != null) {
            link.send(text);
        }
        else {
            Link.encodeText(text, true);    // still refuse a bad message, so the mistake shows
        }
    }

    /**
     * Send a message to every connection.
     *
     * @throws IllegalArgumentException if the text is null or longer than {@link Network#MAX_MESSAGE_LENGTH} bytes.
     */
    public void broadcast(String text)
    {
        byte[] frame = Link.encodeText(text, true);     // encoded once, shared by every connection
        for (Link link : links.values()) {
            link.sendFrame(frame);
        }
    }

    /**
     * Close one connection, telling it why. Everything already sent to it
     * goes first, so a last message before the kick is delivered; the other
     * side sees the reason as the text of its {@code DISCONNECTED} event.
     */
    public void kick(int connectionId, String reason)
    {
        Link link = links.get(connectionId);
        if (link != null) {
            link.close(reason);
        }
    }

    /** Close one connection without a stated reason. */
    public void kick(int connectionId)
    {
        kick(connectionId, "disconnected by the server");
    }

    /**
     * Stop the server: no more connections are accepted and every
     * connection is closed with the reason "server stopped". The
     * {@code DISCONNECTED} events for them can still be polled afterwards.
     */
    public void stop()
    {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            // already closed
        }
        List<Link> open;
        List<Link> shaking;
        synchronized (lock) {
            open = new ArrayList<>(links.values());
            shaking = new ArrayList<>(pending.values());
        }
        for (Link link : open) {
            link.close("server stopped");
        }
        for (Link link : shaking) {
            link.dropNow("server stopped");
        }
        Network.forget(this);
    }

    /** @return The port the server is listening on (useful after asking for port 0), or -1 if it failed to start. */
    public int getPort()
    {
        return port;
    }

    /** @return True while the server accepts connections. */
    public boolean isListening()
    {
        return !stopped;
    }

    /** @return Why the server could not start, in words, or null if it started. */
    public String getError()
    {
        return error;
    }

    /** @return How many connections are open right now. */
    public int getConnectionCount()
    {
        return links.size();
    }

    /** @return The ids of the open connections, lowest first. */
    public List<Integer> getConnectionIds()
    {
        List<Integer> ids = new ArrayList<>(links.keySet());
        java.util.Collections.sort(ids);
        return ids;
    }

    /** @return Where a connection comes from (address and port), or "" if it has gone. */
    public String getRemoteAddress(int connectionId)
    {
        Link link = links.get(connectionId);
        return link == null ? "" : link.getRemoteAddress();
    }

    /**
     * @return The web page a connection came from, when it came from a web
     *         browser: the {@code Origin} the browser sent, such as
     *         {@code https://example.org}. "" for a connection from a
     *         SuperGreenfoot scenario, or one that has gone. The server
     *         does not act on it; a scenario that wants to allow only its
     *         own web page can check it and kick the rest.
     */
    public String getOrigin(int connectionId)
    {
        Link link = links.get(connectionId);
        return link == null ? "" : link.getOrigin();
    }

    /**
     * @return True when only programs on this computer can reach the server
     *         (started with {@link Network#startServer(int, int, boolean)}).
     */
    public boolean isLocalOnly()
    {
        return localOnly;
    }

    /**
     * @return How many bytes are queued for a connection but not yet sent. A
     *         number that keeps growing means that connection is not keeping
     *         up, and it will be dropped at 256 KB.
     */
    public long getPendingBytes(int connectionId)
    {
        Link link = links.get(connectionId);
        return link == null ? 0 : link.getPendingBytes();
    }

    /** The most connections this server accepts at once; the rest are refused with "the server is full". */
    public int getMaxConnections()
    {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections)
    {
        this.maxConnections = Math.max(0, maxConnections);
    }

    /** @return Bytes put on the wire so far, over every connection there has been (WebSocket frames, before any TLS). */
    public long getBytesSent()
    {
        long total = retiredSent.get();
        for (Link link : links.values()) {
            total += link.getBytesSent();
        }
        return total;
    }

    /** @return Bytes read from the wire so far, over every connection there has been. */
    public long getBytesReceived()
    {
        long total = retiredReceived.get();
        for (Link link : links.values()) {
            total += link.getBytesReceived();
        }
        return total;
    }

    @Override
    public String toString()
    {
        return error != null ? "NetServer (failed: " + error + ")"
                : "NetServer on port " + port + " (" + links.size() + " connections)";
    }
}
