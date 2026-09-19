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
package greenfoot.guifx.superide;

import bluej.utility.javafx.JavaFXUtil;
import greenfoot.guifx.superide.folders.ClassFolders;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.function.Consumer;

/**
 * The SuperGreenfoot IDE's main window: top bar with the run controls, a
 * collapsible Classes panel on the left, the world (or an editor tab) in the
 * middle with the Output panel below, a collapsible Inspector on the right,
 * and a status bar.
 *
 * <p>This class is only the window. It knows nothing about projects or the
 * debug VM: the controller wiring sets its data and reacts to its callbacks.
 * Collapsing a panel leaves a thin rail and gives the world the space.
 */
@OnThread(Tag.FXPlatform)
public class SuperStage extends Stage
{
    /** What the compile pill in the top bar says. */
    public enum CompileState { COMPILED, COMPILING, NEEDS_COMPILE, ERRORS }


    private final BooleanProperty leftOpen = new SimpleBooleanProperty(true);
    private final BooleanProperty rightOpen = new SimpleBooleanProperty(true);
    private final BooleanProperty bottomOpen = new SimpleBooleanProperty(true);
    private final BooleanProperty running = new SimpleBooleanProperty(false);
    private final IntegerProperty speed = new SimpleIntegerProperty(50);

    private final ClassBrowserPane classBrowser = new ClassBrowserPane();
    private final InspectorPane inspector = new InspectorPane();
    private final OutputPane output = new OutputPane();
    private final WorldHost worldHost = new WorldHost();
    private final ClassFolders.Listener statusListener = this::updateStatus;
    private final BorderPane root = new BorderPane();
    private final Pane glassPane = new Pane();
    private final Label worldMessage = new Label();
    private final Label hungMessage = new Label();
    private final HBox controls = new HBox(8);
    private final Slider speedSlider = new Slider(0, 100, 50);
    /** The world, its messages and the zoom button: the World tab's content */
    private final StackPane worldArea = new StackPane();
    /** The centre when no editor host supplies it: a World-only tab strip over the world area */
    private final VBox defaultCentre = new VBox();
    /** Holds whatever fills the centre (the default centre, or an editor host with a World tab) */
    private final StackPane centreSlot = new StackPane();
    private Node topBar;
    private Node twirler;
    private Node welcome;

    private final Button scenarioButton = new Button("Scenario");
    private final Button actButton = Widgets.button("Act", SuperIcons.ACT, () -> run(this.onAct));
    private final Button runButton = new Button();
    private final Button resetButton = Widgets.button("Reset", SuperIcons.RESET, () -> run(this.onReset));
    private final HBox compilePill = new HBox();
    private final Label compileLabel = new Label("Compiled");
    private final StackPane compileIcon = new StackPane();
    private final Label classesStatus = new Label();
    private final Label runStatus = new Label();
    private final Label worldStatus = new Label();
    private final Label worldTabLabel = new Label("World");
    private final Label leftRailText = new Label("Classes");
    private final Button zoomButton = new Button();
    private final HBox tabStrip = new HBox();
    private final HBox worldTab;
    private String worldName = "";
    private ContextMenu scenarioMenu;

    private Runnable onAct;
    private Runnable onRunPause;
    private Runnable onReset;
    private Runnable onSwitchToClassic;
    private Runnable onShare;

