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
package greenfoot.templates;

import greenfoot.Actor;
import greenfoot.WorldCreator;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles the classes offered by Edit &gt; Import Class ({@code greenfoot/common})
 * against the Greenfoot API, as a user's scenario would, together with scenario-style
 * helper classes from {@code src/test/templates} that exercise them. The templates are
 * in the default package and are not on any classpath, so this is the only way tests
 * and the thumbnail renderer can use them.
 *
 * <p>Paths are relative to the greenfoot module folder, which is the working folder of
 * Gradle's test and JavaExec tasks.
 */
public class TemplateCompiler
{
    /** The importable templates. */
    public static final File COMMON_DIR = new File("common");
    /** Default-package helper classes that use the templates. */
    public static final File HELPERS_DIR = new File("src/test/templates");

    /** Every template source file, in all category folders. */
    public static List<File> templateSources() throws IOException
    {
        if (!COMMON_DIR.isDirectory())
        {
            throw new IOException("No " + COMMON_DIR.getAbsolutePath()
                    + " (run from the greenfoot module folder)");
        }
        try (Stream<Path> files = Files.walk(COMMON_DIR.toPath()))
        {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Compile all templates plus the named helper classes into a new temporary folder,
     * and return a class loader for them whose parent is this class's loader (so they
     * share the Greenfoot classes with the caller).
     *
     * @throws AssertionError listing the compiler's errors if anything fails to compile
     */
    public static ClassLoader compile(String... helperClassNames) throws IOException
    {
        List<File> sources = new ArrayList<>(templateSources());
        for (String helper : helperClassNames)
        {
            sources.add(new File(HELPERS_DIR, helper + ".java"));
        }
        File out = Files.createTempDirectory("sgf-templates").toFile();
        out.deleteOnExit();

        String classpath = String.join(File.pathSeparator,
                System.getProperty("java.class.path"),
                codeSource(Actor.class),
                codeSource(WorldCreator.class));

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = javac.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8))
        {
            List<String> options = List.of("-d", out.getPath(), "-classpath", classpath,
                    "-proc:none", "-encoding", "UTF-8", "-nowarn");
            boolean ok = javac.getTask(null, files, diagnostics, options, null,
                    files.getJavaFileObjectsFromFiles(sources)).call();
            if (!ok)
            {
                StringBuilder errors = new StringBuilder("Import Class templates failed to compile:");
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics())
                {
                    if (d.getKind() == Diagnostic.Kind.ERROR)
                    {
                        errors.append("\n  ").append(d.getSource() == null ? "?" : d.getSource().getName())
                                .append(':').append(d.getLineNumber()).append(": ").append(d.getMessage(null));
                    }
                }
                throw new AssertionError(errors.toString());
            }
        }
        return new URLClassLoader(new URL[] { out.toURI().toURL() }, TemplateCompiler.class.getClassLoader());
    }

    private static String codeSource(Class<?> c)
    {
        try
        {
            return new File(c.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Cannot locate the classes of " + c.getName(), e);
        }
    }
}
