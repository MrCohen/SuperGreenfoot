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

import greenfoot.util.GreenfootUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simple persistent storage for a scenario: named values that survive between
 * runs, plus local high-score tables. There are no limits on the number of
 * values, unlike {@link UserInfo}.
 *
 * <pre>
 *   int best = Save.getInt("bestScore", 0);
 *   Save.putInt("bestScore", score);
 *   Save.putString("playerName", name);
 *   Save.submitScore("classic", score);          // uses the current player's name
 *   for (Save.ScoreEntry e : Save.getTopScores("classic", 10)) { ... }
 * </pre>
 *
 * <p>Values are written to disk shortly after each change and when the
 * scenario stops, so there is nothing to call at the end. Inside the IDE the
 * files live in the scenario's {@code saves} folder; in an exported game they
 * live in the player's application-data folder. If storage is unavailable the
 * methods still work for the current run and simply forget everything after.
 *
 * @since SuperGreenfoot 1.0
 */
@OnThread(Tag.Any)
public final class Save
{
    /** One line of a high-score table. */
    @OnThread(Tag.Any)
    public static final class ScoreEntry
    {
        private final String player;
        private final int score;
        private final long time;

        ScoreEntry(String player, int score, long time)
        {
            this.player = player;
            this.score = score;
            this.time = time;
        }

        /** @return The player's name. */
        public String getPlayer()
        {
            return player;
        }

        /** @return The score. */
        public int getScore()
        {
            return score;
        }

        /** @return When the score was recorded, in milliseconds since 1970. */
        public long getTime()
        {
            return time;
        }

        @Override
        public String toString()
        {
            return player + ": " + score;
        }
    }

    private static final String VALUES_FILE = "save.properties";
    private static final long FLUSH_DELAY_MS = 500;
    private static final int MAX_SCORES_KEPT = 1000;

    private static final Properties values = new Properties();
    private static boolean loaded = false;
    private static boolean dirty = false;
    private static File directory;
    private static Thread flusher;

    private Save()
    {
    }

    // ---- values ----

    /** Store an int under the given key. */
    public static void putInt(String key, int value)
    {
        put(key, Integer.toString(value));
    }

    /** @return The int stored under the key, or {@code defaultValue} if there is none. */
    public static int getInt(String key, int defaultValue)
    {
        String v = get(key);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Store a double under the given key. */
    public static void putDouble(String key, double value)
    {
        put(key, Double.toString(value));
    }

    /** @return The double stored under the key, or {@code defaultValue} if there is none. */
    public static double getDouble(String key, double defaultValue)
    {
        String v = get(key);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Store a boolean under the given key. */
    public static void putBoolean(String key, boolean value)
    {
        put(key, Boolean.toString(value));
    }

    /** @return The boolean stored under the key, or {@code defaultValue} if there is none. */
    public static boolean getBoolean(String key, boolean defaultValue)
    {
        String v = get(key);
        return v == null ? defaultValue : Boolean.parseBoolean(v.trim());
    }

    /** Store a string under the given key (null removes the key). */
    public static void putString(String key, String value)
    {
        put(key, value);
    }

    /** @return The string stored under the key, or {@code defaultValue} if there is none. */
    public static String getString(String key, String defaultValue)
    {
        String v = get(key);
        return v == null ? defaultValue : v;
    }

    /** @return true if a value is stored under the key. */
    public static synchronized boolean contains(String key)
    {
        ensureLoaded();
        return values.containsKey(key);
    }

    /** Remove the value stored under the key, if any. */
    public static synchronized void remove(String key)
    {
        ensureLoaded();
        if (values.remove(key) != null) {
            markDirty();
        }
    }

    /** Remove every stored value (high-score tables are kept). */
    public static synchronized void clear()
    {
        ensureLoaded();
        if (!values.isEmpty()) {
            values.clear();
            markDirty();
        }
    }

    /** @return All stored keys, sorted. */
    public static synchronized List<String> getKeys()
    {
        ensureLoaded();
        Set<String> keys = new TreeSet<String>();
        for (Object k : values.keySet()) {
            keys.add(k.toString());
        }
        return new ArrayList<String>(keys);
    }

    /**
     * Write any unsaved changes to disk now. Normally unnecessary, since
     * changes are saved automatically within half a second.
     */
    public static synchronized void flush()
    {
        ensureLoaded();
        if (!dirty || directory == null) {
            dirty = false;
            return;
        }
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return;
            }
            File f = new File(directory, VALUES_FILE);
            try (OutputStream out = Files.newOutputStream(f.toPath())) {
                values.store(out, "SuperGreenfoot saved values");
            }
            dirty = false;
        }
        catch (IOException e) {
            System.err.println("Save: could not write " + VALUES_FILE + ": " + e.getMessage());
        }
    }

    // ---- high scores ----

    /**
     * Add a score to the named table for the current player (the name from
     * {@link UserInfo#getMyInfo()} / the player's login name, or "Player").
     *
     * @param table A name for the table, e.g. "classic" or "level3".
     * @param score The score.
     */
    @OnThread(Tag.Simulation)
    public static void submitScore(String table, int score)
    {
        String name = null;
        try {
            name = GreenfootUtil.getUserName();
        }
        catch (Exception e) {
            // fall through to default
        }
        if (name == null || name.isEmpty()) {
            name = System.getProperty("user.name", "Player");
        }
        submitScore(table, name, score);
    }