    public SuperStage()
    {
        setTitle("Super Greenfoot");
        worldTab = makeTab(SuperIcons.WORLD, worldTabLabel, null);
        worldTab.getStyleClass().add("sg-on");

        root.getStyleClass().add("sg-root");
        topBar = buildTopBar();
        root.setTop(topBar);
        root.setCenter(buildMiddle());
        root.setBottom(buildStatusBar());
        // A layer above everything, for things that follow the mouse (a new actor being placed):
        glassPane.setMouseTransparent(true);
        glassPane.setPickOnBounds(false);
        StackPane layers = new StackPane(root, glassPane);

        Scene scene = new Scene(layers, 1440, 900);
        SuperTheme.install(scene);
        setScene(scene);
        setMinWidth(1100);
        setMinHeight(640);

        classBrowser.setOnCollapse(() -> leftOpen.set(false));
        inspector.setOnCollapse(() -> rightOpen.set(false));
        output.setOnCollapse(() -> bottomOpen.set(false));
        inspector.setFolders(classBrowser.getFolders());
        classBrowser.getFolders().addListener(statusListener);
        JavaFXUtil.addChangeListenerPlatform(running, is -> updateRunState());
        JavaFXUtil.addChangeListenerPlatform(worldHost.scaleProperty(), now -> updateScaleText());
        JavaFXUtil.addChangeListenerPlatform(worldHost.zoomProperty(), now -> updateScaleText());
        updateRunState();
        updateStatus();
        updateScaleText();
    }

    // ------------------------------------------------------------------ layout

    private Node buildTopBar()
    {
        StackPane logo = new StackPane(SuperIcons.icon(SuperIcons.LEAF, 16));
        logo.getStyleClass().add("sg-logo");
        Label brand = new Label("Super Greenfoot");
        brand.getStyleClass().add("sg-brand");
        brand.setMinWidth(Region.USE_PREF_SIZE);
        Label slash = new Label("/");
        slash.getStyleClass().add("sg-faint");
        scenarioButton.getStyleClass().addAll("sg-flat", "sg-scenario");
        scenarioButton.setGraphic(SuperIcons.icon(SuperIcons.CHEVRON_DOWN, 12));
        scenarioButton.setContentDisplay(ContentDisplay.RIGHT);
        scenarioButton.setTooltip(new Tooltip("Scenario"));
        scenarioButton.setOnAction(e -> {
            if (scenarioMenu != null)
            {
                scenarioMenu.show(scenarioButton, Side.BOTTOM, 0, 4);
            }
        });
        HBox brandBox = new HBox(10, logo, brand, slash, scenarioButton);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPrefWidth(330);
        brandBox.setMinWidth(300);

        actButton.setTooltip(new Tooltip("Act once"));
        runButton.getStyleClass().add("sg-primary");
        runButton.setTooltip(new Tooltip("Run or pause the scenario"));
        runButton.setOnAction(e -> run(onRunPause));
        resetButton.setTooltip(new Tooltip("Reset the world"));
        Region divider = new Region();
        divider.getStyleClass().add("sg-divider");
        HBox.setMargin(divider, new Insets(0, 8, 0, 8));
        Label speedLabel = new Label("Speed");
        speedLabel.getStyleClass().add("sg-muted");
        Slider slider = speedSlider;
        slider.setValue(speed.get());
        slider.getStyleClass().add("sg-speed");
        slider.setPrefWidth(130);
        slider.setAccessibleText("Speed");
        speedLabel.setLabelFor(slider);
        JavaFXUtil.addChangeListenerPlatform(slider.valueProperty(), now -> speed.set((int) Math.round(now.doubleValue())));
        JavaFXUtil.addChangeListenerPlatform(speed, now -> {
            if (Math.round(slider.getValue()) != now.intValue())
            {
                slider.setValue(now.intValue());
            }
        });
        controls.getChildren().setAll(actButton, runButton, resetButton, divider, speedLabel, slider);
        controls.setAlignment(Pos.CENTER);
        HBox.setHgrow(controls, Priority.ALWAYS);

        compileIcon.getChildren().setAll(SuperIcons.icon(SuperIcons.CHECK, 12));
        compilePill.getChildren().setAll(compileIcon, compileLabel);
        compilePill.getStyleClass().add("sg-pill");
        compilePill.setMinWidth(Region.USE_PREF_SIZE);
        Button themeButton = Widgets.iconButton(SuperIcons.MOON, "Use the dark theme", 17,
                () -> SuperTheme.darkProperty().set(!SuperTheme.darkProperty().get()));
        JavaFXUtil.addChangeListenerPlatform(SuperTheme.darkProperty(), isDark -> updateThemeButton(themeButton, isDark));
        updateThemeButton(themeButton, SuperTheme.darkProperty().get());
        Button classic = Widgets.button("Classic IDE", SuperIcons.SWITCH, () -> run(onSwitchToClassic));
        classic.setTooltip(new Tooltip("Switch to the Classic Greenfoot IDE. Your world keeps running."));
        classic.setMinWidth(Region.USE_PREF_SIZE);
        Button share = Widgets.button("Share", SuperIcons.SHARE, () -> run(onShare));
        share.setTooltip(new Tooltip("Share or export"));
        share.setMinWidth(Region.USE_PREF_SIZE);
        HBox right = new HBox(6, compilePill, themeButton, classic, share);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setPrefWidth(330);
        right.setMinWidth(Region.USE_PREF_SIZE);

        HBox bar = new HBox(brandBox, controls, right);
        bar.getStyleClass().add("sg-topbar");
        return bar;
    }

