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
package bluej.pkgmgr;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The per-user layout store keeps window geometry per scenario folder,
 * writes only when something changed, and reads back what it wrote.
 */
public class ProjectLayoutStoreTest
{
    @Test
    public void valuesAreKeyedByScenarioAndSurviveAReload() throws Exception
    {
        File prefs = Files.createTempDirectory("prefs").toFile();
        File storeFile = new File(prefs, ProjectLayoutStore.FILE_NAME);
        File scenarioA = Files.createTempDirectory("scenarioA").toFile();
        File scenarioB = Files.createTempDirectory("scenarioB").toFile();

        ProjectLayoutStore store = new ProjectLayoutStore(storeFile);
        assertNull(store.get(scenarioA, "width"));
        store.flush();
        assertFalse("nothing to write yet", storeFile.exists());

        store.put(scenarioA, "width", "1333");
        store.put(scenarioA, "xPosition", "1806");
        store.put(scenarioB, "width", "800");
        store.flush();
        assertTrue(storeFile.isFile());

        ProjectLayoutStore again = new ProjectLayoutStore(storeFile);
        assertEquals("1333", again.get(scenarioA, "width"));
        assertEquals("1806", again.get(scenarioA, "xPosition"));
        assertEquals("800", again.get(scenarioB, "width"));
        assertNull(again.get(scenarioB, "xPosition"));

        long stamp = 1_000_000_000_000L;
        assertTrue(storeFile.setLastModified(stamp));
        again.put(scenarioA, "width", "1333");
        again.flush();
        assertEquals("an unchanged value does not rewrite the file", stamp, storeFile.lastModified());

        again.put(scenarioA, "width", null);
        again.flush();
        assertNull(new ProjectLayoutStore(storeFile).get(scenarioA, "width"));
    }

    @Test
    public void keyUsesTheCanonicalPath() throws Exception
    {
        File dir = Files.createTempDirectory("scenario").toFile();
        File viaDot = new File(dir, ".");
        assertEquals(ProjectLayoutStore.keyFor(dir, "width"), ProjectLayoutStore.keyFor(viaDot, "width"));
        assertTrue(ProjectLayoutStore.keyFor(dir, "width").endsWith("|width"));
    }

    @Test
    public void twoProcessesKeepEachOthersEntries() throws Exception
    {
        File storeFile = new File(Files.createTempDirectory("prefs").toFile(), ProjectLayoutStore.FILE_NAME);
        File scenarioA = Files.createTempDirectory("scenarioA").toFile();
        File scenarioB = Files.createTempDirectory("scenarioB").toFile();

        ProjectLayoutStore first = new ProjectLayoutStore(storeFile);
        ProjectLayoutStore second = new ProjectLayoutStore(storeFile);
        assertNull(first.get(scenarioA, "width"));
        assertNull(second.get(scenarioB, "width"));

        first.put(scenarioA, "width", "1000");
        first.put(scenarioA, "height", "700");
        second.put(scenarioB, "width", "800");
        second.put(scenarioA, "height", "650");
        first.flush();
        second.flush();

        ProjectLayoutStore fresh = new ProjectLayoutStore(storeFile);
        assertEquals("the first process's entry survives the second's flush", "1000", fresh.get(scenarioA, "width"));
        assertEquals("800", fresh.get(scenarioB, "width"));
        assertEquals("the last writer wins for the same key", "650", fresh.get(scenarioA, "height"));

        // A removal is a change like any other, and is merged the same way
        second.put(scenarioA, "width", "1100");
        fresh.put(scenarioB, "width", null);
        second.flush();
        fresh.flush();
        assertEquals("1100", new ProjectLayoutStore(storeFile).get(scenarioA, "width"));
        assertNull(new ProjectLayoutStore(storeFile).get(scenarioB, "width"));
        assertEquals("650", new ProjectLayoutStore(storeFile).get(scenarioA, "height"));
    }

