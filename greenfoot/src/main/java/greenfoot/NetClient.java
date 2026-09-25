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

import greenfoot.net.Addresses;
import greenfoot.net.Link;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A connection from your scenario to a {@link NetServer}. Make one with
 * {@link Network#connect(String)}.
 *
 * <p>Connecting takes a moment and happens in the background, so check
 * {@link #getStatus()} from your act method: it goes from {@code CONNECTING}
 * to {@code CONNECTED}, or to {@code FAILED} with {@link #getError()} saying
 * why in words ("no such host", "connection refused", "timed out"). Once
 * connected, {@link #poll()} gives you the server's messages in order and,
 * at the end, one {@code DISCONNECTED} event with the reason; {@link #send(String)}
 * queues a message and never blocks.</p>
 *
 * <p>A client is not tied to the world that made it: you can connect in one
 * world, wait for the server's first message, and then build the real world
 * and hand the client over to it.</p>
 *
 * @author SuperGreenfoot contributors
 * @since SuperGreenfoot 0.2.0
 */
@OnThread(Tag.Any)
public final class NetClient
{
    /** Still trying to reach the server. */
    public static final int CONNECTING = Link.CONNECTING;
    /** Connected: messages flow. */
    public static final int CONNECTED = Link.CONNECTED;
    /** Was connected, and has been closed by either side. */
    public static final int CLOSED = Link.CLOSED;
    /** Never connected; {@link #getError()} says why. */
    public static final int FAILED = Link.FAILED;

    private final ConcurrentLinkedQueue<NetEvent> events = new ConcurrentLinkedQueue<>();
    private final Link link;
    private final String address;
    private final String badAddress;

    NetClient(String typed, Addresses.Address parsed)
    {
        if (parsed == null) {
            link = null;
            address = typed == null ? "" : typed.trim();
            badAddress = "not an address: \"" + address + "\" (try host:port, or ws://host:port)";
        }
        else {
            address = parsed.toUrl();
            badAddress = null;
            link = Link.connect(parsed, events);
        }
    }

    /**
     * The next thing that has happened, or null when there is nothing more
     * right now: {@code CONNECTED} once, then {@code MESSAGE}s in order, then
     * one {@code DISCONNECTED}. The connection id in these events is always 0.
     */
    public NetEvent poll()
    {
        NetEvent e = events.poll();
        if (e != null && link != null) {
            link.polled(e);
        }
        return e;
    }

    /**
     * Queue a message for the server. Never blocks. Messages sent before the
     * connection is up go out as soon as it is; messages sent after it has
     * closed are dropped.
     *
     * @throws IllegalArgumentException if the text is longer than {@link Network#MAX_MESSAGE_LENGTH} bytes.
     */
    public void send(String text)
    {
        if (link != null) {
            link.send(text);
        }
    }

    /** @return CONNECTING, CONNECTED, CLOSED or FAILED. */
    public int getStatus()
    {
        return link == null ? FAILED : link.getState();
    }

    /** @return True while the connection is open. */
    public boolean isConnected()
    {
        return getStatus() == CONNECTED;
    }

    /**
     * @return Why the connection failed or closed, in words fit to show a
     *         player, or null while it is connecting or connected.
     */
    public String getError()
    {
        if (link == null) {
            return badAddress;
        }
        return link.getError();
    }

    /** Close the connection politely, after everything already sent. */
    public void close()
    {
        close("closed");
    }

    /** Close the connection with a reason the server sees as its {@code DISCONNECTED} text. */
    public void close(String reason)
    {
        if (link != null) {
            link.close(reason);
        }
        Network.forget(this);
    }

    /** @return The address this client was asked to connect to, written out in full. */
    public String getAddress()
    {
        return address;
    }

    /** @return Bytes put on the wire so far (WebSocket frames, before any TLS). */
    public long getBytesSent()
    {
        return link == null ? 0 : link.getBytesSent();
    }

    /** @return Bytes read from the wire so far. */
    public long getBytesReceived()
    {
        return link == null ? 0 : link.getBytesReceived();
    }

    /** @return Bytes queued but not yet sent. */
    public long getPendingBytes()
    {
        return link == null ? 0 : link.getPendingBytes();
    }

    @Override
    public String toString()
    {
        String[] names = { "connecting", "connected", "closed", "failed" };
        return "NetClient " + address + " (" + names[getStatus()] + ")";
    }
}
