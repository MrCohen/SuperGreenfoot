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

import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an exported application jar into a native package with the JDK's
 * {@code jpackage} tool: a self-contained app that needs no Java installed.
 * On macOS it can also sign with a Developer ID and notarize with Apple.
 *
 * <p>Pure JDK, no UI: the IDE's export dialog and the command line both use it.
 * Progress and tool output are reported through a listener so the dialog can
 * show them.
 */
@OnThread(Tag.Any)
public final class NativePackager
{
    /** Modules the SuperGreenfoot runtime needs (from jdeps on the runtime jar, plus the usual desktop extras). */
    public static final String MODULES = "java.base,java.desktop,java.logging,java.prefs,java.xml,"
            + "java.compiler,java.management,jdk.jdi,jdk.xml.dom,jdk.unsupported";

    /** What to produce. */
    public enum Kind
    {
        /** A folder with an executable and a private runtime (all platforms, no extra tools). */
        APP_IMAGE,
        /** macOS disk image. */
        DMG,
        /** Windows installer (needs WiX 3 on the machine); falls back to APP_IMAGE if jpackage fails. */
        MSI
    }

    /** Everything the packager needs to know. */
    public static final class Options
    {
        public File jar;
        public File destDir;
        public String appName;
        public String version = "1.0";
        public String vendor = "SuperGreenfoot";
        public String description;
        public File icon;
        public Kind kind = Kind.APP_IMAGE;
        /** macOS: the name part of a "Developer ID Application: <name>" identity, or null. */
        public String macSigningName;
        /** macOS: notarytool keychain profile to notarize with, or null. */
        public String notarizeProfile;
        /** Extra JVM options for the packaged app. */
        public List<String> javaOptions = new ArrayList<String>(Arrays.asList("-Xmx1g"));
    }

    /** Receives progress messages and raw tool output lines. */
    public interface Listener
    {
        void progress(String message);

        void output(String line);
    }

    /** Result of a packaging run. */
    public static final class Result
    {
        public final boolean success;
        /** The produced .app folder, .dmg, .msi or app-image folder. */
        public final File artifact;
        public final String message;

        Result(boolean success, File artifact, String message)
        {
            this.success = success;
            this.artifact = artifact;
            this.message = message;
        }
    }

    private NativePackager()
    {
    }

    /** Path of the jpackage tool in the running JDK, or null if this JDK does not have it. */
    public static File findJpackage()
    {
        return findTool("jpackage");
    }

    /** Path of a JDK bin tool, or null. */
    public static File findTool(String name)
    {
        String home = System.getProperty("java.home");
        if (home == null) {
            return null;
        }
        File bin = new File(home, "bin");
        File f = new File(bin, name);
        if (f.isFile()) {
            return f;
        }
        f = new File(bin, name + ".exe");
        return f.isFile() ? f : null;
    }

