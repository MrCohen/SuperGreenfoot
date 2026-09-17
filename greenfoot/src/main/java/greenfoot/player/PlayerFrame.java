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

import greenfoot.core.Simulation;
import greenfoot.core.WorldHandler;
import greenfoot.event.SimulationListener;
import greenfoot.gui.input.KeyboardManager;
import greenfoot.gui.input.mouse.MousePollingManager;
import greenfoot.ScaleMode;
import greenfoot.platforms.DisplayDelegate;
import greenfoot.util.GreenfootUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

/**
 * The standalone player's Swing window: the world, a control bar (act /
 * run-pause / reset / speed / full screen) and a full-screen mode with a
 * floating, draggable control bar, mirroring the IDE's full-screen view.
 *
 * <p>Pure JDK: no JavaFX, so an exported game runs from a single jar.
 */
@OnThread(Tag.Swing)
public class PlayerFrame implements DisplayDelegate
{
    private static final long ESC_HOLD_MS = 2000;

    private final PlayerSession session;
    private final JFrame frame;
    private final WorldPanel worldPanel = new WorldPanel();
    private final ControlBar controlBar;
    private final JLayeredPane layers = new JLayeredPane();
    private final JPanel windowedRoot = new JPanel(new BorderLayout());

    private volatile boolean fullScreen = false;
    private volatile boolean controlsVisible = true;
    private volatile boolean controlsLocked = false;
    private volatile boolean pixelPerfect = false;
    /** Enlargement of the windowed world view (Greenfoot.setWindowScale). */
    private volatile double windowScale = 1.0;
    private volatile java.awt.Rectangle screenBoundsCache = new java.awt.Rectangle();
    private long escDownSince = -1;
    private BufferedImage currentFrame;

