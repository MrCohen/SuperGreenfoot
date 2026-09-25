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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * The WebSocket opening handshake (RFC 6455 section 4): an HTTP/1.1 GET with
 * an Upgrade header, answered by "101 Switching Protocols". Both sides live
 * here so that they agree with each other.
 */
@OnThread(Tag.Any)
final class Handshake
{
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    /** The most header bytes either side will read before giving up. */
    private static final int MAX_HEADER = 8192;
    private static final String NOT_A_GAME = "This is a SuperGreenfoot game server. Connect to it from a game, not from a web page.\r\n";

    /** A parsed HTTP head: the first line and the headers (names lower-cased). */
    static final class Head
    {
        final String firstLine;
        final Map<String, String> headers;
        /** Bytes read from the wire for this head. */
        final int wireBytes;

        Head(String firstLine, Map<String, String> headers, int wireBytes)
        {
            this.firstLine = firstLine;
            this.headers = headers;
            this.wireBytes = wireBytes;
        }

        String header(String name)
        {
            return headers.get(name.toLowerCase());
        }

        boolean headerContains(String name, String value)
        {
            String h = header(name);
            return h != null && h.toLowerCase().contains(value.toLowerCase());
        }
    }

    private Handshake()
    {
    }

    /**
     * Read an HTTP head: lines up to and including the first blank line.
     *
     * @throws IOException on end of stream, or a head longer than {@link #MAX_HEADER}.
     */
    static Head readHead(InputStream in) throws IOException
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int total = 0;
        int state = 0; // how many of \r\n\r\n have been seen
        while (state < 4) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("connection closed during the handshake");
            }
            if (++total > MAX_HEADER) {
                throw new Frames.ProtocolException("handshake header too long");
            }
            buf.write(b);
            if (b == '\r' && (state == 0 || state == 2)) {
                state++;
            }
            else if (b == '\n' && (state == 1 || state == 3)) {
                state++;
            }
            else {
                state = (b == '\r') ? 1 : 0;
            }
        }
        String text = new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = text.split("\r\n");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(), lines[i].substring(colon + 1).trim());
            }
        }
        return new Head(lines.length > 0 ? lines[0] : "", headers, total);
    }

    /** The Sec-WebSocket-Accept value for a client's Sec-WebSocket-Key. */
    static String accept(String key)
    {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("no SHA-1", e);
        }
    }

    /** A fresh Sec-WebSocket-Key: 16 random bytes, base64. */
    static String newKey(Random random)
    {
        byte[] raw = new byte[16];
        random.nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    /** True for a well-formed Sec-WebSocket-Key: base64 of exactly 16 bytes. */
    static boolean isKey(String key)
    {
        if (key == null || key.length() != 24) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(key).length == 16;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ---- server side ----

    /**
     * Check a client's request head and write the 101 reply, or write a plain
     * answer and fail.
     *
     * @return The bytes written.
     * @throws IOException when the request is not a WebSocket upgrade; a
     *         reply has already been written in that case.
     */
    static int answerClient(Head request, OutputStream out) throws IOException
    {
        boolean isGet = request.firstLine.startsWith("GET ");
        String key = request.header("Sec-WebSocket-Key");
        boolean upgrade = request.headerContains("Upgrade", "websocket")
                && request.headerContains("Connection", "upgrade");
        if (!isGet || !upgrade || key == null) {
            plainReply(out, isGet ? "200 OK" : "400 Bad Request", "", NOT_A_GAME);
            throw new Frames.ProtocolException("not a WebSocket connection");
        }
        String version = request.header("Sec-WebSocket-Version");
        if (version == null || !version.trim().equals("13")) {
            plainReply(out, "426 Upgrade Required", "Sec-WebSocket-Version: 13\r\n",
                    "This server speaks WebSocket version 13.\r\n");
            throw new Frames.ProtocolException("unsupported WebSocket version");
        }
        if (!isKey(key)) {
            plainReply(out, "400 Bad Request", "", "Malformed Sec-WebSocket-Key.\r\n");
            throw new Frames.ProtocolException("malformed WebSocket key");
        }
        String reply = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept(key) + "\r\n\r\n";
        byte[] bytes = reply.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes);
        out.flush();
        return bytes.length;
    }

    /**
     * The reply that refuses a connection before any handshake is read: a
     * 503 whose reason phrase is the reason in words, which a SuperGreenfoot
     * client shows as its error.
     */
    static byte[] refusal(String reason)
    {
        String phrase = reason.replaceAll("[\\r\\n]", " ");
        return plainReplyBytes("503 " + phrase, "", phrase + "\r\n" + NOT_A_GAME);
    }

    private static void plainReply(OutputStream out, String status, String extraHeaders, String body)
            throws IOException
    {
        out.write(plainReplyBytes(status, extraHeaders, body));
        out.flush();
    }

    private static byte[] plainReplyBytes(String status, String extraHeaders, String body)
    {
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + text.length + "\r\n"
                + extraHeaders
                + "Connection: close\r\n\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[headBytes.length + text.length];
        System.arraycopy(headBytes, 0, all, 0, headBytes.length);
        System.arraycopy(text, 0, all, headBytes.length, text.length);
        return all;
    }

    // ---- client side ----

    /**
     * Write the client's upgrade request.
     *
     * @return The bytes written.
     */
    static int askServer(OutputStream out, String host, int port, boolean secure, String path, String key)
            throws IOException
    {
        boolean defaultPort = secure ? port == 443 : port == 80;
        String hostHeader = (host.contains(":") ? "[" + host + "]" : host) + (defaultPort ? "" : ":" + port);
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "User-Agent: SuperGreenfoot\r\n\r\n";
        byte[] bytes = request.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes);
        out.flush();
        return bytes.length;
    }

    /**
     * Check the server's reply to {@link #askServer}.
     *
     * @throws IOException with a reason in words when the server did not accept.
     */
    static void checkServer(Head reply, String key) throws IOException
    {
        String[] parts = reply.firstLine.split(" ", 3);
        String status = parts.length > 1 ? parts[1] : "";
        if (!status.equals("101")) {
            String text = parts.length > 2 ? parts[2] : reply.firstLine;
            if (status.equals("503") && parts.length > 2 && !text.isEmpty()) {
                // A SuperGreenfoot server's refusal: the reason phrase says why.
                throw new Frames.ProtocolException(text);
            }
            throw new Frames.ProtocolException("the server refused the connection (" + status + " " + text + ")");
        }
        String accept = reply.header("Sec-WebSocket-Accept");
        if (accept == null || !accept.equals(accept(key))) {
            throw new Frames.ProtocolException("the server is not a WebSocket server");
        }
    }
}
