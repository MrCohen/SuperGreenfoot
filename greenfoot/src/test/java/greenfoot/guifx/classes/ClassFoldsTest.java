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

import greenfoot.guifx.GreenfootStage;
import greenfoot.guifx.superide.folders.ProjectSettingsFile;
import junit.framework.TestCase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/**
 * Tests the folded superclasses of the Classic class diagram: how they are kept in
 * the scenario's settings file, and which class shows in place of a hidden one.
 */
public class ClassFoldsTest extends TestCase
{
    private static final Set<String> CLASSES = Set.of("greenfoot.Actor", "Enemy", "Boss", "FinalBoss", "Slime", "Player");

    private File dir;

    /** A class in the tree, without any display. */
    private static class Node extends GClassNode
    {
        private final String name;

        Node(String name, GClassNode... subClasses)
        {
            super(null, List.of(subClasses), new ClassDisplaySelectionManager());
            this.name = name;
        }

        @Override
        public String getQualifiedName()
        {
            return name;
        }

        @Override
        public String getDisplayName()
        {
            return name.substring(name.lastIndexOf('.') + 1);
        }

        @Override
        protected void setupClassDisplay(GreenfootStage greenfootStage, ClassDisplay display)
        {
        }
    }

    /** Actor > Enemy > Boss > FinalBoss, Actor > Enemy > Slime, Actor > Player. */
    private static List<GClassNode> actorTree()
    {
        return List.of(new Node("greenfoot.Actor",
                new Node("Enemy", new Node("Boss", new Node("FinalBoss")), new Node("Slime")),
                new Node("Player")));
    }

