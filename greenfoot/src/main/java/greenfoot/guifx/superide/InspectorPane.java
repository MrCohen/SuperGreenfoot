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

import greenfoot.guifx.superide.folders.ClassFolders;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * The SuperGreenfoot IDE's Inspector panel: details and actions for the actor
 * clicked in the world, or for the class selected in the Classes panel.
 */
@OnThread(Tag.FXPlatform)
public class InspectorPane extends VBox
{
    static final double WIDTH = 296;
    private static final String NO_FOLDER = "No folder";

    /** A method the inspector offers to call. */
    @OnThread(Tag.FXPlatform)
    public static final class MethodEntry
    {
        public final String signature;
        public final String returnType;
        public final Runnable onCall;

        public MethodEntry(String signature, String returnType, Runnable onCall)
        {
            this.signature = signature;
            this.returnType = returnType;
            this.onCall = onCall;
        }
    }

    /** What to show for an actor in the world. Fields left null are shown as blank. */
    @OnThread(Tag.FXPlatform)
    public static final class ActorDetails
    {
        public String variableName;
        public String className;
        public Image image;
        /** e.g. "Level1": shown as "a Player in Level1". */
        public String worldName;
        /** getX(), getY(), e.g. "(300, 431)". */
        public String location;
        /** getPreciseX(), getPreciseY(), e.g. "(300.00, 431.00)". */
        public String preciseLocation;
        public String rotation;
        public String z;
        public final List<MethodEntry> ownMethods = new ArrayList<>();
        public String inheritedFrom = "Actor";
        public final List<MethodEntry> inheritedMethods = new ArrayList<>();
        public Runnable onInspect;
        public Runnable onRemove;
    }

    /** What to show for a class. */
    @OnThread(Tag.FXPlatform)
    public static final class ClassDetails
    {
        public String name;
        public String superName;
        public Image image;
        /** e.g. "new Player()"; null hides the button. */
        public String constructorLabel;
        public final List<String> subclasses = new ArrayList<>();
        public Runnable onOpenEditor;
        public Runnable onConstruct;
        public Runnable onSetImage;
        public Runnable onDuplicate;
        public Runnable onDelete;
    }

    private final VBox body = new VBox();
    private final ClassFolders.Listener foldersListener = this::refreshFolderChoice;
    private ClassFolders folders;
    private ComboBox<String> folderChoice;
    private String shownClass;
    private Runnable onCollapse = () -> {};

    public InspectorPane()
    {
        getStyleClass().addAll("sg-panel", "sg-right");
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);
        Label title = new Label("Inspector");
        title.getStyleClass().add("sg-panel-title");
        title.setPadding(new Insets(0, 0, 0, 4));
        HBox header = new HBox(Widgets.iconButton(SuperIcons.COLLAPSE_RIGHT, "Collapse the inspector", 16,
                () -> onCollapse.run()), title);
        header.getStyleClass().add("sg-panel-header");
        header.setPadding(new Insets(0, 6, 0, 8));

        body.getStyleClass().add("sg-inspector-body");
        body.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(body);
        scroll.getStyleClass().add("sg-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(header, scroll);
        showNothing();
    }

    public void setOnCollapse(Runnable action)
    {
        onCollapse = action;
    }

    /** The folder model the class view's Folder chooser edits. */
    public void setFolders(ClassFolders folders)
    {
        if (this.folders != null)
        {
            this.folders.removeListener(foldersListener);
        }
        this.folders = folders;
        if (folders != null)
        {
            folders.addListener(foldersListener);
        }
        refreshFolderChoice();
    }

    /** Nothing selected. */
    public void showNothing()
    {
        shownClass = null;
        folderChoice = null;
        Label hint = new Label("Select a class, or click an actor in the world.");
        hint.getStyleClass().add("sg-insp-empty");
        body.getChildren().setAll(hint);
    }

    public void showActor(ActorDetails actor)
    {
        shownClass = null;
        folderChoice = null;
        List<Node> nodes = new ArrayList<>();

        Label name = new Label(blank(actor.variableName));
        name.getStyleClass().add("sg-insp-name");
        Label sub = new Label("a " + blank(actor.className)
                + (actor.worldName == null ? "" : " in " + actor.worldName));
        sub.getStyleClass().add("sg-insp-sub");
        nodes.add(headerRow(Widgets.tile(blank(actor.className), actor.image, 36), name, sub));

        GridPane fields = new GridPane();
        fields.getStyleClass().add("sg-fields");
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        fields.getColumnConstraints().addAll(half, half);
        addField(fields, 0, 0, "getX(), getY()", actor.location, "7 0 0 0");
        addField(fields, 1, 0, "Precise", actor.preciseLocation, "0 7 0 0");
        addField(fields, 0, 1, "Rotation", actor.rotation, "0 0 0 7");
        addField(fields, 1, 1, "Z (depth)", actor.z, "0 0 7 0");
        nodes.add(fields);

        VBox own = section(blank(actor.className) + " methods");
        for (MethodEntry m : actor.ownMethods)
        {
            own.getChildren().add(methodButton(m));
        }
        if (actor.ownMethods.isEmpty())
        {
            Label none = new Label("No methods of its own.");
            none.getStyleClass().add("sg-insp-empty");
            own.getChildren().add(none);
        }
        nodes.add(own);

        if (!actor.inheritedMethods.isEmpty())
        {
            VBox inherited = section("Inherited from " + actor.inheritedFrom);
            for (MethodEntry m : actor.inheritedMethods)
            {
                inherited.getChildren().add(methodButton(m));
            }
            nodes.add(inherited);
        }

        nodes.add(buttonRow(
                wideButton("Inspect fields", actor.onInspect),
                wideButton("Remove", actor.onRemove)));
        body.getChildren().setAll(nodes);
    }

