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

/**
 * Where a {@link Link} reports what happened: its owner's event queue. Only
 * the link's reader thread calls these, and for one link they are called in
 * order: {@link #connected} once, then {@link #message} for each message,
 * then {@link #disconnected} once. Implementations must not block and must
 * not call scenario code.
 */
@OnThread(Tag.Any)
public interface Inbox
{
    /** The connection is up. */
    void connected(int id);

    /**
     * A message has arrived.
     *
     * @return False when the inbox will not take it; the link then drops the
     *         connection as flooding and never reports the message.
     */
    boolean message(int id, String text);

    /** The connection is over, with the reason in words. */
    void disconnected(int id, String reason);
}
