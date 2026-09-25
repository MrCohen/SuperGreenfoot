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

import bluej.Config;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.Properties;

/**
 * SuperGreenfoot: the classes of Greenfoot's public API, whose simple names a
 * scenario should not reuse.
 *
 * <p>A scenario class in the default package with one of these names wins over
 * the built-in one (Java prefers the package's own class to {@code import
 * greenfoot.*}), so everything in the scenario that says {@code SuperWindow}
 * means the scenario's class. That is how MrCohenLibrary's own SuperWindow keeps
 * working, and it is also how a student's class called {@code Color} silently
 * hides {@code greenfoot.Color}. So the IDE never offers such a name (new, and
 * duplicated classes are refused), and a class that arrives with one anyway, by
 * import or by copying a file in, is shown with a badge that explains it.</p>
 *
 * <p>The list is the classes in the user Javadoc ({@code userJavadoc} in
 * {@code greenfoot/build.gradle}); {@code BuiltInClassesTest} fails if the two
 * drift apart, so a new API class is reserved as soon as it is documented.</p>
 */
public final class BuiltInClasses
{
    /** The simple names of the public API classes in package greenfoot. */
    @OnThread(Tag.Any)
    public static final List<String> API_CLASSES = List.of(
            "Actor", "Color", "Font", "Greenfoot", "GreenfootImage", "GreenfootSound",
            "MouseInfo", "NetClient", "NetEvent", "NetServer", "Network", "Save",
            "ScaleMode", "SoundCategory", "Sounds", "SuperWindow", "UserInfo", "World",
            "ZSortAnchor");

    private BuiltInClasses()
    {
    }

    /**
     * Whether a scenario class with this (simple) name would hide a built-in class.
     */
    @OnThread(Tag.Any)
    public static boolean isReserved(String simpleName)
    {
        return API_CLASSES.contains(simpleName);
    }

    /**
     * The message for a class name the IDE will not create.
     */
    @OnThread(Tag.Any)
    public static String reservedNameMessage(String simpleName)
    {
        Properties p = new Properties();
        p.put("CLASSNAME", simpleName);
        return Config.getString("newclass.dialog.err.builtInName",
                simpleName + " is the name of a class built into Greenfoot. Please choose another name.",
                p, false);
    }

    /**
     * The explanation shown over the badge of a class that overrides a built-in one.
     */
    @OnThread(Tag.Any)
    public static String overrideMessage(String simpleName)
    {
        Properties p = new Properties();
        p.put("CLASSNAME", simpleName);
        return Config.getString("classes.overridesBuiltIn",
                "This class overrides Greenfoot's built-in greenfoot." + simpleName + ". Because it has the same "
                + "name, everything in this scenario that says " + simpleName + " means this class, not "
                + "Greenfoot's, including classes that extend " + simpleName + ".",
                p, false);
    }

    /**
     * A small badge to show beside a class that overrides a built-in one, explaining
     * itself when the mouse rests on it.  Styled by {@code .gf-override-badge} in
     * both IDEs' stylesheets.
     */
    @OnThread(Tag.FXPlatform)
    public static Node makeOverrideBadge(String simpleName)
    {
        Label badge = new Label("!");
        badge.getStyleClass().add("gf-override-badge");
        Tooltip tip = new Tooltip(overrideMessage(simpleName));
        tip.setWrapText(true);
        tip.setMaxWidth(340);
        tip.setShowDelay(Duration.millis(200));
        Tooltip.install(badge, tip);
        badge.setAccessibleText(overrideMessage(simpleName));
        return badge;
    }
}