    private void updateThemeButton(Button button, boolean isDark)
    {
        button.setGraphic(SuperIcons.icon(isDark ? SuperIcons.SUN : SuperIcons.MOON, 17));
        String text = isDark ? "Use the light theme" : "Use the dark theme";
        button.getTooltip().setText(text);
        button.setAccessibleText(text);
    }

    private Node buildMiddle()
    {
        StackPane leftSlot = new StackPane();
        VBox leftRail = rail(SuperIcons.COLLAPSE_RIGHT, "Expand the classes panel", leftRailText,
                () -> leftOpen.set(true), "sg-left");
        bindSlot(leftSlot, leftOpen, classBrowser, leftRail);

        StackPane rightSlot = new StackPane();
        VBox rightRail = rail(SuperIcons.COLLAPSE_LEFT, "Expand the inspector", new Label("Inspector"),
                () -> rightOpen.set(true), "sg-right");
        bindSlot(rightSlot, rightOpen, inspector, rightRail);

        zoomButton.getStyleClass().add("sg-zoom");
        zoomButton.setTooltip(new Tooltip("Switch between fitting the world and whole-number (pixel-perfect) scaling"));
        zoomButton.setOnAction(e -> worldHost.zoomProperty().set(
                worldHost.zoomProperty().get() == WorldHost.Zoom.FIT ? WorldHost.Zoom.PIXEL_PERFECT : WorldHost.Zoom.FIT));
        StackPane.setAlignment(zoomButton, Pos.TOP_RIGHT);
        StackPane.setMargin(zoomButton, new Insets(8, 12, 0, 0));
        tabStrip.getStyleClass().add("sg-tabstrip");
        tabStrip.getChildren().setAll(worldTab);
        worldMessage.getStyleClass().add("sg-world-message");
        worldMessage.setWrapText(true);
        worldMessage.setMouseTransparent(true);
        worldMessage.setVisible(false);
        hungMessage.getStyleClass().add("sg-hung-message");
        hungMessage.setWrapText(true);
        hungMessage.setMouseTransparent(true);
        hungMessage.setVisible(false);
        StackPane.setAlignment(hungMessage, Pos.BOTTOM_CENTER);
        worldArea.getStyleClass().add("sg-world-area");
        worldArea.getChildren().setAll(worldHost, worldMessage, hungMessage, zoomButton);
        worldArea.setMinSize(0, 0);
        VBox.setVgrow(worldArea, Priority.ALWAYS);
        defaultCentre.getChildren().setAll(tabStrip, worldArea);
        centreSlot.getChildren().setAll(defaultCentre);
        centreSlot.setMinSize(0, 0);
        VBox.setVgrow(centreSlot, Priority.ALWAYS);

        StackPane bottomSlot = new StackPane();
        bindSlot(bottomSlot, bottomOpen, output, collapsedOutputBar());

        VBox centreColumn = new VBox(centreSlot, bottomSlot);
        HBox.setHgrow(centreColumn, Priority.ALWAYS);
        centreColumn.setMinWidth(0);
        return new HBox(leftSlot, centreColumn, rightSlot);
    }