    public void showClass(ClassDetails cls)
    {
        shownClass = cls.name;
        List<Node> nodes = new ArrayList<>();
        Label name = new Label(blank(cls.name));
        name.getStyleClass().add("sg-insp-title");
        Label sub = new Label("extends " + (cls.superName == null ? "Object" : ClassTreeModel.simple(cls.superName)));
        sub.getStyleClass().add("sg-insp-sub");
        nodes.add(headerRow(Widgets.tile(blank(cls.name), cls.image, 36), name, sub));

        Button open = new Button("Open editor");
        open.getStyleClass().add("sg-primary");
        open.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(open, Priority.ALWAYS);
        open.setOnAction(e -> run(cls.onOpenEditor));
        HBox actions = new HBox(8, open);
        if (cls.constructorLabel != null)
        {
            Button construct = wideButton(cls.constructorLabel, cls.onConstruct);
            construct.getStyleClass().add("sg-mono");
            actions.getChildren().add(construct);
        }
        nodes.add(actions);

        VBox folderSection = section("Folder");
        folderChoice = new ComboBox<>();
        folderChoice.getStyleClass().add("sg-folder-choice");
        folderChoice.setMaxWidth(Double.MAX_VALUE);
        folderChoice.setAccessibleText("Folder for " + cls.name);
        folderChoice.setOnAction(e -> {
            String chosen = folderChoice.getValue();
            if (folders != null && chosen != null && shownClass != null)
            {
                String target = NO_FOLDER.equals(chosen) ? "" : chosen;
                if (!target.equals(folders.getFolder(shownClass)))
                {
                    folders.setFolder(shownClass, target);
                }
            }
        });
        folderSection.getChildren().add(folderChoice);
        nodes.add(folderSection);
        refreshFolderChoice();

        VBox subs = section("Subclasses");
        Label subList = new Label(cls.subclasses.isEmpty() ? "None" : String.join(", ", cls.subclasses));
        subList.getStyleClass().add("sg-muted");
        subList.setWrapText(true);
        subs.getChildren().add(subList);
        nodes.add(subs);

        GridPane more = new GridPane();
        more.setHgap(6);
        for (int i = 0; i < 3; i++)
        {
            ColumnConstraints third = new ColumnConstraints();
            third.setPercentWidth(100.0 / 3);
            more.getColumnConstraints().add(third);
        }
        more.add(wideButton("Set image", cls.onSetImage), 0, 0);
        more.add(wideButton("Duplicate", cls.onDuplicate), 1, 0);
        more.add(wideButton("Delete", cls.onDelete), 2, 0);
        nodes.add(more);
        body.getChildren().setAll(nodes);
    }

    private void refreshFolderChoice()
    {
        if (folderChoice == null || folders == null || shownClass == null)
        {
            return;
        }
        List<String> options = new ArrayList<>();
        options.add(NO_FOLDER);
        options.addAll(folders.getFolderNames());
        folderChoice.getItems().setAll(options);
        String current = folders.getFolder(shownClass);
        folderChoice.setValue(current.isEmpty() ? NO_FOLDER : current);
    }

    private static Node headerRow(Node tile, Label title, Label subtitle)
    {
        VBox text = new VBox(1, title, subtitle);
        HBox row = new HBox(12, tile, text);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private static void addField(GridPane grid, int col, int row, String label, String value, String radius)
    {
        Label l = new Label(label);
        l.getStyleClass().add("sg-field-label");
        Label v = new Label(blank(value));
        v.getStyleClass().add("sg-field-value");
        // Precise coordinates are long; wrap after the comma rather than cut them off.
        v.setWrapText(true);
        VBox cell = new VBox(l, v);
        cell.getStyleClass().add("sg-field");
        cell.setStyle("-fx-background-radius: " + radius + ";");
        cell.setMaxWidth(Double.MAX_VALUE);
        grid.add(cell, col, row);
    }

    private static VBox section(String title)
    {
        VBox box = new VBox(Widgets.sectionLabel(title));
        box.getStyleClass().add("sg-section");
        return box;
    }

    private static Button methodButton(MethodEntry m)
    {
        Label sig = new Label(m.signature);
        Label ret = new Label(m.returnType == null ? "" : m.returnType);
        ret.getStyleClass().add("sg-ret");
        HBox content = new HBox(sig, Widgets.spacer(), ret);
        content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(content);
        button.getStyleClass().add("sg-method");
        button.setMaxWidth(Double.MAX_VALUE);
        content.prefWidthProperty().bind(button.widthProperty().subtract(22));
        button.setAccessibleText("Call " + m.signature);
        button.setOnAction(e -> run(m.onCall));
        return button;
    }

    private static Button wideButton(String text, Runnable action)
    {
        Button button = new Button(text);
        button.getStyleClass().add("sg-btn");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setDisable(action == null);
        button.setOnAction(e -> run(action));
        return button;
    }

    private static HBox buttonRow(Button... buttons)
    {
        return new HBox(8, buttons);
    }

    private static void run(Runnable action)
    {
        if (action != null)
        {
            action.run();
        }
    }

    private static String blank(String s)
    {
        return s == null ? "" : s;
    }
}