    @Override
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory("sgf-folds").toFile();
    }

    @Override
    protected void tearDown() throws Exception
    {
        File[] files = dir.listFiles();
        if (files != null)
        {
            for (File f : files)
            {
                f.delete();
            }
        }
        dir.delete();
    }

    private String settingsText() throws Exception
    {
        return new String(Files.readAllBytes(new File(dir, ProjectSettingsFile.FILE_NAME).toPath()), StandardCharsets.UTF_8);
    }

    public void testNothingFoldedAtFirst()
    {
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        assertTrue(folds.getFolded().isEmpty());
        assertFalse(new File(dir, ProjectSettingsFile.FILE_NAME).exists());
    }

    public void testFoldIsSavedAndReadBack() throws Exception
    {
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        folds.setFolded("Enemy", true, CLASSES);
        folds.setFolded("greenfoot.Actor", true, CLASSES);
        assertTrue(settingsText().contains("class.Enemy.folded=true\n"));
        assertTrue(settingsText().contains("class.greenfoot.Actor.folded=true\n"));

        ClassFolds again = new ClassFolds();
        again.load(dir);
        assertEquals(Set.of("Enemy", "greenfoot.Actor"), again.getFolded());
    }

    public void testUnfoldRemovesTheKey() throws Exception
    {
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        folds.setFolded("Enemy", true, CLASSES);
        folds.setFolded("Enemy", false, CLASSES);
        assertFalse(settingsText().contains("class.Enemy.folded"));
    }

    public void testTheNewIdesSettingsAreKept() throws Exception
    {
        // What the new IDE wrote there, as it wrote it:
        Files.write(new File(dir, ProjectSettingsFile.FILE_NAME).toPath(),
                ("class.Enemy.folder=Characters\nfolders.1.name=Characters\nfolders.count=1\n"
                        + "ui.window.x=40\nversion=1\n").getBytes(StandardCharsets.UTF_8));
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        folds.setFolded("Enemy", true, CLASSES);

        ProjectSettingsFile settings = ProjectSettingsFile.load(dir);
        assertEquals("Characters", settings.get("class.Enemy.folder"));
        assertEquals("Characters", settings.get("folders.1.name"));
        assertEquals("40", settings.get("ui.window.x"));
        assertEquals("true", settings.get("class.Enemy.folded"));
    }

    public void testTheFileIsReadAfreshBeforeSaving() throws Exception
    {
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        // The new IDE writes the file after the folds were loaded:
        ProjectSettingsFile other = ProjectSettingsFile.load(dir);
        other.put("ui.window.x", "99");
        other.save();

        folds.setFolded("Enemy", true, CLASSES);
        assertEquals("99", ProjectSettingsFile.load(dir).get("ui.window.x"));
    }

    public void testFoldsOfClassesThatAreGoneAreDropped()
    {
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        folds.setFolded("Gone", true, null);
        folds.setFolded("Enemy", true, CLASSES);
        assertEquals(Set.of("Enemy"), folds.getFolded());

        ClassFolds again = new ClassFolds();
        again.load(dir);
        assertEquals(Set.of("Enemy"), again.getFolded());
    }

    public void testNoScenarioMeansNothingIsWritten()
    {
        ClassFolds folds = new ClassFolds();
        folds.load(null);
        folds.setFolded("Enemy", true, CLASSES);
        assertTrue(folds.isFolded("Enemy"));
        folds.load(null);
        assertFalse(folds.isFolded("Enemy"));
    }

    public void testListenersHearOncePerChange()
    {
        ClassFolds folds = new ClassFolds();
        folds.load(dir);
        int[] heard = {0};
        folds.addListener(() -> heard[0]++);

        folds.setFolded("Enemy", true, CLASSES);
        // Folding what is already folded is not a change:
        folds.setFolded("Enemy", true, CLASSES);
        assertEquals(1, heard[0]);

        folds.setFolded("Boss", true, CLASSES);
        assertTrue(folds.unfoldAll(List.of("Enemy", "Boss", "Player"), CLASSES));
        assertEquals(3, heard[0]);
        assertFalse(folds.unfoldAll(List.of("Enemy"), CLASSES));
        assertEquals(3, heard[0]);
    }

    public void testShownInPlaceOf()
    {
        List<GClassNode> roots = actorTree();
        ClassFolds folds = new ClassFolds();
        folds.load(null);

        // Nothing folded: every class shows as itself.
        assertEquals("FinalBoss", folds.shownInPlaceOf(roots, "FinalBoss"));

        // A folded class shows itself, and stands in for everything beneath it:
        folds.setFolded("Enemy", true, null);
        assertEquals("Enemy", folds.shownInPlaceOf(roots, "Enemy"));
        assertEquals("Enemy", folds.shownInPlaceOf(roots, "FinalBoss"));
        assertEquals("Enemy", folds.shownInPlaceOf(roots, "Slime"));
        assertEquals("Player", folds.shownInPlaceOf(roots, "Player"));

        // The highest fold wins, all the way up to Actor:
        folds.setFolded("Boss", true, null);
        assertEquals("Enemy", folds.shownInPlaceOf(roots, "FinalBoss"));
        folds.setFolded("greenfoot.Actor", true, null);
        assertEquals("greenfoot.Actor", folds.shownInPlaceOf(roots, "FinalBoss"));
        assertEquals("greenfoot.Actor", folds.shownInPlaceOf(roots, "Player"));

        // A class elsewhere in the diagram is not beneath these roots:
        assertNull(folds.shownInPlaceOf(roots, "MyWorld"));
    }

    public void testPathAndCount()
    {
        List<GClassNode> roots = actorTree();
        List<GClassNode> path = ClassFolds.pathTo(roots, "FinalBoss");
        assertEquals(List.of("greenfoot.Actor", "Enemy", "Boss", "FinalBoss"),
                path.stream().map(GClassNode::getQualifiedName).toList());
        assertNull(ClassFolds.pathTo(roots, "MyWorld"));

        // Everything beneath, not just the direct subclasses:
        assertEquals(5, ClassFolds.countBeneath(roots.get(0)));
        assertEquals(3, ClassFolds.countBeneath(path.get(1)));
        assertEquals(0, ClassFolds.countBeneath(path.get(3)));
    }
}
