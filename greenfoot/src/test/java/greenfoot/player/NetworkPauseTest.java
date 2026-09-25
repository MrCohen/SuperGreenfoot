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
package greenfoot.player;

import greenfoot.NetClient;
import greenfoot.NetEvent;
import greenfoot.NetServer;
import greenfoot.Network;
import greenfoot.World;
import greenfoot.sound.SoundMixer;
import junit.framework.TestCase;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import java.util.function.BooleanSupplier;

/**
 * A host that pauses keeps its players: the world's {@code stopped()} and
 * {@code started()} (stock Greenfoot's pause and resume hooks) can tell
 * them, and messages that arrive meanwhile are kept, then discarded, never
 * a reason to drop anyone. Runs a real headless session, so the pause is
 * the real one.
 */
public class NetworkPauseTest extends TestCase
{
    private static final int WAIT_MS = 8000;

    /** A host world: starts a server, and tells the players when it pauses and resumes. */
    public static class HostWorld extends World
    {
        public static volatile NetServer server;

        public HostWorld()
        {
            super(20, 20, 1);
            server = Network.startServer(0, 8);
        }

        private int counted;
        private int lastIndex = -1;
        private boolean ordered = true;

        @Override
        public void act()
        {
            NetEvent e = server.poll();
            while (e != null) {
                if (e.isMessage()) {
                    if (e.getText().startsWith("m")) {
                        // Counted rather than echoed: 2,000 echoes at once would flood the client's own inbox.
                        int index = Integer.parseInt(e.getText().substring(1));
                        ordered &= index > lastIndex;
                        lastIndex = index;
                        counted++;
                    }
                    else if (e.getText().equals("count?")) {
                        server.send(e.getConnectionId(), "count " + counted + (ordered ? " ordered" : " disordered"));
                    }
                    else {
                        server.send(e.getConnectionId(), "echo " + e.getText());
                    }
                }
                e = server.poll();
            }
        }

        @Override
        public void stopped()
        {
            server.broadcast("PAUSED");
        }

        @Override
        public void started()
        {
            server.broadcast("RESUMED");
        }
    }

    @Override
    protected void setUp() throws Exception
    {
        // The engine's default actor image is loaded once, by whichever delegate is installed when
        // Actor first loads. Load it through the test delegate (as the IDE would) before a headless
        // session installs the player delegate, whose logo resource is not on the test classpath.
        greenfoot.util.GreenfootUtil.initialise(new greenfoot.TestUtilDelegate());
        Class.forName("greenfoot.Actor", true, getClass().getClassLoader());
    }

    private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timed out waiting for " + what);
            }
            Thread.sleep(5);
        }
    }

    private static NetEvent next(NetClient c) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            NetEvent e = c.poll();
            if (e != null) {
                return e;
            }
            Thread.sleep(2);
        }
        fail("no event from the client");
        return null;
    }

    public void testPauseTellsPlayersAndKeepsThem() throws Exception
    {
        SoundMixer.installForTesting(false);
        File saveDir = Files.createTempDirectory("sgf-netpause").toFile();
        Properties props = new Properties();
        props.setProperty("main.class", HostWorld.class.getName());
        props.setProperty("project.name", "Pause test");
        props.setProperty("simulation.speed", "60");
        PlayerSession session = PlayerSession.create(getClass().getClassLoader(), props, saveDir, null);
        session.setRenderingEnabled(false);
        HostWorld.server = null;
        session.startRunning();
        waitFor("the host world to start its server", () -> HostWorld.server != null && session.isRunning());
        NetServer server = HostWorld.server;
        try {
            NetClient c = Network.connect("localhost:" + server.getPort());
            assertEquals(NetEvent.CONNECTED, next(c).getType());
            c.send("hello");
            assertEquals("echo hello", next(c).getText());

            // The pause button: stopped() runs, and its broadcast goes out although nothing is acting.
            session.pause();
            assertEquals("PAUSED", next(c).getText());
            waitFor("the session to pause", () -> !session.isRunning());

            // Players who keep sending are not dropped: the first ones wait, the rest go.
            for (int i = 0; i < 3000; i++) {
                c.send("m" + i);
            }
            waitFor("the messages to arrive", () -> server.getBytesReceived() >= c.getBytesSent());
            assertTrue("still connected through the pause", c.isConnected());
            assertEquals(1, server.getConnectionCount());

            // Resume: started() runs, then the kept messages are read in order and the rest were discarded.
            session.run();
            assertEquals("RESUMED", next(c).getText());
            c.send("count?");
            assertEquals("count 2000 ordered", next(c).getText());
            c.send("after");
            assertEquals("echo after", next(c).getText());
            c.close();
        }
        finally {
            session.shutdown();
            Network.closeAll();
        }
    }
}
