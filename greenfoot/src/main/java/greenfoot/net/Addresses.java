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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Addresses as people type them, and this machine's own.
 */
@OnThread(Tag.Any)
public final class Addresses
{
    /** A parsed WebSocket address. */
    public static final class Address
    {
        public final boolean secure;
        public final String host;
        public final int port;
        public final String path;

        Address(boolean secure, String host, int port, String path)
        {
            this.secure = secure;
            this.host = host;
            this.port = port;
            this.path = path;
        }

        /** The address written out in full, for example {@code ws://192.168.1.5:7777/}. */
        public String toUrl()
        {
            String h = host.contains(":") ? "[" + host + "]" : host;
            return (secure ? "wss://" : "ws://") + h + ":" + port + path;
        }
    }

    private Addresses()
    {
    }

    /**
     * Make sense of what a player typed: {@code host}, {@code host:port},
     * {@code ws://host:port/path}, {@code wss://...}, or an IPv6 address in
     * brackets such as {@code [::1]:7777}. Spaces around it are ignored.
     *
     * @param typed        The text.
     * @param defaultPort  The port to use when none is given (a plain
     *                     {@code wss://host} with no port uses 443).
     * @return The address, or null when the text cannot be an address.
     */
    public static Address parse(String typed, int defaultPort)
    {
        if (typed == null) {
            return null;
        }
        String s = typed.trim();
        if (s.isEmpty()) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return null;    // control characters have no place in an address (or a request line)
            }
        }
        boolean secure = false;
        int schemeEnd = s.indexOf("://");
        if (schemeEnd >= 0) {
            String scheme = s.substring(0, schemeEnd).toLowerCase();
            if (scheme.equals("wss") || scheme.equals("https")) {
                secure = true;
            }
            else if (!scheme.equals("ws") && !scheme.equals("http")) {
                return null;
            }
            s = s.substring(schemeEnd + 3);
        }
        String host;
        String rest;
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close < 0) {
                return null;
            }
            host = s.substring(1, close);
            rest = s.substring(close + 1);
        }
        else {
            int end = 0;
            while (end < s.length() && s.charAt(end) != ':' && s.charAt(end) != '/') {
                end++;
            }
            host = s.substring(0, end);
            rest = s.substring(end);
        }
        if (host.isEmpty() || host.contains(" ")) {
            return null;
        }
        int port = secure && defaultPort <= 0 ? 443 : defaultPort;
        if (schemeEnd >= 0 && secure && !rest.startsWith(":")) {
            port = 443;
        }
        else if (schemeEnd >= 0 && !secure && !rest.startsWith(":") && defaultPort <= 0) {
            port = 80;
        }
        if (rest.startsWith(":")) {
            int slash = rest.indexOf('/');
            String digits = slash < 0 ? rest.substring(1) : rest.substring(1, slash);
            try {
                port = Integer.parseInt(digits);
            }
            catch (NumberFormatException e) {
                return null;
            }
            if (port < 1 || port > 65535) {
                return null;
            }
            rest = slash < 0 ? "" : rest.substring(slash);
        }
        if (port < 1 || port > 65535) {
            return null;
        }
        String path = rest.isEmpty() ? "/" : rest;
        if (!path.startsWith("/")) {
            return null;
        }
        return new Address(secure, host, port, path);
    }

    /**
     * This machine's addresses on its networks, for "tell your friends to join
     * at ...": IPv4 addresses first, then IPv6 ones that are not link-local,
     * and never the loopback address.
     */
    public static List<String> local()
    {
        List<String> v4 = new ArrayList<>();
        List<String> v6 = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
                        continue;
                    }
                    String text = a.getHostAddress();
                    int zone = text.indexOf('%');
                    if (zone >= 0) {
                        text = text.substring(0, zone);
                    }
                    if (a instanceof Inet4Address) {
                        v4.add(text);
                    }
                    else {
                        v6.add(text);
                    }
                }
            }
        }
        catch (SocketException e) {
            // no interfaces to report
        }
        v4.addAll(v6);
        return v4;
    }
}