    private static void bindSlot(StackPane slot, BooleanProperty open, Node full, Node collapsed)
    {
        slot.getChildren().setAll(open.get() ? full : collapsed);
        JavaFXUtil.addChangeListenerPlatform(open, is -> slot.getChildren().setAll(is ? full : collapsed));
    }

    private VBox rail(String iconPath, String tooltip, Label text, Runnable expand, String sideClass)
    {
        Button expandButton = Widgets.iconButton(iconPath, tooltip, 16, expand);
        Button label = Widgets.railLabel(text, expand);
        label.setAccessibleText(tooltip);
        VBox rail = new VBox(expandButton, label);
        rail.getStyleClass().addAll("sg-panel", "sg-rail", sideClass);
        return rail;
    }

    private Node collapsedOutputBar()
    {
        Button expand = new Button();
        expand.getStyleClass().add("sg-ptab");
        expand.setGraphic(SuperIcons.icon(SuperIcons.CHEVRON_UP, 14));
        expand.textProperty().bind(output.problemSummaryProperty().map(p -> "Output · " + p));
        expand.setTooltip(new Tooltip("Expand the output panel"));
        expand.setOnAction(e -> bottomOpen.set(true));
        Label last = new Label();
        last.getStyleClass().add("sg-last-line");
        last.textProperty().bind(output.lastLineProperty());
        last.setMinWidth(0);
        HBox.setHgrow(last, Priority.ALWAYS);
        HBox bar = new HBox(expand, last);
        bar.getStyleClass().addAll("sg-bottom", "sg-bottom-bar");
        return bar;
    }

    private Node buildStatusBar()
    {
        HBox bar = new HBox(classesStatus, runStatus, Widgets.spacer(), worldStatus);
        bar.getStyleClass().add("sg-status");
        return bar;
    }

    private HBox makeTab(String iconPath, Label label, Runnable onClose)
    {
        HBox tab = new HBox(SuperIcons.icon(iconPath, 14), label);
        tab.getStyleClass().add("sg-tab");
        tab.setFocusTraversable(true);
        tab.setAccessibleRole(AccessibleRole.TAB_ITEM);
        if (onClose != null)
        {
            tab.getStyleClass().add("sg-editor-tab");
            Button close = Widgets.iconButton(SuperIcons.CLOSE, "Close", 12, onClose);
            close.getStyleClass().add("sg-small");
            tab.getChildren().add(close);
        }
        return tab;
    }

    // --------------------------------------------------------------- behaviour

    private void updateRunState()
    {
        boolean isRunning = running.get();
        runButton.setText(isRunning ? "Pause" : "Run");
        StackPane icon = isRunning ? SuperIcons.icon(SuperIcons.PAUSE, 14) : SuperIcons.filledIcon(SuperIcons.PLAY, 14);
        if (isRunning)
        {
            SuperIcons.shapeOf(icon).getStyleClass().add("sg-bold");
        }
        runButton.setGraphic(icon);
        runStatus.setText(isRunning ? "Running" : "Paused");
    }

    private void updateStatus()
    {
        int classes = classBrowser.getClasses().size();
        int folders = classBrowser.getFolders().getFolderNames().size();
        classesStatus.setText(classes + (classes == 1 ? " class" : " classes") + " · "
                + folders + (folders == 1 ? " folder" : " folders"));
        leftRailText.setText("Classes · " + classes);
    }

    private void updateScaleText()
    {
        zoomButton.setText(worldHost.describeScale());
        if (worldHost.getWorldWidth() > 0)
        {
            worldStatus.setText((worldName.isEmpty() ? "" : worldName + " · ")
                    + (int) worldHost.getWorldWidth() + " × " + (int) worldHost.getWorldHeight()
                    + " · shown at " + Math.round(worldHost.scaleProperty().get() * 100) + "%");
        }
        else
        {
            worldStatus.setText("No world");
        }
    }

    private static void run(Runnable action)
    {
        if (action != null)
        {
            action.run();
        }
    }

    // ------------------------------------------------------------------- API

    /** The scenario's name, shown in the top bar and the window title. */
    public void setScenarioName(String name)
    {
        scenarioButton.setText(name);
        setTitle(name + " - Super Greenfoot");
    }

