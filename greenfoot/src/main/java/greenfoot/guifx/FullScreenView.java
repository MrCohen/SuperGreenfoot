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
package greenfoot.guifx;

import bluej.Config;
import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.GreenfootStage.State;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * SuperGreenfoot full-screen play mode: a second window that fills the screen
 * with the world image, scaled to fit (smooth) or by a whole-number factor
 * (pixel-perfect), plus a floating control bar (act / run / pause / reset /
 * speed) that can be dragged, hidden with Escape, or locked.
 *
 * <p>Keyboard and mouse input on this window is forwarded to the scenario via
 * the owning GreenfootStage, exactly as input on the normal world view is.
 * A teacher can always recover hidden or locked controls by holding Escape
 * for two seconds.
 */
@OnThread(Tag.FXPlatform)
public class FullScreenView extends Stage
{
    /** How long Escape must be held to force the controls back (unlocks them too). */
    private static final long ESC_HOLD_MS = 2000;

    private final GreenfootStage owner;
    private final ImageView imageView = new ImageView();
    private final StackPane root = new StackPane();
    private final VBox controlsBox = new VBox();
    private final ControlPanel controlPanel;
    private final ToggleButton pixelPerfectButton;
    private final ToggleButton lockButton;
    private final Label lockedHint = new Label();
    private final Label scaleLabel = new Label();

    private boolean controlsVisible = true;
    private boolean locked = false;
    /** The factor the world image is currently shown at (for Greenfoot.getDisplayScale). */
    private double displayScale = 1.0;
    private long escDownSince = -1;
    private double dragOffsetX, dragOffsetY;
    /** Set once leave() has started, so a second request does nothing. */
    private boolean leaving = false;

