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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * WebSocket frames (RFC 6455 section 5): reading one from a stream and
 * writing one to a stream. Nothing here knows about threads or sockets.
 *
 * <p>A frame is a header of 2 to 14 bytes followed by the payload. The header
 * carries a FIN bit, an opcode, a MASK bit, the payload length (7, 7+16 or
 * 7+64 bits) and, when masked, a 4-byte masking key that is XORed over the
 * payload. Frames from a browser (a client) are always masked; frames from a
 * server never are. Control frames (close, ping, pong) fit in 125 bytes and
 * are never fragmented.</p>
 */
@OnThread(Tag.Any)
final class Frames
{
    static final int OP_CONTINUATION = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_CLOSE = 0x8;
    static final int OP_PING = 0x9;
    static final int OP_PONG = 0xA;

    /** The largest single frame we accept, as a guard against absurd lengths. */
    static final int MAX_FRAME = 4 * 1024 * 1024;

    /** One decoded frame. */
    static final class Frame
    {
        final boolean fin;
        final int opcode;
        final byte[] payload;
        /** Header plus payload bytes, as read from the wire. */
        final int wireBytes;

        Frame(boolean fin, int opcode, byte[] payload, int wireBytes)
        {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
            this.wireBytes = wireBytes;
        }

        boolean isControl()
        {
            return (opcode & 0x8) != 0;
        }
    }

    private Frames()
    {
    }

    /**
     * Read one frame. Blocks until a whole frame has arrived.
     *
     * @param in            The stream.
     * @param expectMasked  True on a server (clients must mask), false on a client
     *                      (servers must not). A frame that breaks the rule is a
     *                      protocol error.
     * @param maxPayload    Refuse frames whose payload is longer than this.
     * @throws IOException  On end of stream, or a malformed frame (a
     *                      {@link ProtocolException} with a reason in words).
     */
    static Frame read(DataInputStream in, boolean expectMasked, int maxPayload) throws IOException
    {
        int b0 = in.readUnsignedByte();
        int b1 = in.readUnsignedByte();
        int wire = 2;
        boolean fin = (b0 & 0x80) != 0;
        if ((b0 & 0x70) != 0) {
            throw new ProtocolException("reserved bits set in a frame");
        }
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7F;
        if (length == 126) {
            length = in.readUnsignedShort();
            wire += 2;
        }
        else if (length == 127) {
            length = in.readLong();
            wire += 8;
            if (length < 0) {
                throw new ProtocolException("frame length out of range");
            }
        }
        boolean control = (opcode & 0x8) != 0;
        if (control && (!fin || length > 125)) {
            throw new ProtocolException("malformed control frame");
        }
        if (masked != expectMasked) {
            throw new ProtocolException(expectMasked ? "unmasked frame from a client" : "masked frame from a server");
        }
        if (length > maxPayload || length > MAX_FRAME) {
            throw new ProtocolException("message too long (" + length + " bytes, the limit is " + maxPayload + ")");
        }
        byte[] key = null;
        if (masked) {
            key = new byte[4];
            in.readFully(key);
            wire += 4;
        }
        byte[] payload = new byte[(int) length];
        in.readFully(payload);
        wire += (int) length;
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= key[i & 3];
            }
        }
        return new Frame(fin, opcode, payload, wire);
    }

    /**
     * Encode one frame (FIN always set: we never fragment what we send).
     *
     * @param opcode   The opcode.
     * @param payload  The payload (may be empty).
     * @param mask     True on a client (frames to a server must be masked).
     * @param random   Source of masking keys when masking.
     * @return The bytes to put on the wire.
     */
    static byte[] encode(int opcode, byte[] payload, boolean mask, Random random)
    {
        int len = payload.length;
        int headerLen = 2 + (len >= 65536 ? 8 : len >= 126 ? 2 : 0) + (mask ? 4 : 0);
        byte[] out = new byte[headerLen + len];
        out[0] = (byte) (0x80 | opcode);
        int p = 2;
        if (len < 126) {
            out[1] = (byte) len;
        }
        else if (len < 65536) {
            out[1] = (byte) 126;
            out[2] = (byte) (len >> 8);
            out[3] = (byte) len;
            p = 4;
        }
        else {
            out[1] = (byte) 127;
            long l = len;
            for (int i = 0; i < 8; i++) {
                out[2 + i] = (byte) (l >> (56 - 8 * i));
            }
            p = 10;
        }
        if (mask) {
            out[1] |= (byte) 0x80;
            byte[] key = new byte[4];
            random.nextBytes(key);
            System.arraycopy(key, 0, out, p, 4);
            p += 4;
            for (int i = 0; i < len; i++) {
                out[p + i] = (byte) (payload[i] ^ key[i & 3]);
            }
        }
        else {
            System.arraycopy(payload, 0, out, p, len);
        }
        return out;
    }

    /** Write an encoded frame and flush it. */
    static void write(OutputStream out, byte[] encoded) throws IOException
    {
        out.write(encoded);
        out.flush();
    }

    /** The payload of a close frame: a 2-byte status code and a UTF-8 reason. */
    static byte[] closePayload(int code, String reason)
    {
        byte[] text = reason == null ? new byte[0] : reason.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (text.length > 123) {
            // A control frame holds 125 bytes; keep the reason whole at a character boundary.
            String cut = new String(text, 0, 120, java.nio.charset.StandardCharsets.UTF_8);
            text = cut.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        byte[] payload = new byte[2 + text.length];
        payload[0] = (byte) (code >> 8);
        payload[1] = (byte) code;
        System.arraycopy(text, 0, payload, 2, text.length);
        return payload;
    }

    /** The reason text of a close frame's payload ("" when there is none). */
    static String closeReason(byte[] payload)
    {
        if (payload == null || payload.length <= 2) {
            return "";
        }
        return new String(payload, 2, payload.length - 2, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** The status code of a close frame's payload (1005 when there is none). */
    static int closeCode(byte[] payload)
    {
        if (payload == null || payload.length < 2) {
            return 1005;
        }
        return ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
    }

    /** A frame or handshake that breaks the protocol; the message is fit to show. */
    static final class ProtocolException extends IOException
    {
        ProtocolException(String message)
        {
            super(message);
        }
    }
}
