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
package greenfoot.guifx.classes;

import bluej.utility.Debug;
import greenfoot.guifx.superide.folders.ProjectSettingsFile;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which classes in the Classic class diagram are folded: shown without their
 * subclasses, which stay hidden beneath them until the class is unfolded.
 *
 * <p>Any class with subclasses can be folded, World and Actor included.  Nothing is
 * folded until the user folds it.  The folds are kept per scenario in
 * {@value ProjectSettingsFile#FILE_NAME}, one {@code class.<name>.folded=true} key
 * per folded class, beside the new IDE's {@code class.<name>.folder} keys.
 *
 * <p>Folds are kept by class name, not on the diagram's nodes, because the diagram
 * throws its nodes away and rebuilds them whenever the set of classes changes.
 */
@OnThread(Tag.FXPlatform)
public class ClassFolds
{
    /**
     * Hears when classes are folded or unfolded.
     */
    @OnThread(Tag.FXPlatform)
    public interface Listener
    {
        void foldsChanged();
    }

    private static final Pattern FOLDED_KEY = Pattern.compile("class\\.(.+)\\.folded");

    private final Set<String> folded = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();
    // The scenario folder whose settings file holds the folds (null: none, so nothing is saved):
    private File projectDir;

    /**
     * Read the folds of the scenario in the given folder, replacing any held now.
     *
     * @param projectDir  the scenario folder, or null for no scenario (nothing folded)
     */
    public void load(File projectDir)
    {
        this.projectDir = projectDir;
        folded.clear();
        if (projectDir != null)
        {
            ProjectSettingsFile settings = ProjectSettingsFile.load(projectDir);
            for (String key : settings.getKeys())
            {
                Matcher m = FOLDED_KEY.matcher(key);
                if (m.matches() && "true".equals(settings.get(key)))
                {
                    folded.add(m.group(1));
                }
            }
        }
    }

    /**
     * Is the given class folded?  A class without subclasses may still be listed
     * here (it keeps its fold for when it has subclasses again); the diagram only
     * draws a fold for a class that has some.
     */
    public boolean isFolded(String qualifiedName)
    {
        return folded.contains(qualifiedName);
    }

    /**
     * The folded classes, by qualified name.
     */
    public Set<String> getFolded()
    {
        return Collections.unmodifiableSet(folded);
    }

    /**
     * Fold or unfold a class, save the change, and tell the listeners.
     *
     * @param qualifiedName  the class
     * @param fold  true to fold it, false to unfold it
     * @param currentClasses  every class the diagram shows, so that folds of classes
     *                        that no longer exist are dropped from the file (null: keep them)
     */
    public void setFolded(String qualifiedName, boolean fold, Collection<String> currentClasses)
    {
        boolean changed = fold ? folded.add(qualifiedName) : folded.remove(qualifiedName);
        if (changed)
        {
            save(currentClasses);
            fireChanged();
        }
    }

    /**
     * Unfold every given class that is folded, save once, and tell the listeners
     * once.  Used to show a class that has just been added beneath folded ones.
     *
     * @return  true if anything was unfolded
     */
    public boolean unfoldAll(Collection<String> qualifiedNames, Collection<String> currentClasses)
    {
        if (folded.removeAll(qualifiedNames))
        {
            save(currentClasses);
            fireChanged();
            return true;
        }
        return false;
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(Listener listener)
    {
        listeners.remove(listener);
    }

    private void fireChanged()
    {
        for (Listener listener : new ArrayList<>(listeners))
        {
            listener.foldsChanged();
        }
    }

    /**
     * Write the folds to the settings file.  The file is read afresh first, so that
     * everything else in it (the new IDE's folders and window settings) is kept as
     * the new IDE last wrote it.
     */
    private void save(Collection<String> currentClasses)
    {
        if (projectDir == null)
        {
            return;
        }
        if (currentClasses != null)
        {
            folded.retainAll(currentClasses);
        }
        ProjectSettingsFile settings = ProjectSettingsFile.load(projectDir);
        for (String key : settings.getKeys())
        {
            if (FOLDED_KEY.matcher(key).matches())
            {
                settings.put(key, null);
            }
        }
        for (String name : folded)
        {
            settings.put("class." + name + ".folded", "true");
        }
        try
        {
            settings.save();
        }
        catch (IOException e)
        {
            Debug.reportError("Could not save " + settings.getFile(), e);
        }
    }

    /**
     * The class the diagram shows in place of the given one: the given class itself
     * if nothing above it is folded, otherwise the highest folded class above it
     * (everything beneath that one is hidden).
     *
     * @param roots  the top-level classes of one part of the diagram
     * @param qualifiedName  the class to look for
     * @return  the class shown in its place, or null if the class is not beneath these roots
     */
    public String shownInPlaceOf(List<GClassNode> roots, String qualifiedName)
    {
        List<GClassNode> path = pathTo(roots, qualifiedName);
        if (path == null)
        {
            return null;
        }
        // Every entry but the last is above the class; the first folded one hides it:
        for (int i = 0; i < path.size() - 1; i++)
        {
            String name = path.get(i).getQualifiedName();
            if (folded.contains(name))
            {
                return name;
            }
        }
        return qualifiedName;
    }

    /**
     * The chain of classes from a root down to the given class, both included.
     *
     * @return  the chain, or null if the class is not beneath these roots
     */
    public static List<GClassNode> pathTo(List<GClassNode> roots, String qualifiedName)
    {
        for (GClassNode root : roots)
        {
            List<GClassNode> path = new ArrayList<>();
            if (findPath(root, qualifiedName, path))
            {
                return path;
            }
        }
        return null;
    }

    private static boolean findPath(GClassNode node, String qualifiedName, List<GClassNode> path)
    {
        path.add(node);
        if (node.getQualifiedName().equals(qualifiedName))
        {
            return true;
        }
        for (GClassNode sub : node.getSubClasses())
        {
            if (findPath(sub, qualifiedName, path))
            {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }

    /**
     * How many classes are beneath the given one: its subclasses, theirs, and so on.
     */
    public static int countBeneath(GClassNode node)
    {
        int count = 0;
        for (GClassNode sub : node.getSubClasses())
        {
            count += 1 + countBeneath(sub);
        }
        return count;
    }
}
