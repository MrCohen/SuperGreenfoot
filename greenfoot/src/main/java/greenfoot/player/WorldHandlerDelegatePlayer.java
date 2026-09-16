/*
 This file is part of the SuperGreenfoot program, a derivative of Greenfoot.
 Copyright (C) 2005-2023 Poul Henriksen and Michael Kolling
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

import greenfoot.Actor;
import greenfoot.World;
import greenfoot.WorldVisitor;
import greenfoot.core.Simulation;
import greenfoot.core.WorldHandler;
import greenfoot.gui.WorldRenderer;
import greenfoot.platforms.WorldHandlerDelegate;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * World handler delegate for the standalone player: renders the world on the
 * simulation thread into a recycled BufferedImage and hands it to the display
 * (a Swing panel, or nothing when headless). Derived from Greenfoot's
 * WorldHandlerDelegateStandAlone, minus JavaFX.
 */
@OnThread(Tag.Simulation)
public class WorldHandlerDelegatePlayer implements WorldHandlerDelegate
{
    /** Receives finished frames on the simulation thread; must be quick and thread-safe. */
    @OnThread(Tag.Any)
    public interface FrameSink
    {
        void frameReady();
    }

    @OnThread(Tag.Any)
    private final PlayerSession session;
    @OnThread(Tag.Any)
    private final FrameSink sink;
    private final WorldRenderer worldRenderer = new WorldRenderer();
    private long lastFramePaint;
    /** Minimum nanoseconds between frames (default caps at ~120 fps). */
    @OnThread(Tag.Any)
    private volatile long minFrameInterval = 8_000_000L;

    @OnThread(Tag.Any)
    private final AtomicReference<BufferedImage> pendingImage = new AtomicReference<>(null);
    @OnThread(Tag.Any)
    private final ConcurrentLinkedQueue<BufferedImage> oldImages = new ConcurrentLinkedQueue<>();

    @OnThread(Tag.Any)
    public WorldHandlerDelegatePlayer(PlayerSession session, FrameSink sink)
    {
        this.session = session;
        this.sink = sink;
    }

    /**
     * Take the latest rendered frame, or null if none is new. The caller must
     * give it back with {@link #recycle} when done drawing it.
     */
    @OnThread(Tag.Any)
    public BufferedImage takeFrame()
    {
        return pendingImage.getAndSet(null);
    }

    @OnThread(Tag.Any)
    public void recycle(BufferedImage image)
    {
        if (image != null) {
            oldImages.add(image);
        }
    }

    @Override
    @OnThread(Tag.Any)
    public void setWorld(World oldWorld, World newWorld)
    {
    }

    @Override
    @OnThread(Tag.Any)
    public void instantiateNewWorld(String className, Runnable runIfError)
    {
        // Construct the world on the simulation thread, like the IDE does, so
        // that user code in the constructor runs where act() will run.
        Simulation.getInstance().runLater(() -> {
            WorldHandler.getInstance().clearWorldSet();
            World newWorld = session.instantiateWorld();
            if (newWorld == null) {
                runIfError.run();
                return;
            }
            if (!WorldHandler.getInstance().checkWorldSet()) {
                WorldHandler.getInstance().setWorld(newWorld, false);
            }
        });
    }

    @Override
    @OnThread(Tag.Any)
    public void discardWorld(World world)
    {
        BufferedImage image = pendingImage.getAndSet(null);
        if (image != null) {
            oldImages.add(image);
        }
    }

    @Override
    public void objectAddedToWorld(Actor actor)
    {
    }

    @Override
    public String ask(String prompt)
    {
        return session.ask(prompt);
    }

    @Override
    public void paint(World world, boolean forcePaint)
    {
        if (world == null) {
            return;
        }
        long now = System.nanoTime();
        if (!forcePaint && now - lastFramePaint < minFrameInterval) {
            return;
        }
        lastFramePaint = now;

        int imageWidth = WorldVisitor.getWidthInPixels(world);
        int imageHeight = WorldVisitor.getHeightInPixels(world);

        BufferedImage worldImage = oldImages.poll();
        if (worldImage == null || worldImage.getHeight() != imageHeight || worldImage.getWidth() != imageWidth) {
            worldImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        }
        worldRenderer.renderWorld(world, worldImage);
        BufferedImage oldImage = pendingImage.getAndSet(worldImage);
        if (oldImage != null) {
            oldImages.add(oldImage);
        }
        if (sink != null) {
            sink.frameReady();
        }
    }

    @Override
    public void notifyStoppedWithError()
    {
    }
}