    public static boolean isMac()
    {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    public static boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * macOS: names of the installed "Developer ID Application" signing identities
     * (the part after the colon, e.g. "Jane Doe (ABCDE12345)"), from the keychain.
     * Empty on other platforms or when none are installed.
     */
    public static List<String> findMacSigningIdentities()
    {
        if (!isMac()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        try {
            Process p = new ProcessBuilder("security", "find-identity", "-v", "-p", "codesigning")
                    .redirectErrorStream(true).start();
            Pattern pat = Pattern.compile("\"Developer ID Application: ([^\"]+)\"");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("CSSMERR")) {
                        continue;   // revoked / invalid
                    }
                    Matcher m = pat.matcher(line);
                    if (m.find() && !names.contains(m.group(1))) {
                        names.add(m.group(1));
                    }
                }
            }
            p.waitFor();
        }
        catch (IOException | InterruptedException e) {
            // no identities
        }
        return names;
    }

    /**
     * Build the jpackage command line for the options (no execution). Exposed
     * for tests and for showing the user what will run.
     */
    public static List<String> buildJpackageCommand(File jpackage, Options o, File inputDir)
    {
        List<String> cmd = new ArrayList<String>();
        cmd.add(jpackage.getAbsolutePath());
        cmd.add("--type");
        switch (o.kind) {
            case DMG: cmd.add("dmg"); break;
            case MSI: cmd.add("msi"); break;
            default: cmd.add("app-image"); break;
        }
        cmd.add("--input");
        cmd.add(inputDir.getAbsolutePath());
        cmd.add("--main-jar");
        cmd.add(o.jar.getName());
        cmd.add("--main-class");
        cmd.add("greenfoot.player.PlayerMain");
        cmd.add("--name");
        cmd.add(o.appName);
        cmd.add("--app-version");
        cmd.add(o.version);
        cmd.add("--vendor");
        cmd.add(o.vendor);
        if (o.description != null && !o.description.isEmpty()) {
            cmd.add("--description");
            cmd.add(o.description);
        }
        cmd.add("--dest");
        cmd.add(o.destDir.getAbsolutePath());
        cmd.add("--add-modules");
        cmd.add(MODULES);
        for (String opt : o.javaOptions) {
            cmd.add("--java-options");
            cmd.add(opt);
        }
        if (o.icon != null && o.icon.isFile()) {
            cmd.add("--icon");
            cmd.add(o.icon.getAbsolutePath());
        }
        if (isMac()) {
            cmd.add("--mac-package-identifier");
            cmd.add("org.supergreenfoot." + o.appName.replaceAll("[^A-Za-z0-9]", "").toLowerCase());
            if (o.macSigningName != null && !o.macSigningName.isEmpty()) {
                cmd.add("--mac-sign");
                cmd.add("--mac-signing-key-user-name");
                cmd.add(o.macSigningName);
            }
        }
        if (isWindows() && o.kind == Kind.MSI) {
            cmd.add("--win-shortcut");
            cmd.add("--win-menu");
            cmd.add("--win-dir-chooser");
        }
        return cmd;
    }

    /**
     * Package the jar. Blocks until finished (seconds for an app image, minutes
     * when notarizing). Never throws; the result carries the outcome.
     */
    public static Result run(Options o, Listener listener)
    {
        File jpackage = findJpackage();
        if (jpackage == null) {
            return new Result(false, null, "This Java installation has no jpackage tool; install a full JDK 21+ to build native apps.");
        }
        if (o.jar == null || !o.jar.isFile()) {
            return new Result(false, null, "Application jar not found: " + o.jar);
        }
        try {
            // jpackage wants an input directory containing just the jar
            File inputDir = Files.createTempDirectory("sgf-jpackage").toFile();
            Files.copy(o.jar.toPath(), new File(inputDir, o.jar.getName()).toPath());
            if (!o.destDir.isDirectory() && !o.destDir.mkdirs()) {
                return new Result(false, null, "Cannot create " + o.destDir);
            }

            if (isMac() && o.macSigningName != null && !o.macSigningName.isEmpty()) {
                // Sign one scratch file first: this raises the keychain permission
                // prompt once (jpackage would otherwise hit it for every file, and a
                // long wait makes codesign's timestamp check fail) and surfaces
                // codesign's own error text if the identity cannot be used.
                listener.progress("Checking the signing identity (answer the keychain prompt with Always Allow)...");
                String problem = probeSigning(o.macSigningName, listener);
                if (problem != null) {
                    deleteQuietly(inputDir);
                    return new Result(false, null, "Cannot sign with \"Developer ID Application: " + o.macSigningName + "\": " + problem);
                }
            }

            listener.progress("Building native app with jpackage...");
            List<String> cmd = buildJpackageCommand(jpackage, o, inputDir);
            int rc = exec(cmd, listener);
            if (rc != 0 && o.kind == Kind.MSI) {
                listener.progress("Installer build failed (WiX missing?); building an app folder instead...");
                o.kind = Kind.APP_IMAGE;
                cmd = buildJpackageCommand(jpackage, o, inputDir);
                rc = exec(cmd, listener);
            }
            deleteQuietly(inputDir);
            if (rc != 0) {
                return new Result(false, null, "jpackage failed (exit " + rc + "); see the log above.");
            }
            File artifact = locateArtifact(o);
            if (artifact == null) {
                return new Result(false, null, "jpackage finished but the output was not found in " + o.destDir);
            }

            if (isMac() && o.macSigningName != null && !o.macSigningName.isEmpty() && artifact.getName().endsWith(".dmg")) {
                // jpackage signs the app inside the image but not the image itself;
                // Gatekeeper assesses the .dmg as a whole, so sign that too.
                listener.progress("Signing the disk image...");
                int sc = exec(Arrays.asList("/usr/bin/codesign", "-s", "Developer ID Application: " + o.macSigningName,
                        "--timestamp", "-f", artifact.getAbsolutePath()), listener);
                if (sc != 0) {
                    return new Result(false, artifact, "Signing the disk image failed (exit " + sc + ").");
                }
            }
            if (isMac() && o.notarizeProfile != null && !o.notarizeProfile.isEmpty()
                    && o.macSigningName != null && !o.macSigningName.isEmpty()) {
                Result n = notarize(artifact, o.notarizeProfile, listener);
                if (!n.success) {
                    return n;
                }
            }
            return new Result(true, artifact, "Native app written to " + artifact);
        }
        catch (IOException e) {
            return new Result(false, null, "Packaging failed: " + e.getMessage());
        }
    }

    /**
     * macOS: try to sign a scratch file with the identity. Returns null on
     * success, otherwise codesign's message.
     */
    public static String probeSigning(String signingName, Listener listener)
    {
        try {
            File probe = File.createTempFile("sgf-sign-probe", ".dylib");
            // Any file works for a detached-style signature probe; use the JDK's own tiny library if present
            File lib = new File(System.getProperty("java.home"), "lib/libjsig.dylib");
            if (lib.isFile()) {
                Files.copy(lib.toPath(), probe.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            StringBuilder out = new StringBuilder();
            Listener capture = new Listener() {
                public void progress(String m) { }
                public void output(String l) { out.append(l).append('\n'); if (listener != null) listener.output(l); }
            };
            int rc = exec(Arrays.asList("/usr/bin/codesign", "-s", "Developer ID Application: " + signingName,
                    "-f", "--timestamp", "--options", "runtime", probe.getAbsolutePath()), capture);
            probe.delete();
            if (rc == 0) {
                return null;
            }
            String text = out.toString().trim();
            if (text.contains("timestamps differ")) {
                return "codesign reported a clock/timestamp mismatch. This happens when the keychain prompt was left "
                        + "waiting for minutes; click Always Allow and export again.";
            }
            if (text.contains("errSecInternalComponent") || text.contains("user interaction is not allowed")) {
                return "the keychain refused access to the private key. In Keychain Access, allow /usr/bin/codesign "
                        + "(or all applications) to use the \"" + signingName + "\" private key.";
            }
            return text.isEmpty() ? "codesign exited with " + rc : text;
        }
        catch (IOException e) {
            return e.getMessage();
        }
    }

    private static File locateArtifact(Options o)
    {
        File[] candidates = o.destDir.listFiles();
        if (candidates == null) {
            return null;
        }
        String base = o.appName;
        File best = null;
        for (File f : candidates) {
            String n = f.getName();
            if (!n.startsWith(base)) {
                continue;
            }
            if (o.kind == Kind.DMG && n.endsWith(".dmg")) return f;
            if (o.kind == Kind.MSI && n.endsWith(".msi")) return f;
            if (o.kind == Kind.APP_IMAGE && (n.endsWith(".app") || f.isDirectory())) best = f;
        }
        return best;
    }

    /**
     * macOS: submit to Apple's notary service and staple the ticket. For an
     * .app folder the app is zipped for submission and the .app itself stapled.
     */
    private static Result notarize(File artifact, String profile, Listener listener) throws IOException
    {
        File submit = artifact;
        File zip = null;
        if (artifact.getName().endsWith(".app")) {
            zip = new File(artifact.getParentFile(), artifact.getName().replace(".app", "") + "-notarize.zip");
            listener.progress("Zipping app for notarization...");
            int z = exec(Arrays.asList("ditto", "-c", "-k", "--keepParent", artifact.getAbsolutePath(), zip.getAbsolutePath()), listener);
            if (z != 0) {
                return new Result(false, artifact, "Could not zip the app for notarization.");
            }
            submit = zip;
        }
        listener.progress("Submitting to Apple for notarization (this takes a few minutes)...");
        int rc = exec(Arrays.asList("xcrun", "notarytool", "submit", submit.getAbsolutePath(),
                "--keychain-profile", profile, "--wait"), listener);
        if (zip != null) {
            zip.delete();
        }
        if (rc != 0) {
            return new Result(false, artifact, "Notarization failed (exit " + rc + "). Run 'xcrun notarytool log <id> --keychain-profile "
                    + profile + "' for details.");
        }
        listener.progress("Stapling notarization ticket...");
        rc = exec(Arrays.asList("xcrun", "stapler", "staple", artifact.getAbsolutePath()), listener);
        if (rc != 0) {
            return new Result(false, artifact, "Stapling failed (exit " + rc + ").");
        }
        return new Result(true, artifact, "Signed and notarized.");
    }

    private static int exec(List<String> cmd, Listener listener) throws IOException
    {
        listener.output("$ " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                listener.output(line);
            }
        }
        try {
            return p.waitFor();
        }
        catch (InterruptedException e) {
            p.destroyForcibly();
            return -1;
        }
    }

    private static void deleteQuietly(File dir)
    {
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteQuietly(k);
            }
        }
        dir.delete();
    }
}
