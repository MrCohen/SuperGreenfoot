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

import greenfoot.GreenfootImage;
import greenfoot.UserInfo;
import greenfoot.UserInfoVisitor;
import greenfoot.platforms.GreenfootUtilDelegate;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resource, storage and identity services for the standalone player.
 * Resources come from the scenario class loader (the exported jar, or a
 * scenario directory when run from the command line). UserInfo is stored in a
 * small text file in the save directory, so the Greenfoot 3.9 UserInfo API keeps
 * working in exported games without any server.
 */
@OnThread(Tag.Any)
public class GreenfootUtilDelegatePlayer implements GreenfootUtilDelegate
{
    private static final String USERINFO_FILE = "userinfo.txt";

    private final ClassLoader loader;
    private final File saveDir;
    private final String userName;

    /**
     * @param loader  Class loader holding the scenario's classes and resources.
     * @param saveDir Directory for Save and UserInfo files (created on demand), or null.
     * @param userName Name for UserInfo; null uses the OS login name.
     */
    public GreenfootUtilDelegatePlayer(ClassLoader loader, File saveDir, String userName)
    {
        this.loader = loader;
        this.saveDir = saveDir;
        this.userName = userName != null && !userName.isEmpty() ? userName
                : System.getProperty("user.name", "Player");
    }

    @Override
    public URL getResource(String path)
    {
        URL res = loader.getResource(path);
        if (res == null && path.indexOf('\\') != -1) {
            res = loader.getResource(path.replace('\\', '/'));
        }
        return res;
    }

    @Override
    public String getGreenfootLogoPath()
    {
        URL logo = getClass().getClassLoader().getResource("greenfoot.png");
        return logo == null ? null : logo.toString();
    }

    @Override
    public Iterable<String> getSoundFiles()
    {
        // The mixer decodes on demand; nothing to preload.
        return Collections.emptyList();
    }

    @Override
    public File getSaveDirectory()
    {
        return saveDir;
    }

    // ---- UserInfo (local file) ----

    @Override
    public boolean isStorageSupported()
    {
        return saveDir != null;
    }

    @Override
    public String getUserName()
    {
        return userName;
    }

    @Override
    @OnThread(Tag.Simulation)
    public UserInfo getCurrentUserInfo()
    {
        for (UserInfo u : readAll()) {
            if (u.getUserName().equals(userName)) {
                return u;
            }
        }
        return UserInfoVisitor.allocate(userName, -1, userName);
    }

    @Override
    @OnThread(Tag.Simulation)
    public boolean storeCurrentUserInfo(UserInfo data)
    {
        if (saveDir == null) {
            return false;
        }
        List<UserInfo> all = readAll();
        all.removeIf(u -> u.getUserName().equals(data.getUserName()));
        all.add(data);
        return writeAll(all);
    }

    @Override
    @OnThread(Tag.Simulation)
    public List<UserInfo> getTopUserInfo(int limit)
    {
        List<UserInfo> all = readAll();
        all.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        if (limit > 0 && all.size() > limit) {
            all = new ArrayList<UserInfo>(all.subList(0, limit));
        }
        return all;
    }

    @Override
    @OnThread(Tag.Simulation)
    public List<UserInfo> getNearbyUserInfo(int maxAmount)
    {
        List<UserInfo> all = getTopUserInfo(0);
        int me = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getUserName().equals(userName)) {
                me = i;
            }
        }
        if (me < 0) {
            return new ArrayList<UserInfo>(all.subList(0, Math.min(all.size(), maxAmount)));
        }
        int from = Math.max(0, me - maxAmount / 2);
        int to = Math.min(all.size(), from + maxAmount);
        return new ArrayList<UserInfo>(all.subList(from, to));
    }

    @Override
    @OnThread(Tag.Simulation)
    public GreenfootImage getUserImage(String userName)
    {
        return null;
    }

    @OnThread(Tag.Simulation)
    private List<UserInfo> readAll()
    {
        List<UserInfo> list = new ArrayList<UserInfo>();
        if (saveDir == null) {
            return list;
        }
        File f = new File(saveDir, USERINFO_FILE);
        if (!f.isFile()) {
            return list;
        }
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                // name \t score \t int0..int9 \t s0..s4 (strings escaped)
                String[] p = line.split("\t", -1);
                if (p.length < 2 + UserInfo.NUM_INTS + UserInfo.NUM_STRINGS) {
                    continue;
                }
                UserInfo u = UserInfoVisitor.allocate(unescape(p[0]), -1, userName);
                u.setScore(parse(p[1]));
                for (int i = 0; i < UserInfo.NUM_INTS; i++) {
                    u.setInt(i, parse(p[2 + i]));
                }
                for (int i = 0; i < UserInfo.NUM_STRINGS; i++) {
                    u.setString(i, unescape(p[2 + UserInfo.NUM_INTS + i]));
                }
                list.add(u);
            }
        }
        catch (IOException e) {
            System.err.println("Could not read " + USERINFO_FILE + ": " + e.getMessage());
        }
        // Assign ranks by score
        list.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        List<UserInfo> ranked = new ArrayList<UserInfo>();
        for (int i = 0; i < list.size(); i++) {
            UserInfo u = list.get(i);
            UserInfo r = UserInfoVisitor.allocate(u.getUserName(), i + 1, userName);
            r.setScore(u.getScore());
            for (int k = 0; k < UserInfo.NUM_INTS; k++) {
                r.setInt(k, u.getInt(k));
            }
            for (int k = 0; k < UserInfo.NUM_STRINGS; k++) {
                r.setString(k, u.getString(k));
            }
            ranked.add(r);
        }
        return ranked;
    }

    @OnThread(Tag.Simulation)
    private boolean writeAll(List<UserInfo> all)
    {
        try {
            if (!saveDir.isDirectory() && !saveDir.mkdirs()) {
                return false;
            }
            List<String> lines = new ArrayList<String>();
            for (UserInfo u : all) {
                StringBuilder sb = new StringBuilder(escape(u.getUserName())).append('\t').append(u.getScore());
                for (int i = 0; i < UserInfo.NUM_INTS; i++) {
                    sb.append('\t').append(u.getInt(i));
                }
                for (int i = 0; i < UserInfo.NUM_STRINGS; i++) {
                    sb.append('\t').append(escape(u.getString(i)));
                }
                lines.add(sb.toString());
            }
            Files.write(new File(saveDir, USERINFO_FILE).toPath(), lines, StandardCharsets.UTF_8);
            return true;
        }
        catch (IOException e) {
            System.err.println("Could not write " + USERINFO_FILE + ": " + e.getMessage());
            return false;
        }
    }

    private static int parse(String s)
    {
        try {
            return Integer.parseInt(s.trim());
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escape(String s)
    {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String s)
    {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 't') out.append('\t');
                else if (n == 'n') out.append('\n');
                else out.append(n);
            }
            else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
