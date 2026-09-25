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

import greenfoot.net.Link;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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
 * 256 KB waiting) is dropped rather than letting your game wait, and so is
 * one that sends faster than you poll (more than 2,000 unread messages).
 * A connection nothing has been heard from for 20 seconds is dropped as
 * lost. Every drop shows up as a {@code DISCONNECTED} event with the reason
 * in words.</p>
 *
 * @author SuperGreenfoot contributors
 * @since SuperGreenfoot 0.2.0
 */
@OnThread(Tag.Any)
public final class NetServer
{
    private final ConcurrentLinkedQueue<NetEvent> events = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Link> links = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final AtomicLong retiredSent = new AtomicLong();
    private final AtomicLong retiredReceived = new AtomicLong();
    private final ServerSocket serverSocket;
    private final int port;
    private final String error;
    private volatile int maxConnections;
    private volatile boolean stopped;

    NetServer(int requestedPort, int maxConnections)
    {
        this.maxConnections = maxConnections;
        ServerSocket socket = null;
        String problem = null;
        try {
            socket = new ServerSocket(requestedPort);
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
            int id = nextId.getAndIncrement();
            if (links.size() >= maxConnections) {
                Link.accept(id, client, events, "the server is full", null);
            }
            else {
                links.put(id, Link.accept(id, client, events, null, () -> retire(id)));
            }
        }
    }

    /** A link is over (on its own thread): it no longer counts as a connection, but its bytes still do. */
    private void retire(int id)
    {
        Link link = links.remove(id);
        if (link != null) {
            retiredSent.addAndGet(link.getBytesSent());
            retiredReceived.addAndGet(link.getBytesReceived());
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
            Link link = links.get(e.getConnectionId());
            if (link != null) {
                link.polled(e);
            }
        }
        return e;
    }

    /**
     * Send a message to one connection. Never blocks: the message is queued
     * and goes out in the background, in order. Nothing happens if that
     * connection has gone.
     *
     * @throws IllegalArgumentException if the text is longer than {@link Network#MAX_MESSAGE_LENGTH} bytes.
     */
    public void send(int connectionId, String text)
    {
        Link link = links.get(connectionId);
        if (link != null) {
            link.send(text);
        }
    }

    /** Send a message to every connection. */
    public void broadcast(String text)
    {
        for (Link link : links.values()) {
            link.send(text);
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
        for (Link link : links.values()) {
            link.close("server stopped");
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