    public PlayerFrame(PlayerSession session)
    {
        this.session = session;
        frame = new JFrame(session.getTitle());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e)
            {
                session.shutdown();
                frame.dispose();
                System.exit(0);
            }
        });

        updateScreenBounds();
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e)
            {
                updateScreenBounds();
            }
        });

        controlBar = new ControlBar();
        controlBar.setLocked(session.isLocked());
        controlsVisible = !session.isControlsHidden();

        windowedRoot.add(worldPanel, BorderLayout.CENTER);
        windowedRoot.add(controlBar, BorderLayout.SOUTH);
        frame.setContentPane(windowedRoot);
        controlBar.setVisible(controlsVisible);

        // Frames: poll the delegate at up to 120 Hz on the EDT
        new Timer(8, e -> pullFrame()).start();

        session.setAsker(this::askDialog);
        installInput(worldPanel);
        GreenfootUtil.setDisplayDelegate(this);

        Simulation.getInstance().addSimulationListener(new SimulationListener() {
            @Override
            @OnThread(Tag.Simulation)
            public void simulationChangedSync(SyncEvent e)
            {
                if (e == SyncEvent.STARTED) {
                    SwingUtilities.invokeLater(() -> controlBar.setRunning(true));
                }
            }

            @Override
            @OnThread(Tag.Any)
            public void simulationChangedAsync(AsyncEvent e)
            {
                if (e == AsyncEvent.STOPPED || e == AsyncEvent.DISABLED) {
                    SwingUtilities.invokeLater(() -> controlBar.setRunning(false));
                }
                else if (e == AsyncEvent.CHANGED_SPEED) {
                    SwingUtilities.invokeLater(() -> controlBar.setSpeed(Simulation.getInstance().getSpeed()));
                }
            }
        });
    }

    /** Show the window sized to the world. */
    public void show()
    {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        worldPanel.requestFocusInWindow();
    }

    public JFrame getFrame()
    {
        return frame;
    }

    // ---- frames ----

    private void pullFrame()
    {
        BufferedImage img = session.getDelegate().takeFrame();
        if (img != null) {
            BufferedImage old = currentFrame;
            currentFrame = img;
            if (old != null) {
                session.getDelegate().recycle(old);
            }
            if (worldPanel.setWorldSize(img.getWidth(), img.getHeight()) && !fullScreen) {
                frame.pack();
            }
            worldPanel.repaint();
        }
    }

    // ---- DisplayDelegate (scenario API) ----

    @Override
    @OnThread(Tag.Any)
    public void setFullScreen(boolean on)
    {
        SwingUtilities.invokeLater(() -> setFullScreenOnEdt(on));
    }

    @Override
    @OnThread(Tag.Any)
    public boolean isFullScreen()
    {
        return fullScreen;
    }

    @Override
    @OnThread(Tag.Any)
    public void setControlsVisible(boolean visible)
    {
        SwingUtilities.invokeLater(() -> setControlsVisibleOnEdt(visible));
    }

    @Override
    @OnThread(Tag.Any)
    public boolean isControlsVisible()
    {
        return controlsVisible;
    }

    @Override
    @OnThread(Tag.Any)
    public void setControlsLocked(boolean locked)
    {
        controlsLocked = locked;
        if (locked) {
            setControlsVisible(false);
        }
    }

    @Override
    @OnThread(Tag.Any)
    public boolean isControlsLocked()
    {
        return controlsLocked;
    }

    @Override
    @OnThread(Tag.Any)
    public boolean isStandalone()
    {
        return true;
    }

    @Override
    @OnThread(Tag.Any)
    public boolean isFullScreenSupported()
    {
        return true;
    }

    @Override
    @OnThread(Tag.Any)
    public void setScaleMode(ScaleMode mode)
    {
        boolean pp = mode == ScaleMode.PIXEL_PERFECT;
        pixelPerfect = pp;
        SwingUtilities.invokeLater(() -> {
            controlBar.setPixelPerfect(pp);
            worldPanel.repaint();
        });
    }

    @Override
    @OnThread(Tag.Any)
    public ScaleMode getScaleMode()
    {
        return pixelPerfect ? ScaleMode.PIXEL_PERFECT : ScaleMode.SMOOTH;
    }

    @Override
    @OnThread(Tag.Any)
    public double getDisplayScale()
    {
        return worldPanel.getShownScale();
    }

    @Override
    @OnThread(Tag.Any)
    public int getScreenWidth()
    {
        return screenBounds().width;
    }

    @Override
    @OnThread(Tag.Any)
    public int getScreenHeight()
    {
        return screenBounds().height;
    }

    @Override
    @OnThread(Tag.Any)
    public void setWindowScale(double scale)
    {
        double s = Math.max(0.25, Math.min(8.0, scale));
        windowScale = s;
        SwingUtilities.invokeLater(() -> {
            worldPanel.updatePreferredSize();
            if (!fullScreen) {
                frame.pack();
            }
            worldPanel.repaint();
        });
    }

    @Override
    @OnThread(Tag.Any)
    public double getWindowScale()
    {
        return windowScale;
    }

    /** Logical bounds of the screen the window is on, cached on the Swing thread for any-thread readers. */
    @OnThread(Tag.Any)
    private java.awt.Rectangle screenBounds()
    {
        return screenBoundsCache;
    }

    /** Refresh the cached screen bounds (AWT reports scaled pixels on HiDPI screens). */
    private void updateScreenBounds()
    {
        java.awt.GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        if (gc == null) {
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        }
        screenBoundsCache = gc.getBounds();
    }

    // ---- full screen ----

    private void setFullScreenOnEdt(boolean on)
    {
        if (on == fullScreen) {
            return;
        }
        fullScreen = on;
        GraphicsDevice device = frame.getGraphicsConfiguration() != null
                ? frame.getGraphicsConfiguration().getDevice()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        frame.dispose();
        if (on) {
            layers.removeAll();
            layers.setLayout(null);
            layers.setBackground(Color.BLACK);
            layers.setOpaque(true);
            layers.add(worldPanel, JLayeredPane.DEFAULT_LAYER);
            layers.add(controlBar, JLayeredPane.PALETTE_LAYER);
            worldPanel.setFillParent(true);
            frame.setUndecorated(true);
            frame.setContentPane(layers);
            frame.setResizable(false);
            layers.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e)
                {
                    layoutFullScreen();
                }
            });
            device.setFullScreenWindow(frame);
            if (device.getFullScreenWindow() != frame) {
                // Exclusive mode unavailable: fall back to a screen-sized undecorated window
                frame.setBounds(device.getDefaultConfiguration().getBounds());
                frame.setVisible(true);
            }
            layoutFullScreen();
        }
        else {
            device.setFullScreenWindow(null);
            worldPanel.setFillParent(false);
            windowedRoot.removeAll();
            windowedRoot.add(worldPanel, BorderLayout.CENTER);
            windowedRoot.add(controlBar, BorderLayout.SOUTH);
            controlBar.setBounds(0, 0, 0, 0);
            frame.setUndecorated(false);
            frame.setResizable(true);
            frame.setContentPane(windowedRoot);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
        controlBar.setFullScreenMode(on);
        controlBar.setVisible(controlsVisible);
        worldPanel.requestFocusInWindow();
        updateScreenBounds();
    }

    private void layoutFullScreen()
    {
        int w = layers.getWidth();
        int h = layers.getHeight();
        worldPanel.setBounds(0, 0, w, h);
        Dimension pref = controlBar.getPreferredSize();
        if (controlBar.getX() == 0 && controlBar.getY() == 0) {
            controlBar.setBounds((w - pref.width) / 2, h - pref.height - 24, pref.width, pref.height);
        }
        else {
            controlBar.setSize(pref);
        }
        layers.repaint();
    }

    private void setControlsVisibleOnEdt(boolean visible)
    {
        controlsVisible = visible;
        controlBar.setVisible(visible);
        if (!fullScreen) {
            frame.pack();
        }
        else {
            layers.repaint();
        }
    }

    // ---- input ----

    private void installInput(JComponent target)
    {
        target.setFocusable(true);
        target.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (handleControlKeys(e)) {
                    return;
                }
                keyboard().keyPressed(AwtKeyNames.toGreenfootName(e));
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    escDownSince = -1;
                }
                keyboard().keyReleased(AwtKeyNames.toGreenfootName(e));
            }

            @Override
            public void keyTyped(KeyEvent e)
            {
                String name = AwtKeyNames.typedName(e);
                if (!name.isEmpty()) {
                    keyboard().keyTyped(name);
                }
            }
        });
        target.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                WorldHandler.getInstance().worldFocusChanged(true);
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e)
            {
                WorldHandler.getInstance().worldFocusChanged(false);
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                Point p = worldPanel.toWorld(e.getPoint());
                mouseManager().mouseClicked(p.x, p.y, button(e), e.getClickCount());
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                target.requestFocusInWindow();
                Point p = worldPanel.toWorld(e.getPoint());
                mouseManager().mousePressed(p.x, p.y, button(e));
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                Point p = worldPanel.toWorld(e.getPoint());
                mouseManager().mouseReleased(p.x, p.y, button(e));
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                Point p = worldPanel.toWorld(e.getPoint());
                mouseManager().mouseDragged(p.x, p.y, button(e));
            }

            @Override
            public void mouseMoved(MouseEvent e)
            {
                Point p = worldPanel.toWorld(e.getPoint());
                mouseManager().mouseMoved(p.x, p.y);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                mouseManager().mouseExited();
            }
        };
        target.addMouseListener(mouse);
        target.addMouseMotionListener(mouse);
    }

    /** Escape shows/hides controls (hold 2 s to force + unlock); Shortcut+Shift+F toggles full screen. */
    private boolean handleControlKeys(KeyEvent e)
    {
        int shortcut = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        if (e.getKeyCode() == KeyEvent.VK_F && (e.getModifiersEx() & shortcut) != 0 && e.isShiftDown()) {
            setFullScreen(!fullScreen);
            return true;
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            long now = System.currentTimeMillis();
            if (escDownSince < 0) {
                escDownSince = now;
                if (!controlsLocked) {
                    setControlsVisibleOnEdt(!controlsVisible);
                }
            }
            else if (now - escDownSince >= ESC_HOLD_MS) {
                controlsLocked = false;
                controlBar.setLocked(false);
                setControlsVisibleOnEdt(true);
                escDownSince = Long.MAX_VALUE / 2;
            }
            // Escape is also delivered to the scenario
        }
        return false;
    }

    @OnThread(Tag.Any)
    private static int button(MouseEvent e)
    {
        if (SwingUtilities.isLeftMouseButton(e)) {
            // Ctrl-click on Mac acts as right button, as in the IDE
            if (System.getProperty("os.name", "").toLowerCase().contains("mac") && e.isControlDown()) {
                return 3;
            }
            return 1;
        }
        if (SwingUtilities.isMiddleMouseButton(e)) {
            return 2;
        }
        if (SwingUtilities.isRightMouseButton(e)) {
            return 3;
        }
        return 0;
    }

    @OnThread(Tag.Any)
    private static KeyboardManager keyboard()
    {
        return WorldHandler.getInstance().getKeyboardManager();
    }

    @OnThread(Tag.Any)
    private static MousePollingManager mouseManager()
    {
        return WorldHandler.getInstance().getMouseManager();
    }

    @OnThread(Tag.Any)
    private String askDialog(String prompt)
    {
        final String[] result = new String[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                result[0] = JOptionPane.showInputDialog(frame, prompt, session.getTitle(), JOptionPane.QUESTION_MESSAGE);
            });
        }
        catch (InterruptedException | InvocationTargetException e) {
            return "";
        }
        return result[0] == null ? "" : result[0];
    }

    // ---- components ----

    /** Paints the current world frame, scaled to fit when filling the parent. */
    @OnThread(Tag.Swing)
    private class WorldPanel extends JComponent
    {
        private int worldW = 1, worldH = 1;
        private boolean fillParent = false;
        private volatile double scale = 1;
        private int offX, offY;

        WorldPanel()
        {
            setBackground(Color.BLACK);
            setOpaque(true);
            setPreferredSize(new Dimension(600, 400));
        }

        boolean setWorldSize(int w, int h)
        {
            if (w != worldW || h != worldH) {
                worldW = w;
                worldH = h;
                updatePreferredSize();
                return true;
            }
            return false;
        }

        void updatePreferredSize()
        {
            setPreferredSize(new Dimension((int) Math.round(worldW * windowScale), (int) Math.round(worldH * windowScale)));
        }

        @OnThread(Tag.Any)
        double getShownScale()
        {
            return scale;
        }

        void setFillParent(boolean fill)
        {
            fillParent = fill;
        }

        Point toWorld(Point p)
        {
            return new Point((int) ((p.x - offX) / scale), (int) ((p.y - offY) / scale));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            BufferedImage img = currentFrame;
            if (img == null) {
                return;
            }
            if (fillParent) {
                double sx = getWidth() / (double) img.getWidth();
                double sy = getHeight() / (double) img.getHeight();
                double s = Math.min(sx, sy);
                if (pixelPerfect) {
                    s = Math.max(1, Math.floor(s));
                }
                scale = s;
                int dw = (int) Math.round(img.getWidth() * s);
                int dh = (int) Math.round(img.getHeight() * s);
                offX = (getWidth() - dw) / 2;
                offY = (getHeight() - dh) / 2;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        pixelPerfect ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                     : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, offX, offY, dw, dh, null);
            }
            else if (windowScale != 1.0) {
                scale = windowScale;
                int dw = (int) Math.round(img.getWidth() * windowScale);
                int dh = (int) Math.round(img.getHeight() * windowScale);
                offX = (getWidth() - dw) / 2;
                offY = (getHeight() - dh) / 2;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        pixelPerfect ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                     : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, offX, offY, dw, dh, null);
            }
            else {
                scale = 1;
                offX = (getWidth() - img.getWidth()) / 2;
                offY = (getHeight() - img.getHeight()) / 2;
                g2.drawImage(img, offX, offY, null);
            }
        }
    }

    /** Act / Run-Pause / Reset / speed, plus full-screen extras. Draggable in full screen. */
    @OnThread(Tag.Swing)
    private class ControlBar extends JPanel
    {
        private final JButton actButton = new JButton("Act");
        private final JButton runButton = new JButton("Run");
        private final JButton resetButton = new JButton("Reset");
        private final JSlider speed = new JSlider(0, Simulation.MAX_SIMULATION_SPEED, 50);
        private final JButton fullScreenButton = new JButton("Full Screen");
        private final JCheckBox pixelBox = new JCheckBox("Pixel-perfect");
        private final JButton hideButton = new JButton("Hide");
        private final JCheckBox lockBox = new JCheckBox("Lock");
        private final JLabel hint = new JLabel("Esc shows/hides controls");
        private boolean settingSpeed = false;
        private Point dragStart;

        ControlBar()
        {
            super(new FlowLayout(FlowLayout.CENTER, 8, 4));
            for (JComponent c : new JComponent[] {actButton, runButton, resetButton, speed, fullScreenButton, pixelBox, hideButton, lockBox}) {
                c.setFocusable(false);
            }
            actButton.addActionListener(e -> session.act());
            runButton.addActionListener(e -> session.runPause());
            resetButton.addActionListener(e -> session.reset());
            speed.setPreferredSize(new Dimension(120, speed.getPreferredSize().height));
            speed.addChangeListener(e -> {
                if (!settingSpeed) {
                    session.setSpeed(speed.getValue());
                }
            });
            fullScreenButton.addActionListener(e -> PlayerFrame.this.setFullScreen(!fullScreen));
            pixelBox.addActionListener(e -> {
                pixelPerfect = pixelBox.isSelected();
                worldPanel.repaint();
            });
            hideButton.addActionListener(e -> setControlsVisibleOnEdt(false));
            lockBox.addActionListener(e -> {
                controlsLocked = lockBox.isSelected();
                if (controlsLocked) {
                    setControlsVisibleOnEdt(false);
                }
            });
            hint.setForeground(Color.GRAY);
            add(actButton);
            add(runButton);
            add(resetButton);
            add(new JLabel("Speed:"));
            add(speed);
            add(fullScreenButton);
            setFullScreenMode(false);

            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    dragStart = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if (fullScreen && dragStart != null) {
                        Point loc = getLocation();
                        setLocation(loc.x + e.getX() - dragStart.x, loc.y + e.getY() - dragStart.y);
                    }
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        void setFullScreenMode(boolean on)
        {
            remove(pixelBox);
            remove(hideButton);
            remove(lockBox);
            remove(hint);
            fullScreenButton.setText(on ? "Exit Full Screen" : "Full Screen");
            if (on) {
                add(pixelBox);
                add(hideButton);
                add(lockBox);
                add(hint);
                setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 80), 1, true));
                setBackground(new Color(30, 30, 30, 220));
                setOpaque(true);
            }
            else {
                setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                setBackground(null);
                setOpaque(true);
            }
            revalidate();
            setSize(getPreferredSize());
        }

        void setPixelPerfect(boolean on)
        {
            pixelBox.setSelected(on);
        }

        void setRunning(boolean running)
        {
            runButton.setText(running ? "Pause" : "Run");
            actButton.setEnabled(!running);
        }

        void setSpeed(int value)
        {
            settingSpeed = true;
            speed.setValue(value);
            settingSpeed = false;
        }

        void setLocked(boolean locked)
        {
            actButton.setVisible(!locked);
            speed.setVisible(!locked);
            for (java.awt.Component c : getComponents()) {
                if (c instanceof JLabel && "Speed:".equals(((JLabel) c).getText())) {
                    c.setVisible(!locked);
                }
            }
            lockBox.setSelected(controlsLocked);
        }
    }
}
