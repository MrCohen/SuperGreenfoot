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
import greenfoot.*;
import greenfoot.templates.ThumbnailSupport;

/**
 * The Import Class dialog's preview thumbnails for the templates that draw themselves,
 * made from the real classes: each public static method named after a template (with a
 * lower-case first letter) returns that template's thumbnail. Redraw them all with
 * <code>./gradlew :greenfoot:renderTemplateThumbnails</code> after changing a template
 * or a scene here.
 *
 * <p>Thumbnails are 400 x 260 and shown at most 240 wide, so the scenes draw everything
 * at twice its usual size to stay sharp on high-resolution screens.
 */
public class TemplateThumbnails
{
    public static final int WIDTH = 400;
    public static final int HEIGHT = 260;

    public static GreenfootImage textBox()
    {
        GreenfootImage scene = tiled("backgrounds/space1.jpg");

        TextBox title = new TextBox("Game Over!", new Font(true, false, 40));
        title.setBorder(4);
        draw(scene, title, WIDTH / 2, 62);

        TextBox log = new TextBox(3, new Font(24), 360);
        log.setColors(Color.WHITE, new Color(20, 24, 48), new Color(120, 140, 200));
        log.setBorder(3);
        log.addLine("You found a key.");
        log.addLine("The door creaks open...");
        log.addLine("A bee buzzes past!");
        draw(scene, log, WIDTH / 2, 190);
        return scene;
    }

    public static GreenfootImage statBar()
    {
        GreenfootImage scene = tiled("backgrounds/sand.jpg");
        World world = WorldCreator.createWorld(WIDTH, HEIGHT, 1);

        Actor wombat = new Actor() {};
        wombat.setImage(ThumbnailSupport.libraryImage("animals/wombat.png", 2));
        world.addObject(wombat, 110, 160);
        StatBar health = new StatBar(100, 70, wombat, 110, 16);
        health.setBorder(Color.BLACK, 2);
        world.addObject(health, 0, 0);

        Actor elephant = new Actor() {};
        elephant.setImage(ThumbnailSupport.libraryImage("animals/elephant.png", 2));
        world.addObject(elephant, 290, 165);
        StatBar healthAndMana = new StatBar(new int[] { 100, 100 }, new int[] { 90, 40 }, elephant, 120, 24);
        healthAndMana.setBorder(Color.BLACK, 2);
        world.addObject(healthAndMana, 0, 0);

        for (Actor actor : new Actor[] { wombat, health, elephant, healthAndMana })
        {
            draw(scene, actor, actor.getX(), actor.getY());
        }
        return scene;
    }

    public static GreenfootImage counter()
    {
        GreenfootImage scene = tiled("backgrounds/weave.jpg");

        Counter score = new Counter("Score: ", 44, Color.BLACK, new Color(241, 195, 37), new Color(10, 8, 2));
        score.setValue(1250);
        draw(scene, score, WIDTH / 2, 90);

        Counter lives = new Counter("Lives: ", 44, Color.WHITE, new Color(40, 110, 220), new Color(10, 20, 40));
        lives.setValue(3);
        draw(scene, lives, WIDTH / 2, 175);
        return scene;
    }

    /** A scene filled with a background picture from the image library, repeated. */
    private static GreenfootImage tiled(String backgroundName)
    {
        GreenfootImage tile = ThumbnailSupport.libraryImage(backgroundName, 1);
        GreenfootImage scene = new GreenfootImage(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y += tile.getHeight())
        {
            for (int x = 0; x < WIDTH; x += tile.getWidth())
            {
                scene.drawImage(tile, x, y);
            }
        }
        return scene;
    }

    /** Draw an actor's image centred on a point, as the world would. */
    private static void draw(GreenfootImage scene, Actor actor, int x, int y)
    {
        GreenfootImage image = actor.getImage();
        scene.drawImage(image, x - image.getWidth() / 2, y - image.getHeight() / 2);
    }
}
