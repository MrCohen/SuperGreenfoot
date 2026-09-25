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

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * One thing that has happened on a network connection: somebody connected,
 * a message arrived, or somebody disconnected.
 *
 * <p>You get these by polling a {@link NetServer} or a {@link NetClient} from
 * your act method, in the order they happened. For each connection you will
 * see one {@code CONNECTED}, then any number of {@code MESSAGE}s in the order
 * they were sent, then one {@code DISCONNECTED}, and never anything else for
 * that connection id afterwards.</p>
 *
 * <pre>
 *   NetEvent e = server.poll();
 *   while (e != null) {
 *       if (e.getType() == NetEvent.MESSAGE) {
 *           handle(e.getConnectionId(), e.getText());
 *       }
 *       e = server.poll();
 *   }
 * </pre>
 *
 * @author SuperGreenfoot contributors
 * @since SuperGreenfoot 0.2.0
 */
@OnThread(Tag.Any)
public final class NetEvent
{
    /** A connection has opened. On a server, {@link #getConnectionId()} says which. */
    public static final int CONNECTED = 0;
    /** A text message has arrived; {@link #getText()} holds it. */
    public static final int MESSAGE = 1;
    /** A connection has closed; {@link #getText()} says why, in words. */
    public static final int DISCONNECTED = 2;

    private final int type;
    private final int connectionId;
    private final String text;

    /**
     * Make an event. The engine makes these for you; scenarios only read them.
     *
     * @param type          CONNECTED, MESSAGE or DISCONNECTED
     * @param connectionId  which connection it is about (0 on a client)
     * @param text          the message, the reason for a disconnect, or ""
     */
    public NetEvent(int type, int connectionId, String text)
    {
        this.type = type;
        this.connectionId = connectionId;
        this.text = text == null ? "" : text;
    }

    /** @return CONNECTED, MESSAGE or DISCONNECTED. */
    public int getType()
    {
        return type;
    }

    /**
     * @return Which connection this is about. On a server, the number given
     *         to that connection when it opened; the same number is never used
     *         again while the server runs. On a client, always 0.
     */
    public int getConnectionId()
    {
        return connectionId;
    }

    /**
     * @return The message text for MESSAGE; for DISCONNECTED, the reason in
     *         words (the other side's stated reason if it gave one); "" for
     *         CONNECTED.
     */
    public String getText()
    {
        return text;
    }

    @Override
    public String toString()
    {
        String[] names = { "CONNECTED", "MESSAGE", "DISCONNECTED" };
        return names[type] + " " + connectionId + (text.isEmpty() ? "" : " " + text);
    }
}
