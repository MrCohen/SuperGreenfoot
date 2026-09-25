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
package greenfoot.player;

import greenfoot.UserInfo;
import greenfoot.UserInfoVisitor;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Local storage must preserve the documented UserInfo API in exported games. */
public class PlayerStorageTest extends TestCase
{
    private File dir;
    private GreenfootUtilDelegatePlayer storage;

    @Override
    protected void setUp() throws Exception
    {
        dir = Files.createTempDirectory("sgf-player-storage").toFile();
        storage = new GreenfootUtilDelegatePlayer(getClass().getClassLoader(), dir, "Ada");
    }

    @Override
    protected void tearDown() throws IOException
    {
        try (Stream<Path> paths = Files.walk(dir.toPath())) {
            for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }

    public void testMyInfoIsTheSharedObjectAsInTheIde()
    {
        UserInfo user = storage.getCurrentUserInfo();
        user.setScore(5);
        assertTrue(storage.storeCurrentUserInfo(user));
        assertSame(user, storage.getCurrentUserInfo());
        assertEquals(5, storage.getCurrentUserInfo().getScore());
    }

    public void testStringsRoundTripWithLineEndingsAndEscapes()
    {
        UserInfo user = storage.getCurrentUserInfo();
        String text = "one\rtwo\nthree\tfour\\r";
        user.setString(0, text);
        assertTrue(storage.storeCurrentUserInfo(user));
        // Read as another player so UserInfoVisitor's current-user cache
        // cannot conceal a lost or damaged disk record.
        GreenfootUtilDelegatePlayer reader = new GreenfootUtilDelegatePlayer(
                getClass().getClassLoader(), storage.getSaveDirectory(), "Reader");
        List<UserInfo> all = reader.getTopUserInfo(0);
        assertEquals(1, all.size());
        assertEquals(text, all.get(0).getString(0));
    }

    private void addScores()
    {
        for (int i = 0; i < 5; i++) {
            String name = i == 0 ? "Ada" : "Player" + i;
            UserInfo user = UserInfoVisitor.allocate(name, -1, "Ada");
            user.setScore(i);
            assertTrue(storage.storeCurrentUserInfo(user));
        }
    }

    public void testUpdatingExistingUserPersistsNewValues()
    {
        UserInfo user = storage.getCurrentUserInfo();
        user.setScore(10);
        assertTrue(storage.storeCurrentUserInfo(user));
        user.setScore(20);
        user.setString(0, "updated");
        assertTrue(storage.storeCurrentUserInfo(user));
        GreenfootUtilDelegatePlayer reader = new GreenfootUtilDelegatePlayer(
                getClass().getClassLoader(), storage.getSaveDirectory(), "Reader");
        UserInfo saved = reader.getTopUserInfo(1).get(0);
        assertEquals(20, saved.getScore());
        assertEquals("updated", saved.getString(0));
    }

    public void testNearbyNonPositiveLimitReturnsAll()
    {
        addScores();
        assertEquals(5, storage.getNearbyUserInfo(0).size());
        assertEquals(5, storage.getNearbyUserInfo(-1).size());
    }

    public void testNearbyFillsRequestedCountAtBottomOfTable()
    {
        addScores();
        List<UserInfo> nearby = storage.getNearbyUserInfo(4);
        assertEquals(4, nearby.size());
        assertEquals("Ada", nearby.get(3).getUserName());
        assertEquals(2, nearby.get(0).getRank());
    }
}