    @Test
    public void goneScenariosAreDroppedAndTheOldestBeyondTheCap() throws Exception
    {
        File storeFile = new File(Files.createTempDirectory("prefs").toFile(), ProjectLayoutStore.FILE_NAME);
        File live = Files.createTempDirectory("live").toFile();
        File gone = Files.createTempDirectory("gone").toFile();
        String livePath = live.getCanonicalPath();
        String gonePath = gone.getCanonicalPath();
        assertTrue(gone.delete());
        // As an earlier build wrote it: no stamps
        Properties old = new Properties();
        old.setProperty(livePath + "|width", "900");
        old.setProperty(gonePath + "|width", "700");
        try (OutputStream out = new FileOutputStream(storeFile))
        {
            old.store(out, null);
        }

        ProjectLayoutStore store = new ProjectLayoutStore(storeFile);
        assertEquals("an entry without a stamp is still read", "900", store.getRaw(livePath, "width"));
        assertNull("a scenario whose folder is gone is dropped", store.getRaw(gonePath, "width"));
        store.put(live, "height", "600");
        store.flush();
        assertFalse(new String(Files.readAllBytes(storeFile.toPath()), StandardCharsets.ISO_8859_1).contains(gonePath));

        // Only the most recently written scenarios are kept
        Properties many = new Properties();
        for (int i = 0; i < 5; i++)
        {
            many.setProperty("/s" + i + "|width", "10" + i);
            many.setProperty("/s" + i + "|" + ProjectLayoutStore.STAMP_KEY, Long.toString(1000 + i));
        }
        many.setProperty("/unstamped|width", "1");
        try (OutputStream out = new FileOutputStream(storeFile))
        {
            many.store(out, null);
        }
        ProjectLayoutStore capped = new ProjectLayoutStore(storeFile, f -> true, 3);
        assertEquals("104", capped.getRaw("/s4", "width"));
        assertEquals("103", capped.getRaw("/s3", "width"));
        assertEquals("102", capped.getRaw("/s2", "width"));
        assertNull(capped.getRaw("/s1", "width"));
        assertNull(capped.getRaw("/unstamped", "width"));
        capped.putRaw("/s0", "width", "999");
        assertNotNull("writing a scenario stamps it", capped.getRaw("/s0", ProjectLayoutStore.STAMP_KEY));
        capped.flush();
        ProjectLayoutStore reread = new ProjectLayoutStore(storeFile, f -> true, 3);
        assertEquals("the newly written scenario is kept", "999", reread.getRaw("/s0", "width"));
        assertNull("and the oldest of the rest goes", reread.getRaw("/s2", "width"));
    }

    @Test
    public void aCorruptOrTruncatedStoreLoadsEmptyAndIsReplaced() throws Exception
    {
        File storeFile = new File(Files.createTempDirectory("prefs").toFile(), ProjectLayoutStore.FILE_NAME);
        File scenario = Files.createTempDirectory("scenario").toFile();
        // Cut off in the middle of a unicode escape, as a crash mid-write would leave it
        Files.write(storeFile.toPath(), ("#x\n" + scenario.getCanonicalPath() + "|width=12\\u00").getBytes(StandardCharsets.ISO_8859_1));

        ProjectLayoutStore store = new ProjectLayoutStore(storeFile);
        assertNull(store.get(scenario, "width"));
        store.put(scenario, "width", "640");
        store.flush();
        assertEquals("640", new ProjectLayoutStore(storeFile).get(scenario, "width"));
    }

    @Test
    public void windowsPathsRoundTrip() throws Exception
    {
        File storeFile = new File(Files.createTempDirectory("prefs").toFile(), ProjectLayoutStore.FILE_NAME);
        String path = "C:\\Users\\Zo\u00EB M\u00FCller\\Dropbox\\Greenfoot Scenarios\\Tenth Realm #2";
        ProjectLayoutStore store = new ProjectLayoutStore(storeFile, f -> true, ProjectLayoutStore.MAX_SCENARIOS);
        store.putRaw(path, "editor.fx.0.x", "12");
        store.putRaw(path, "width", "1024");
        store.flush();

        ProjectLayoutStore again = new ProjectLayoutStore(storeFile, f -> true, ProjectLayoutStore.MAX_SCENARIOS);
        assertEquals("12", again.getRaw(path, "editor.fx.0.x"));
        assertEquals("1024", again.getRaw(path, "width"));
        // A plain java.util.Properties (an earlier build, a person's tool) reads the same key
        Properties plain = new Properties();
        try (InputStream in = new FileInputStream(storeFile))
        {
            plain.load(in);
        }
        assertEquals("1024", plain.getProperty(path + "|width"));
    }

    @Test
    public void writesLfOnlyAndLeavesNoTemporaryFiles() throws Exception
    {
        File prefs = Files.createTempDirectory("prefs").toFile();
        File storeFile = new File(prefs, ProjectLayoutStore.FILE_NAME);
        ProjectLayoutStore store = new ProjectLayoutStore(storeFile);
        store.put(Files.createTempDirectory("scenario").toFile(), "width", "800");
        store.flush();
        assertFalse(new String(Files.readAllBytes(storeFile.toPath()), StandardCharsets.ISO_8859_1).contains("\r"));
        assertEquals(1, prefs.list().length);
    }

    @Test
    public void badNumbersReadAsMissing()
    {
        assertEquals(Double.valueOf(1806), ProjectLayoutStore.parseNumber("1806"));
        assertEquals(Double.valueOf(12.5), ProjectLayoutStore.parseNumber(" 12.5 "));
        assertNull(ProjectLayoutStore.parseNumber(null));
        assertNull(ProjectLayoutStore.parseNumber(""));
        assertNull(ProjectLayoutStore.parseNumber("12px"));
        assertNull(ProjectLayoutStore.parseNumber("NaN"));
        assertNull(ProjectLayoutStore.parseNumber("Infinity"));
    }
}