    public FullScreenView(GreenfootStage owner)
    {
        this.owner = owner;
        setTitle(owner.getTitle());
        // Deliberately no initOwner(): on macOS an owned window is a child window
        // and cannot enter native full-screen mode (it only maximises).
        // Without an owner or a position JavaFX would centre this window on the
        // primary display, and macOS goes full screen on the display the window is
        // on. Start over the main window instead, so full screen uses its display.
        setX(owner.getX());
        setY(owner.getY());
        setWidth(owner.getWidth());
        setHeight(owner.getHeight());

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFocusTraversable(true);
        root.setStyle("-fx-background-color: black;");
        root.getChildren().add(imageView);
        StackPane.setAlignment(imageView, Pos.CENTER);

        controlPanel = new ControlPanel(owner, new Region());
        pixelPerfectButton = new ToggleButton(Config.getString("fullscreen.pixelPerfect"));
        pixelPerfectButton.setFocusTraversable(false);
        pixelPerfectButton.setOnAction(e -> {
            relayout();
            owner.displayStateChanged();
        });
        Button hideButton = new Button(Config.getString("fullscreen.hideControls"));
        hideButton.setFocusTraversable(false);
        hideButton.setOnAction(e -> {
            setControlsVisible(false);
            owner.displayStateChanged();
        });
        lockButton = new ToggleButton(Config.getString("fullscreen.lockControls"));
        lockButton.setFocusTraversable(false);
        lockButton.setOnAction(e -> {
            locked = lockButton.isSelected();
            if (locked) {
                setControlsVisible(false);
            }
            owner.displayStateChanged();
        });
        Button exitButton = new Button(Config.getString("fullscreen.exit"));
        exitButton.setFocusTraversable(false);
        exitButton.setOnAction(e -> owner.exitFullScreenView());

        HBox extras = new HBox(8, pixelPerfectButton, hideButton, lockButton, exitButton);
        extras.setAlignment(Pos.CENTER);
        Label hint = new Label(Config.getString("fullscreen.hint"));
        hint.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 11px;");
        controlsBox.getChildren().addAll(controlPanel, extras, hint);
        controlsBox.setSpacing(6);
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPadding(new Insets(10, 16, 10, 16));
        controlsBox.setMaxWidth(Region.USE_PREF_SIZE);
        controlsBox.setMaxHeight(Region.USE_PREF_SIZE);
        controlsBox.setStyle("-fx-background-color: rgba(30,30,30,0.85); -fx-background-radius: 10;"
                + " -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 10;");
        StackPane.setAlignment(controlsBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(controlsBox, new Insets(0, 0, 24, 0));
        makeDraggable(controlsBox);
        root.getChildren().add(controlsBox);

        scaleLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 11px;");
        StackPane.setAlignment(scaleLabel, Pos.BOTTOM_LEFT);
        StackPane.setMargin(scaleLabel, new Insets(0, 0, 4, 8));
        root.getChildren().add(scaleLabel);
        scaleLabel.visibleProperty().bind(controlsBox.visibleProperty());

        lockedHint.setText(Config.getString("fullscreen.lockedHint"));
        lockedHint.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 11px;");
        lockedHint.setVisible(false);
        StackPane.setAlignment(lockedHint, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(lockedHint, new Insets(0, 8, 4, 0));
        root.getChildren().add(lockedHint);

        Scene scene = new Scene(root, Color.BLACK);
        Config.addGreenfootStylesheets(scene);
        setScene(scene);
        setFullScreenExitHint("");
        setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        JavaFXUtil.addChangeListenerPlatform(scene.widthProperty(), n -> relayout());
        JavaFXUtil.addChangeListenerPlatform(scene.heightProperty(), n -> relayout());

        // Keys: Escape toggles the controls (hold 2 s to force them back and unlock),
        // Shortcut+Shift+F leaves full screen. Everything is also forwarded to the world.
        KeyCombination exitCombo = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);
        scene.addEventFilter(KeyEvent.ANY, e -> {
            if (e.getEventType() == KeyEvent.KEY_PRESSED) {
                if (exitCombo.match(e)) {
                    owner.exitFullScreenView();
                    e.consume();
                    return;
                }
                if (e.getCode() == KeyCode.ESCAPE) {
                    long now = System.currentTimeMillis();
                    if (escDownSince < 0) {
                        escDownSince = now;
                        if (!locked) {
                            setControlsVisible(!controlsVisible);
                            owner.displayStateChanged();
                        }
                    }
                    else if (now - escDownSince >= ESC_HOLD_MS) {
                        // Teacher recovery: unlock and show
                        locked = false;
                        lockButton.setSelected(false);
                        setControlsVisible(true);
                        escDownSince = Long.MAX_VALUE / 2; // don't re-trigger until released
                        owner.displayStateChanged();
                    }
                }
            }
            else if (e.getEventType() == KeyEvent.KEY_RELEASED && e.getCode() == KeyCode.ESCAPE) {
                escDownSince = -1;
            }
            owner.forwardWorldKeyEvent(e);
        });

        imageView.addEventFilter(MouseEvent.ANY, e -> {
            Point2D worldPos = toWorld(e.getSceneX(), e.getSceneY());
            owner.forwardWorldMouseEvent(e, worldPos, false);
            if (e.getEventType() == MouseEvent.MOUSE_PRESSED) {
                imageView.requestFocus();
            }
        });

        setOnCloseRequest(e -> {
            e.consume();
            owner.exitFullScreenView();
        });
        setOnShown(e -> {
            // Enter native full screen once the window exists (macOS needs this order).
            setFullScreen(true);
            relayout();
            imageView.requestFocus();
            owner.notifyWorldFocus(true);
        });
        setOnHidden(e -> owner.notifyWorldFocus(false));
        // macOS can leave full screen without us (the green window button, for example);
        // treat that as leaving full-screen play mode, rather than keeping a plain window.
        JavaFXUtil.addChangeListenerPlatform(fullScreenProperty(), fs -> {
            if (!fs && isShowing() && !leaving) {
                owner.exitFullScreenView();
            }
        });
    }

    /**
     * Leave native full screen, then close this window. Closing a window that is
     * still in native full screen deadlocks JavaFX on macOS: Stage.close() waits for
     * the exit animation while holding the render lock, and a pulse during that wait
     * blocks on the renderer, which needs the same lock. setFullScreen(false) waits
     * for the animation without holding the lock.
     */
    public void leave()
    {
        if (leaving) {
            return;
        }
        leaving = true;
        if (isFullScreen()) {
            setFullScreen(false);
        }
        JavaFXUtil.runAfterCurrent(this::close);
    }

    /** Show the given world image (called for every frame). */
    public void setImage(Image image)
    {
        Image old = imageView.getImage();
        imageView.setImage(image);
        if (image != null && (old == null || old.getWidth() != image.getWidth() || old.getHeight() != image.getHeight())) {
            relayout();
        }
    }

    /** Mirror of GreenfootStage.updateGUIState for the floating controls. */
    public void updateState(State newState, boolean atBreakpoint)
    {
        controlPanel.updateState(newState, atBreakpoint);
    }

    /** Keep the floating speed slider in step with the main one. */
    public void setSpeed(int speed)
    {
        controlPanel.setSpeed(speed);
    }

    public void setControlsVisible(boolean visible)
    {
        controlsVisible = visible;
        controlsBox.setVisible(visible);
        controlsBox.setManaged(visible);
        lockedHint.setVisible(!visible);
    }

    public boolean isControlsVisible()
    {
        return controlsVisible;
    }

    /** Lock (hide, ignore Escape) or unlock the controls; used by the scenario API later. */
    public void setControlsLocked(boolean lock)
    {
        locked = lock;
        lockButton.setSelected(lock);
        if (lock) {
            setControlsVisible(false);
        }
    }

    public boolean isControlsLocked()
    {
        return locked;
    }

    /** Whole-number, unsmoothed scaling (true) or smooth fit (false); used by the scenario API. */
    public void setPixelPerfect(boolean pixelPerfect)
    {
        if (pixelPerfectButton.isSelected() != pixelPerfect) {
            pixelPerfectButton.setSelected(pixelPerfect);
            relayout();
        }
    }

    public boolean isPixelPerfect()
    {
        return pixelPerfectButton.isSelected();
    }

    /** The factor the world image is currently shown at. */
    public double getDisplayScale()
    {
        return displayScale;
    }

    /** Convert scene coordinates on this window to world pixel coordinates. */
    public Point2D toWorld(double sceneX, double sceneY)
    {
        Point2D local = imageView.sceneToLocal(sceneX, sceneY);
        Image img = imageView.getImage();
        double shownW = imageView.getBoundsInLocal().getWidth();
        double shownH = imageView.getBoundsInLocal().getHeight();
        if (img == null || shownW <= 0 || shownH <= 0) {
            return local;
        }
        return new Point2D(local.getX() * img.getWidth() / shownW, local.getY() * img.getHeight() / shownH);
    }

    /** Size the image view for the current scene size and scaling mode. */
    private void relayout()
    {
        Image img = imageView.getImage();
        Scene scene = getScene();
        if (img == null || scene == null) {
            return;
        }
        double sw = scene.getWidth();
        double sh = scene.getHeight();
        if (sw <= 0 || sh <= 0) {
            java.util.List<Screen> screens = Screen.getScreensForRectangle(getX(), getY(),
                    Math.max(1, getWidth()), Math.max(1, getHeight()));
            javafx.geometry.Rectangle2D b = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getBounds();
            sw = b.getWidth();
            sh = b.getHeight();
        }
        double iw = img.getWidth();
        double ih = img.getHeight();
        if (pixelPerfectButton.isSelected()) {
            int scale = (int) Math.max(1, Math.floor(Math.min(sw / iw, sh / ih)));
            displayScale = scale;
            imageView.setSmooth(false);
            imageView.setFitWidth(iw * scale);
            imageView.setFitHeight(ih * scale);
            scaleLabel.setText(String.format("%dx%d at %dx (pixel-perfect)", (int) iw, (int) ih, scale));
        }
        else {
            double scale = Math.min(sw / iw, sh / ih);
            displayScale = scale;
            imageView.setSmooth(true);
            imageView.setFitWidth(iw * scale);
            imageView.setFitHeight(ih * scale);
            boolean whole = Math.abs(scale - Math.rint(scale)) < 0.001;
            scaleLabel.setText(String.format("%dx%d at %.2fx%s", (int) iw, (int) ih, scale,
                    whole ? "" : " (not a whole number: try Pixel-perfect or a world size from the World template tip)"));
        }
    }

    private void makeDraggable(Region node)
    {
        node.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX() - node.getTranslateX();
            dragOffsetY = e.getSceneY() - node.getTranslateY();
        });
        node.setOnMouseDragged(e -> {
            node.setTranslateX(e.getSceneX() - dragOffsetX);
            node.setTranslateY(e.getSceneY() - dragOffsetY);
        });
    }
}
