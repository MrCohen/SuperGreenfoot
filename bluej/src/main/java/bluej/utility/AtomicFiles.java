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
package bluej.utility;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.concurrent.ThreadLocalRandom;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * SuperGreenfoot: replace a file's contents without ever leaving it
 * truncated or half written. The bytes go to a temporary file in the same
 * folder, which is then moved over the target, atomically where the file
 * system allows it. If anything fails the target keeps its old contents.
 */
@OnThread(Tag.Any)
public final class AtomicFiles
{
    private AtomicFiles()
    {
    }

    /**
     * Write {@code bytes} to {@code target}, replacing it. The temporary file
     * is removed whatever happens; on POSIX systems the new file gets the
     * permissions the old one had.
     *
     * @throws IOException if the file could not be written; the target is then unchanged
     */
    public static void write(Path target, byte[] bytes) throws IOException
    {
        Path absolute = target.toAbsolutePath();
        Path dir = absolute.getParent();
        Path temp = dir.resolve("." + absolute.getFileName() + "."
                + Long.toHexString(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE) + ".tmp");
        try
        {
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
            {
                out.write(bytes);
            }
            copyPermissions(absolute, temp);
            try
            {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                // Some file systems (network shares, some sync folders) cannot
                // rename atomically over an existing file; a plain replace
                // still never leaves the target truncated.
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temp);
        }
    }

    private static void copyPermissions(Path from, Path to)
    {
        try
        {
            if (Files.exists(from) && Files.getFileAttributeView(from, PosixFileAttributeView.class) != null)
            {
                Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
            }
        }
        catch (IOException | UnsupportedOperationException e)
        {
            // Best effort: the file keeps the default permissions.
        }
    }
}
