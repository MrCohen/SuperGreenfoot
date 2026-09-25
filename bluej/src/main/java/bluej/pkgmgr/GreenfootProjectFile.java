/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2014,2016,2018,2026  Michael Kolling and John Rosenberg
 
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;

import bluej.utility.AtomicFiles;
import bluej.utility.SortedProperties;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Reference to the Greenfoot project file(s). A Greenfoot project file is
 * basically just a BlueJ package with some extra information added.
 * 
 * @author Poul Henriksen
 */
@OnThread(Tag.Any)
public class GreenfootProjectFile
    implements PackageFile
{
    private static final String pkgfileName = "project.greenfoot";
    private File dir;
    private File pkgFile;

    /**
     * @see PackageFileFactory
     */
    @OnThread(Tag.Any)
    GreenfootProjectFile(File dir)
    {
        this.dir = dir;
        this.pkgFile = new File(dir, pkgfileName);
    }

    public String toString()
    {
        return dir.toString() + File.separator + pkgfileName;
    }

    public void load(Properties p)
        throws IOException
    {
        FileInputStream input = null;
        try {
            if (pkgFile.canRead()) {
                input = new FileInputStream(pkgFile);
            }
            else {
                throw new IOException("Can't read from project file: " + pkgFile);
            }
            p.load(input);
        }
        finally {
            if(input != null) {
                input.close();
            }
        }
    }

    /**
     * Save the given properties to the file.
     * 
     * @throws IOException if something goes wrong while trying to write the
     *             file.
     */
    public void save(Properties props)
        throws IOException
    {
        String header = "Greenfoot project file";
        // SuperGreenfoot: render first, and leave the file alone when it would
        // come out identical, so an unchanged scenario is not touched (its
        // modification time, Dropbox, git, a read-only folder). Always sorted
        // with '\n' line ends, so the same settings give the same bytes on
        // Windows and elsewhere and a scenario moving between them is not
        // rewritten just for that.
        ByteArrayOutputStream rendered = new ByteArrayOutputStream();
        SortedProperties sorted;
        if (props instanceof SortedProperties) {
            sorted = (SortedProperties) props;
        }
        else {
            sorted = new SortedProperties();
            sorted.putAll(props);
        }
        sorted.store(rendered, header, "\n");
        byte[] bytes = rendered.toByteArray();
        if (hasContent(pkgFile, bytes))
        {
            return;
        }

        if (!pkgFile.canWrite())
        {
            throw new IOException("Greenfoot project file not writable: " + this);
        }

        // SuperGreenfoot: write a temporary file and move it into place, so a
        // failed or interrupted save (a full disk, a crash, a sync client
        // holding the file) leaves the old file whole rather than truncated.
        try
        {
            AtomicFiles.write(pkgFile.toPath(), bytes);
        }
        catch (IOException e)
        {
            throw new IOException("Error when storing properties to Greenfoot project file: " + this, e);
        }
    }
    
    /**
     * Whether a Greenfoot package file exists in this directory.
     */
    public static boolean exists(File dir)
    {
        if (dir == null)
            return false;

        // don't try to test Windows root directories (you'll get in
        // trouble with disks that are not in drives...).
        if (dir.getPath().endsWith(":\\"))
            return false;

        if (!dir.isDirectory())
            return false;

        File packageFile = new File(dir, pkgfileName);
        return packageFile.exists();
    }
    
    /**
     * Whether this file is the name has the name of a Greenfoot project file.
     */
    public static boolean isProjectFileName(String fileName)
    {
        return fileName.endsWith(pkgfileName);
    }
    
    /**
     * Creates the Greenfoot project file if it does not already exist. 
     * 
     * @return true if it created a package file, false if it didn't create any package files.
     * @param dir The directory to create package file in.
     * @throws IOException If the package file could not be created.
     * 
     */
    /**
     * True if the file exists and holds exactly these bytes.
     */
    static boolean hasContent(File file, byte[] bytes)
    {
        try
        {
            return file.isFile() && file.length() == bytes.length
                    && Arrays.equals(Files.readAllBytes(file.toPath()), bytes);
        }
        catch (IOException e)
        {
            return false;
        }
    }

    public boolean create()
        throws IOException
    {
        File pkgFile = new File(dir, pkgfileName);

        if (pkgFile.exists()) {
            return false;
        }
        else {
            pkgFile.createNewFile();
            return true;
        }     
    }

}