    /** The menu the scenario name opens (New, Open, Save, Close...). */
    public void setScenarioMenu(ContextMenu menu)
    {
        scenarioMenu = menu;
    }

    public void setOnAct(Runnable action)
    {
        onAct = action;
    }

    public void setOnRunPause(Runnable action)
    {
        onRunPause = action;
    }

    public void setOnReset(Runnable action)
    {
        onReset = action;
    }

    public void setOnSwitchToClassic(Runnable action)
    {
        onSwitchToClassic = action;
    }

    public void setOnShare(Runnable action)
    {
        onShare = action;
    }

    /** Whether the scenario is running: the Run button then reads Pause. */
    public BooleanProperty runningProperty()
    {
        return running;
    }

    /** The speed slider, 0 to 100. */
    public IntegerProperty speedProperty()
    {
        return speed;
    }

    public void setControlsEnabled(boolean act, boolean runPause, boolean reset)
    {
        actButton.setDisable(!act);
        runButton.setDisable(!runPause);
        resetButton.setDisable(!reset);
    }

    public void setCompileState(CompileState state, int errorCount)
    {
        compilePill.getStyleClass().removeAll("sg-error", "sg-busy");
        switch (state)
        {
            case COMPILED:
                compileLabel.setText("Compiled");
                compileIcon.getChildren().setAll(SuperIcons.icon(SuperIcons.CHECK, 12));
                break;
            case COMPILING:
                compilePill.getStyleClass().add("sg-busy");
                compileLabel.setText("Compiling...");
                compileIcon.getChildren().setAll(SuperIcons.icon(SuperIcons.RESET, 12));
                break;
            case NEEDS_COMPILE:
                compilePill.getStyleClass().add("sg-busy");
                compileLabel.setText("Not compiled");
                compileIcon.getChildren().setAll(SuperIcons.icon(SuperIcons.RESET, 12));
                break;
            case ERRORS:
                compilePill.getStyleClass().add("sg-error");
                compileLabel.setText(errorCount == 1 ? "1 error" : errorCount + " errors");
                compileIcon.getChildren().setAll(SuperIcons.icon(SuperIcons.CLOSE, 12));
                break;
        }
    }

    public ClassBrowserPane getClassBrowser()
    {
        return classBrowser;
    }

    public InspectorPane getInspector()
    {
        return inspector;
    }

    public OutputPane getOutput()
    {
        return output;
    }

    public WorldHost getWorldHost()
    {
        return worldHost;
    }

    /** The scenario's classes, for the Classes panel and the status bar. */
    public void setClasses(List<ClassEntry> classes)
    {
        classBrowser.setClasses(classes);
        updateStatus();
    }

    /** The scenario's folder model, edited by the Classes panel and the Inspector. */
    public void setClassFolders(ClassFolders folders)
    {
        classBrowser.getFolders().removeListener(statusListener);
        classBrowser.setFolders(folders);
        inspector.setFolders(folders);
        folders.addListener(statusListener);
        updateStatus();
    }

    /** Double-click or Enter on a class. */
    public void setOnOpenClass(Consumer<String> action)
    {
        classBrowser.setOnOpenClass(action);
    }

    /**
     * Show a world: the node is laid out at width x height and scaled to fit.
     * Pass a null node for "no world".
     */
    public void setWorld(Node node, double width, double height, String name)
    {
        worldName = name == null ? "" : name;
        worldTabLabel.setText(worldName.isEmpty() ? "World" : "World: " + worldName);
        worldHost.setWorld(node, width, height);
        updateScaleText();
    }

    /**
     * The world shown changed size (the same node stays in place).
     */
    public void setWorldSize(double width, double height)
    {
        worldHost.setWorldSize(width, height);
        updateScaleText();
    }

    /**
     * The name of the world shown, for the World tab and the status bar.
     */
    public void setWorldName(String name)
    {
        String newName = name == null ? "" : name;
        if (!newName.equals(worldName))
        {
            worldName = newName;
            worldTabLabel.setText(worldName.isEmpty() ? "World" : "World: " + worldName);
            updateScaleText();
        }
    }

