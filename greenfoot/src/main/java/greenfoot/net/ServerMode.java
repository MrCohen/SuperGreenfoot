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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * What a dedicated server run knows that a windowed run does not: that it is
 * one, its settings, and the commands typed at its console. Set up by the
 * player's {@code --server} mode; read through {@code greenfoot.Network}.
 */
@OnThread(Tag.Any)
public final class ServerMode
{
    private static volatile boolean dedicated;
    private static volatile Properties settings = new Properties();
    private static final ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
    private static volatile boolean consoleStarted;

    private ServerMode()
    {
    }

    /** Declare this run a dedicated server with these settings. */
    public static void enable(Properties serverSettings)
    {
        settings = serverSettings == null ? new Properties() : serverSettings;
        dedicated = true;
    }

    public static boolean isDedicated()
    {
        return dedicated;
    }

    public static Properties getSettings()
    {
        return settings;
    }

    /**
     * Start reading lines from standard input in the background. Each line
     * becomes one command for {@link #pollCommand()}. Safe to call twice.
     */
    public static synchronized void startConsole()
    {
        if (consoleStarted) {
            return;
        }
        consoleStarted = true;
        Thread t = new Thread(ServerMode::readConsole, "SuperGreenfoot-Net-console");
        t.setDaemon(true);
        t.start();
    }

    private static void readConsole()
    {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    commands.add(line);
                }
            }
        }
        catch (IOException e) {
            // no console: nothing more will arrive
        }
    }

    /** Put a command in the queue as if it had been typed (Ctrl-C does this with "stop"). */
    public static void pushCommand(String command)
    {
        commands.add(command);
    }

    /** The next command typed at the console, or null when there is none. */
    public static String pollCommand()
    {
        return commands.poll();
    }
}
