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
package greenfoot.export;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;

/**
 * Checks the jpackage command line the native packager builds (no execution).
 */
public class NativePackagerTest extends TestCase
{
    private NativePackager.Options options()
    {
        NativePackager.Options o = new NativePackager.Options();
        o.jar = new File("/tmp/out/My Game.jar");
        o.destDir = new File("/tmp/out");
        o.appName = "My Game";
        o.description = "A game";
        return o;
    }

    public void testAppImageCommand()
    {
        NativePackager.Options o = options();
        List<String> cmd = NativePackager.buildJpackageCommand(new File("/jdk/bin/jpackage"), o, new File("/tmp/in"));
        assertEquals("/jdk/bin/jpackage", cmd.get(0));
        assertTrue(cmd.contains("--type"));
        assertEquals("app-image", cmd.get(cmd.indexOf("--type") + 1));
        assertEquals("My Game.jar", cmd.get(cmd.indexOf("--main-jar") + 1));
        assertEquals("greenfoot.player.PlayerMain", cmd.get(cmd.indexOf("--main-class") + 1));
        assertEquals("My Game", cmd.get(cmd.indexOf("--name") + 1));
        assertEquals(NativePackager.MODULES, cmd.get(cmd.indexOf("--add-modules") + 1));
        assertEquals("-Xmx1g", cmd.get(cmd.indexOf("--java-options") + 1));
        assertFalse(cmd.contains("--mac-sign"));
        assertFalse(cmd.contains("--icon"));
        if (NativePackager.isMac()) {
            assertEquals("org.supergreenfoot.mygame", cmd.get(cmd.indexOf("--mac-package-identifier") + 1));
        }
    }

    public void testDmgSignedCommandOnMac()
    {
        NativePackager.Options o = options();
        o.kind = NativePackager.Kind.DMG;
        o.macSigningName = "Jane Doe (ABCDE12345)";
        List<String> cmd = NativePackager.buildJpackageCommand(new File("/jdk/bin/jpackage"), o, new File("/tmp/in"));
        assertEquals("dmg", cmd.get(cmd.indexOf("--type") + 1));
        if (NativePackager.isMac()) {
            assertTrue(cmd.contains("--mac-sign"));
            assertEquals("Jane Doe (ABCDE12345)", cmd.get(cmd.indexOf("--mac-signing-key-user-name") + 1));
        }
        else {
            assertFalse(cmd.contains("--mac-sign"));
        }
    }

    public void testRuntimeImageReplacesModuleLinking()
    {
        NativePackager.Options o = options();
        o.runtimeImage = new File(System.getProperty("java.home"));   // exists
        List<String> cmd = NativePackager.buildJpackageCommand(new File("/jdk/bin/jpackage"), o, new File("/tmp/in"));
        assertTrue(cmd.contains("--runtime-image"));
        assertFalse(cmd.contains("--add-modules"));
        assertFalse(cmd.contains("--module-path"));
    }

    public void testMissingJarIsReportedNotThrown()
    {
        NativePackager.Options o = options();
        o.jar = new File("/definitely/not/here.jar");
        NativePackager.Result r = NativePackager.run(o, new NativePackager.Listener() {
            public void progress(String m) { }
            public void output(String l) { }
        });
        assertFalse(r.success);
        assertNotNull(r.message);
    }

    public void testFindJpackageReturnsFileOrNull()
    {
        File f = NativePackager.findJpackage();
        assertTrue(f == null || f.isFile());
    }
}
