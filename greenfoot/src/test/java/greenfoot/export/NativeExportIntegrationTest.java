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

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A real native export, run the way the Application tab runs it: jpackage
 * builds the app beside the scenario, in the scenario's parent folder (the
 * export's default destination), and then again over the first app.
 *
 * In 0.2.0 this deleted the scenario on Windows and Linux, where the app
 * folder had the scenario's own name. Each jpackage run takes up to half a
 * minute, so the test runs only when asked:
 * {@code ./gradlew :greenfoot:test -PjpackageTests=true}, as CI does on Linux
 * and Windows.
 */
public class NativeExportIntegrationTest extends TestCase
{
    private static final String SOURCE = "public class MyWorld {}\n";

    private static final NativePackager.Listener QUIET = new NativePackager.Listener() {
        public void progress(String m) { }
        public void output(String l) { }
    };

    private static boolean enabled()
    {
        return Boolean.getBoolean("supergreenfoot.test.jpackage") && NativePackager.findJpackage() != null;
    }

    public void testExportBesideTheScenarioKeepsItAndReplacesTheEarlierApp() throws Exception
    {
        if (!enabled()) {
            return;
        }
        File parent = Files.createTempDirectory("sgf-export").toFile();
        File scenario = new File(parent, "Demo");
        assertTrue(scenario.mkdirs());
        File source = new File(scenario, "MyWorld.java");
        Files.writeString(source.toPath(), SOURCE);

        NativePackager.Options o = new NativePackager.Options();
        o.jar = runnableJar(parent);
        o.destDir = parent;
        o.appName = scenario.getName();
        o.kind = NativePackager.Kind.APP_IMAGE;
        o.runtimeImage = NativePackager.findRuntimeImage();

        NativePackager.Result first = NativePackager.run(o, QUIET);
        assertTrue(first.message, first.success);
        assertEquals("the scenario's source survives the export", SOURCE, Files.readString(source.toPath()));
        assertFalse("the app is not the scenario's folder", first.artifact.equals(scenario));
        assertTrue(first.artifact + " looks like an app jpackage built", NativePackager.looksLikeAppImage(first.artifact));

        File marker = new File(first.artifact, "from-the-first-export.txt");
        assertTrue(marker.createNewFile());
        NativePackager.Result second = NativePackager.run(o, QUIET);
        assertTrue(second.message, second.success);
        assertEquals("the scenario's source survives a second export", SOURCE, Files.readString(source.toPath()));
        assertEquals(first.artifact, second.artifact);
        assertFalse("the earlier app was replaced", marker.exists());
    }

    /** A jar whose main class prints a line, standing in for the exported game. */
    private static File runnableJar(File dir) throws Exception
    {
        File src = new File(dir, "Hello.java");
        Files.writeString(src.toPath(), "public class Hello { public static void main(String[] a) { System.out.println(\"hello\"); } }\n");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertEquals(0, javac.run(null, null, null, "-d", dir.getPath(), src.getPath()));

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "Hello");
        File jar = new File(dir, "Demo.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), manifest)) {
            out.putNextEntry(new JarEntry("Hello.class"));
            out.write(Files.readAllBytes(new File(dir, "Hello.class").toPath()));
            out.closeEntry();
        }
        return jar;
    }
}
