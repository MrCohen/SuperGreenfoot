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
import greenfoot.net.ServerMode;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Networking for scenarios: one copy of a scenario can be a server, and
 * others can connect to it and exchange text messages.
 *
 * <pre>
 *   // The host:
 *   NetServer server = Network.startServer(7777);
 *   ...
 *   NetEvent e = server.poll();                // in act(), until null
 *
 *   // A player joining:
 *   NetClient client = Network.connect("192.168.1.5:7777");
 *   ...
 *   if (client.isConnected()) client.send("HELLO;Ada");
 * </pre>
 *
 * <p><b>How it stays simple.</b> The network's threads live inside the
 * engine and never call your code. Your scenario only ever polls for what
 * has arrived and sends text, from its act method, so it stays as
 * single-threaded as any other scenario. Messages are text, arrive whole
 * and in the order they were sent, and each connection reports one
 * {@code CONNECTED}, then its messages, then one {@code DISCONNECTED}
 * with the reason in words. The protocol on the wire is WebSocket, so a
 * scenario exported to the web can join a desktop server ({@code wss://}
 * addresses work from the desktop too).</p>
 *
 * <p><b>Dedicated servers.</b> An exported game can run as a server with no
 * window ({@code --server}); {@link #isDedicatedServer()},
 * {@link #getServerSetting(String)} and {@link #pollConsoleCommand()} are for
 * that run.</p>
 *
 * @author SuperGreenfoot contributors
 * @since SuperGreenfoot 0.2.0
 */
@OnThread(Tag.Any)
public final class Network
{
    /** The longest message either side accepts, in bytes of UTF-8 text. */
    public static final int MAX_MESSAGE_LENGTH = Link.MAX_MESSAGE_BYTES;
    /** The most connections a server takes unless {@link #startServer(int, int)} says otherwise. */
    public static final int DEFAULT_MAX_CONNECTIONS = 64;

    private static final List<NetServer> servers = new ArrayList<>();
    private static final List<NetClient> clients = new ArrayList<>();

    private Network()
    {
    }

    /**
     * Start a server on a port. It listens at once, in the background.
     *
     * @param port  The port to listen on, or 0 to let the system pick a free
     *              one (then ask the server with {@code getPort()}).
     * @return The server. If the port could not be opened, the server is not
     *         listening and {@code getError()} says why.
     */
    public static NetServer startServer(int port)
    {
        return startServer(port, DEFAULT_MAX_CONNECTIONS);
    }

    /**
     * Start a server that accepts at most so many connections at once; the
     * rest are turned away with the reason "the server is full".
     */
    public static NetServer startServer(int port, int maxConnections)
    {
        NetServer server = new NetServer(port, maxConnections);
        if (server.isListening()) {
            synchronized (servers) {
                servers.add(server);
            }
        }
        return server;
    }

    /**
     * Connect to a server. Returns at once; the connection is made in the
     * background, so watch {@code getStatus()}.
     *
     * @param address  Where the server is: {@code host:port},
     *                 {@code ws://host:port}, {@code wss://host} (a secure
     *                 connection, port 443 unless given), or an IPv6 address
     *                 in brackets such as {@code [::1]:7777}.
     * @return The client. If the text is not an address, its status is
     *         {@code FAILED} straight away and {@code getError()} says so.
     */
    public static NetClient connect(String address)
    {
        return connect(address, 0);
    }

    /**
     * Connect to a server, using a port of your choosing when the address
     * does not give one, so a player can type just the host.
     */
    public static NetClient connect(String address, int defaultPort)
    {
        NetClient client = new NetClient(address, Addresses.parse(address, defaultPort));
        if (client.getStatus() != NetClient.FAILED) {
            synchronized (clients) {
                clients.add(client);
            }
            client.start();     // after it is listed, so a client that fails at once is forgotten cleanly
        }
        return client;
    }

    /**
     * @return True if this copy of the scenario can be a server. On the
     *         desktop it always can; a scenario running in a web browser
     *         can only join.
     */
    public static boolean canHost()
    {
        return true;
    }

    /**
     * @return True when the scenario is running as a dedicated server: no
     *         window, nothing drawn, started from the command line with
     *         {@code --server}.
     */
    public static boolean isDedicatedServer()
    {
        return ServerMode.isDedicated();
    }

    /**
     * A setting from the dedicated server's {@code server.properties}
     * file, such as {@code port} or {@code password}.
     *
     * @return The value, or null when there is no such setting (or this is
     *         not a dedicated server).
     */
    public static String getServerSetting(String key)
    {
        return ServerMode.getSettings().getProperty(key);
    }

    /** A server setting, or the given default when it is not set. */
    public static String getServerSetting(String key, String defaultValue)
    {
        String value = ServerMode.getSettings().getProperty(key);
        return value == null ? defaultValue : value;
    }

    /**
     * On a dedicated server, the next line typed at its console, or null
     * when nothing has been typed. Pressing Ctrl-C at the console puts the
     * word {@code stop} here, and the server then has a few seconds to save
     * and call {@code Greenfoot.stop()}.
     */
    public static String pollConsoleCommand()
    {
        return ServerMode.pollCommand();
    }

    /**
     * This computer's addresses on its networks, to tell friends on the same
     * network where to join: IPv4 first. Empty if it is not on a network at
     * all. Players on the same computer can always use {@code localhost}.
     */
    public static List<String> getLocalAddresses()
    {
        return Addresses.local();
    }

    /**
     * Turn what a player typed into a full address, or find out that it is
     * not one: {@code "192.168.1.5"} with default port 7777 becomes
     * {@code "ws://192.168.1.5:7777/"}.
     *
     * @return The full address, or null if the text cannot be an address.
     */
    public static String normalizeAddress(String typed, int defaultPort)
    {
        Addresses.Address a = Addresses.parse(typed, defaultPort);
        return a == null ? null : a.toUrl();
    }

    /**
     * Stop every server and close every client this scenario has open. The
     * engine calls this when the scenario is reset, so a port is free again
     * for the next run; a scenario can call it too.
     */
    public static void closeAll()
    {
        List<NetServer> s;
        List<NetClient> c;
        synchronized (servers) {
            s = new ArrayList<>(servers);
        }
        synchronized (clients) {
            c = new ArrayList<>(clients);
        }
        for (NetServer server : s) {
            server.stop();
        }
        for (NetClient client : c) {
            client.close("scenario reset");
        }
    }

    static void forget(NetServer server)
    {
        synchronized (servers) {
            servers.remove(server);
        }
    }

    static void forget(NetClient client)
    {
        synchronized (clients) {
            clients.remove(client);
        }
    }
}