    /**
     * Add a score for a named player to the named table.
     *
     * @param table A name for the table.
     * @param player The player's name.
     * @param score The score.
     */
    public static synchronized void submitScore(String table, String player, int score)
    {
        List<ScoreEntry> entries = loadScores(table);
        entries.add(new ScoreEntry(player == null ? "Player" : player, score, System.currentTimeMillis()));
        sortScores(entries);
        if (entries.size() > MAX_SCORES_KEPT) {
            entries = new ArrayList<ScoreEntry>(entries.subList(0, MAX_SCORES_KEPT));
        }
        writeScores(table, entries);
    }

    /**
     * @param table The table name.
     * @param count How many entries to return at most.
     * @return The highest scores in the table, best first (empty if none).
     */
    public static synchronized List<ScoreEntry> getTopScores(String table, int count)
    {
        List<ScoreEntry> entries = loadScores(table);
        sortScores(entries);
        if (entries.size() > count) {
            entries = new ArrayList<ScoreEntry>(entries.subList(0, Math.max(0, count)));
        }
        return Collections.unmodifiableList(entries);
    }

    /**
     * @param table The table name.
     * @param player The player's name.
     * @return The player's best score in the table, or {@code Integer.MIN_VALUE} if none.
     */
    public static synchronized int getBestScore(String table, String player)
    {
        int best = Integer.MIN_VALUE;
        for (ScoreEntry e : loadScores(table)) {
            if (e.player.equals(player) && e.score > best) {
                best = e.score;
            }
        }
        return best;
    }

    /** Delete every score in the named table. */
    public static synchronized void clearScores(String table)
    {
        writeScores(table, new ArrayList<ScoreEntry>());
    }

    // ---- internals ----

    private static synchronized String get(String key)
    {
        ensureLoaded();
        return values.getProperty(key);
    }

    private static synchronized void put(String key, String value)
    {
        if (key == null) {
            throw new NullPointerException("key");
        }
        ensureLoaded();
        if (value == null) {
            remove(key);
            return;
        }
        Object old = values.setProperty(key, value);
        if (old == null || !old.equals(value)) {
            markDirty();
        }
    }

    private static void ensureLoaded()
    {
        if (loaded) {
            return;
        }
        loaded = true;
        directory = GreenfootUtil.getSaveDirectory();
        if (directory == null) {
            return;
        }
        File f = new File(directory, VALUES_FILE);
        if (f.isFile()) {
            try (InputStream in = Files.newInputStream(f.toPath())) {
                values.load(in);
            }
            catch (IOException e) {
                System.err.println("Save: could not read " + VALUES_FILE + ": " + e.getMessage());
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(Save::flush, "SuperGreenfoot-Save-flush"));
    }

    private static void markDirty()
    {
        dirty = true;
        if (flusher == null || !flusher.isAlive()) {
            flusher = new Thread(() -> {
                try {
                    Thread.sleep(FLUSH_DELAY_MS);
                }
                catch (InterruptedException e) {
                    // fall through and flush anyway
                }
                flush();
            }, "SuperGreenfoot-Save");
            flusher.setDaemon(true);
            flusher.start();
        }
    }

    /**
     * Reset for tests: forget everything in memory so the next call reloads
     * from the (possibly changed) save directory.
     */
    static synchronized void resetForTesting()
    {
        values.clear();
        loaded = false;
        dirty = false;
        directory = null;
    }

    private static File scoresFile(String table)
    {
        String safe = table.replaceAll("[^A-Za-z0-9_.-]", "_");
        return new File(directory, "scores-" + safe + ".txt");
    }

    private static List<ScoreEntry> loadScores(String table)
    {
        ensureLoaded();
        List<ScoreEntry> list = new ArrayList<ScoreEntry>();
        if (directory == null) {
            List<ScoreEntry> mem = memoryScores(table, false);
            return mem == null ? list : new ArrayList<ScoreEntry>(mem);
        }
        File f = scoresFile(table);
        if (!f.isFile()) {
            return list;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) {
                    try {
                        list.add(new ScoreEntry(parts[2], Integer.parseInt(parts[0]), Long.parseLong(parts[1])));
                    }
                    catch (NumberFormatException e) {
                        // skip bad line
                    }
                }
            }
        }
        catch (IOException e) {
            System.err.println("Save: could not read scores: " + e.getMessage());
        }
        return list;
    }

    private static void writeScores(String table, List<ScoreEntry> entries)
    {
        ensureLoaded();
        if (directory == null) {
            memoryScores(table, true).clear();
            memoryScores(table, true).addAll(entries);
            return;
        }
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return;
            }
            List<String> lines = new ArrayList<String>();
            for (ScoreEntry e : entries) {
                lines.add(e.score + "\t" + e.time + "\t" + e.player.replace('\t', ' ').replace('\n', ' '));
            }
            Files.write(scoresFile(table).toPath(), lines, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            System.err.println("Save: could not write scores: " + e.getMessage());
        }
    }

    private static final java.util.Map<String, List<ScoreEntry>> memoryOnlyScores = new java.util.HashMap<String, List<ScoreEntry>>();

    private static List<ScoreEntry> memoryScores(String table, boolean create)
    {
        List<ScoreEntry> l = memoryOnlyScores.get(table);
        if (l == null && create) {
            l = new ArrayList<ScoreEntry>();
            memoryOnlyScores.put(table, l);
        }
        return l;
    }

    private static void sortScores(List<ScoreEntry> entries)
    {
        entries.sort((a, b) -> {
            if (b.score != a.score) {
                return Integer.compare(b.score, a.score);
            }
            return Long.compare(a.time, b.time);
        });
    }
}