    public void appendOutput(String line)
    {
        output.appendOutput(line);
    }

    public void appendCall(String call)
    {
        output.appendCall(call);
    }

    public void showActor(InspectorPane.ActorDetails actor)
    {
        inspector.showActor(actor);
    }

    public void showClass(InspectorPane.ClassDetails cls)
    {
        inspector.showClass(cls);
    }

    public void clearInspector()
    {
        inspector.showNothing();
    }

    public BooleanProperty leftOpenProperty()
    {
        return leftOpen;
    }

    public BooleanProperty rightOpenProperty()
    {
        return rightOpen;
    }

    public BooleanProperty bottomOpenProperty()
    {
        return bottomOpen;
    }

    /**
     * Add the menu bar (on macOS it goes into the system menu bar and takes no room here).
     */
    public void setMenuBar(MenuBar menuBar)
    {
        root.setTop(new VBox(menuBar, topBar));
    }

    /**
     * A layer over the whole window that does not take mouse events, for things that
     * follow the mouse.  It is the size of the scene, so its coordinates are scene coordinates.
     */
    public Pane getGlassPane()
    {
        return glassPane;
    }

    /**
     * Show a message in place of the world (why there is no world, or what to do next).
     * An empty message shows nothing.
     */
    public void setWorldMessage(String message)
    {
        worldMessage.setText(message == null ? "" : message);
        worldMessage.setVisible(message != null && !message.isEmpty());
    }

    /**
     * Show or hide the message that the scenario seems to be stuck (under the world).
     */
    public void setHungMessage(String message, boolean visible)
    {
        hungMessage.setText(message);
        hungMessage.setVisible(visible);
    }

    /**
     * Put a node (the busy indicator shown while the scenario's code runs for a long
     * time) after the Reset button.
     */
    public void setTwirler(Node node)
    {
        if (twirler != null)
        {
            controls.getChildren().remove(twirler);
        }
        twirler = node;
        if (node != null)
        {
            controls.getChildren().add(controls.getChildren().indexOf(resetButton) + 1, node);
        }
    }

    /**
     * Show a node over the world area (for example a welcome panel when no scenario
     * is open), or remove it again with null.
     */
    public void setWelcome(Node node)
    {
        if (welcome != null)
        {
            worldArea.getChildren().remove(welcome);
        }
        welcome = node;
        if (node != null)
        {
            worldArea.getChildren().add(node);
        }
    }

    /** Enable or disable the speed slider. */
    public void setSpeedEnabled(boolean enabled)
    {
        speedSlider.setDisable(!enabled);
    }

    /**
     * The world area: the world, its messages and the zoom button (and the welcome panel
     * when there is no scenario).  It is the content of the World tab.
     */
    public StackPane getWorldArea()
    {
        return worldArea;
    }

    /**
     * A header for a World tab (the world icon and the world's name), for a tab strip
     * other than this window's own (an editor host's).
     */
    public Node makeWorldTabGraphic()
    {
        Label label = new Label();
        label.textProperty().bind(worldTabLabel.textProperty());
        label.getStyleClass().add("sg-world-tab-label");
        HBox header = new HBox(7, SuperIcons.icon(SuperIcons.WORLD, 14), label);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /**
     * Fill the centre (above the Output panel) with the given node, typically an editor
     * host whose first tab shows {@link #getWorldArea()}.  With null, the centre goes back
     * to a World-only tab strip over the world area.
     */
    public void setCentreContent(Node content)
    {
        if (content == null)
        {
            // Take the world area back from whatever was showing it:
            if (worldArea.getParent() != defaultCentre)
            {
                if (worldArea.getParent() instanceof Pane)
                {
                    ((Pane) worldArea.getParent()).getChildren().remove(worldArea);
                }
                defaultCentre.getChildren().setAll(tabStrip, worldArea);
            }
            centreSlot.getChildren().setAll(defaultCentre);
        }
        else
        {
            centreSlot.getChildren().setAll(content);
        }
    }
}
