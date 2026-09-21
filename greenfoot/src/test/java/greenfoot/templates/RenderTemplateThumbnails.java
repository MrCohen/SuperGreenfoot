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

import greenfoot.GreenfootImage;
import greenfoot.TestUtilDelegate;
import greenfoot.core.Simulation;
import greenfoot.util.GreenfootUtil;

import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Dev tool behind {@code ./gradlew :greenfoot:renderTemplateThumbnails}: draws each
 * scene in src/test/templates/TemplateThumbnails.java and saves it as the matching
 * template's {@code <Name>-thumb.png}, beside its source in greenfoot/common.
 */
public class RenderTemplateThumbnails
{
    public static void main(String[] args) throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());
        Simulation.initialize();   // scenes use worlds, to place actors as the game would
        Class<?> scenes = TemplateCompiler.compile("TemplateThumbnails").loadClass("TemplateThumbnails");
        int written = 0;
        for (Method scene : scenes.getDeclaredMethods())
        {
            if (!Modifier.isPublic(scene.getModifiers()) || !Modifier.isStatic(scene.getModifiers())
                    || scene.getReturnType() != GreenfootImage.class || scene.getParameterCount() != 0)
            {
                continue;
            }
            String className = Character.toUpperCase(scene.getName().charAt(0)) + scene.getName().substring(1);
            File source = TemplateCompiler.templateSources().stream()
                    .filter(f -> f.getName().equals(className + ".java"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No template " + className + ".java for scene " + scene.getName()));
            File thumbnail = new File(source.getParentFile(), className + "-thumb.png");
            GreenfootImage image = (GreenfootImage) scene.invoke(null);
            ImageIO.write(image.getAwtImage(), "png", thumbnail);
            System.out.println("Wrote " + thumbnail);
            written++;
        }
        if (written == 0)
        {
            throw new IllegalStateException("No thumbnail scenes found");
        }
    }
}
