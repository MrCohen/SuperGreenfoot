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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The SuperGreenfoot IDE's Classes panel: the scenario's classes grouped into
 * virtual folders, or by inheritance as in the Classic IDE. Classes are dragged
 * onto a folder (or onto "Not in a folder") to move them.
 */
@OnThread(Tag.FXPlatform)
public class ClassBrowserPane extends VBox
{
    /** How the classes are grouped. */
    public enum View { FOLDERS, INHERITANCE }

    /** Shows the menu for a class (or a built-in class) that was right-clicked. */
    @OnThread(Tag.FXPlatform)
    public interface ClassMenuHandler
    {
        /**
         * @param className  the class's name ("World" or "Actor" for the built-in rows)
         * @param anchor     the row that was clicked
         * @param screenX    where to show the menu
         * @param screenY    where to show the menu
         */
        void showMenu(String className, Node anchor, double screenX, double screenY);
    }

    private static final String DRAG_PREFIX = "sg-class:";
    static final double WIDTH = 272;

    private final ObjectProperty<View> view = new SimpleObjectProperty<>(View.FOLDERS);
    private final StringProperty selectedClass = new SimpleStringProperty(null);
    private final VBox list = new VBox();
    private final ClassFolders.Listener foldersListener = this::rebuild;
    private List<ClassEntry> classes = new ArrayList<>();
    private ClassFolders folders = new ClassFolders();
    private Consumer<String> onOpenClass = name -> {};
    private ClassMenuHandler onShowClassMenu = (name, anchor, x, y) -> {};
    private ClassMenuHandler onShowBuiltInMenu = (name, anchor, x, y) -> {};
    private Runnable onNewClass = () -> {};
    private Runnable onCollapse = () -> {};
    private String dragging;
    private String renaming;

    public ClassBrowserPane()
    {
        getStyleClass().addAll("sg-panel", "sg-left");
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);

        Label title = new Label("Classes");
        title.getStyleClass().add("sg-panel-title");
        HBox header = new HBox(title, Widgets.spacer(),
                Widgets.iconButton(SuperIcons.PLUS, "New class", 16, () -> onNewClass.run()),
                Widgets.iconButton(SuperIcons.FOLDER_PLUS, "New folder", 16, this::newFolder),
                Widgets.iconButton(SuperIcons.COLLAPSE_LEFT, "Collapse the classes panel", 16, () -> onCollapse.run()));
        header.getStyleClass().add("sg-panel-header");
        header.setPadding(new Insets(0, 6, 0, 14));

        ToggleGroup group = new ToggleGroup();
        ToggleButton foldersButton = segment("Folders", group);
        ToggleButton inheritanceButton = segment("Inheritance", group);
        foldersButton.setSelected(true);
        JavaFXUtil.addChangeListenerPlatform(group.selectedToggleProperty(), now -> {
            if (now != null)
            {
                view.set(now == foldersButton ? View.FOLDERS : View.INHERITANCE);
            }
        });
        JavaFXUtil.addChangeListenerPlatform(view, now -> {
            (now == View.FOLDERS ? foldersButton : inheritanceButton).setSelected(true);
            rebuild();
        });
        HBox segments = new HBox(foldersButton, inheritanceButton);
        segments.getStyleClass().add("sg-seg-box");
        HBox segmentsRow = new HBox(segments);
        HBox.setHgrow(segments, Priority.ALWAYS);
        segmentsRow.setPadding(new Insets(10, 12, 6, 12));

        list.setPadding(new Insets(4, 8, 12, 8));
        list.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("sg-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label hint = new Label("Folders only organise this list. Your code and files stay the same.");
        hint.getStyleClass().add("sg-hint");
        hint.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(header, segmentsRow, scroll, hint);
        JavaFXUtil.addChangeListenerPlatform(selectedClass, now -> rebuild());
        folders.addListener(foldersListener);
        rebuild();
    }

    private ToggleButton segment(String text, ToggleGroup group)
    {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("sg-seg");
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        // Keep one segment selected, like a real segmented control:
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (button.isSelected())
            {
                e.consume();
            }
        });
        return button;
    }

    /** Show these classes (replaces the previous list). */
    public void setClasses(List<ClassEntry> classes)
    {
        this.classes = new ArrayList<>(classes);
        rebuild();
    }

    public List<ClassEntry> getClasses()
    {
        return new ArrayList<>(classes);
    }

    /** Use this folder model (the panel edits it as the user drags and renames). */
    public void setFolders(ClassFolders folders)
    {
        this.folders.removeListener(foldersListener);
        this.folders = folders;
        folders.addListener(foldersListener);
        rebuild();
    }

    public ClassFolders getFolders()
    {
        return folders;
    }

    public ObjectProperty<View> viewProperty()
    {
        return view;
    }

    /** The selected class's name, or null. */
    public StringProperty selectedClassProperty()
    {
        return selectedClass;
    }

    /** Double-click or Enter on a class. */
    public void setOnOpenClass(Consumer<String> action)
    {
        onOpenClass = action;
    }

    /** Right-click on a class (the caller shows its class menu). */
    public void setOnShowClassMenu(ClassMenuHandler action)
    {
        onShowClassMenu = action;
    }

    /** Right-click on the built-in World or Actor row of the inheritance view. */
    public void setOnShowBuiltInMenu(ClassMenuHandler action)
    {
        onShowBuiltInMenu = action;
    }

    public void setOnNewClass(Runnable action)
    {
        onNewClass = action;
    }

    public void setOnCollapse(Runnable action)
    {
        onCollapse = action;
    }

    /** Add a folder called "New folder" (or "New folder 2", ...) and start renaming it. */
    public void newFolder()
    {
        String name = "New folder";
        for (int n = 2; folders.hasFolder(name); n++)
        {
            name = "New folder " + n;
        }
        folders.addFolder(name);
        renaming = name;
        view.set(View.FOLDERS);
        rebuild();
    }

    private void rebuild()
    {
        list.getChildren().clear();
        if (view.get() == View.FOLDERS)
        {
            for (ClassTreeModel.FolderGroup group : ClassTreeModel.folderGroups(folders, classes))
            {
                list.getChildren().add(folderNode(group));
            }
            VBox unfiled = new VBox();
            unfiled.getStyleClass().add("sg-folder");
            VBox.setMargin(unfiled, new Insets(8, 0, 0, 0));
            Label label = new Label("NOT IN A FOLDER");
            label.getStyleClass().add("sg-group-label");
            unfiled.getChildren().add(label);
            for (ClassEntry entry : ClassTreeModel.unfiled(folders, classes))
            {
                unfiled.getChildren().add(classRow(entry, 0, true));
            }
            makeDropTarget(unfiled, "");
            list.getChildren().add(unfiled);
        }
        else
        {
            for (ClassTreeModel.InheritanceGroup group : ClassTreeModel.inheritanceGroups(classes))
            {
                Label label = new Label(group.label.toUpperCase());
                label.getStyleClass().add("sg-group-label");
                list.getChildren().add(label);
                if (group.base != null)
                {
                    Label base = new Label(group.base);
                    Label builtIn = new Label("built in");
                    builtIn.getStyleClass().add("sg-sup");
                    HBox row = new HBox(Widgets.builtinTile(20), base, builtIn);
                    row.getStyleClass().addAll("sg-row", "sg-builtin");
                    String baseName = group.base;
                    row.setOnContextMenuRequested(e -> onShowBuiltInMenu.showMenu(baseName, row, e.getScreenX(), e.getScreenY()));
                    list.getChildren().add(row);
                }
                for (ClassTreeModel.InheritanceRow row : group.rows)
                {
                    list.getChildren().add(classRow(row.entry, row.depth, false));
                }
            }
        }
    }

    private Node folderNode(ClassTreeModel.FolderGroup group)
    {
        VBox box = new VBox();
        box.getStyleClass().add("sg-folder");

        StackPane chevron = SuperIcons.icon(SuperIcons.CHEVRON_RIGHT, 14);
        chevron.setRotate(group.open ? 90 : 0);
        Node name;
        if (group.name.equals(renaming))
        {
            name = renameField(group.name);
        }
        else
        {
            Label label = new Label(group.name);
            label.getStyleClass().add("sg-folder-name");
            name = label;
        }
        HBox.setHgrow(name, Priority.ALWAYS);
        Label count = new Label(Integer.toString(group.classes.size()));
        count.getStyleClass().add("sg-count");
        HBox header = new HBox(chevron, SuperIcons.icon(SuperIcons.FOLDER, 16), name, Widgets.spacer(), count);
        header.getStyleClass().addAll("sg-row", "sg-folder-row");
        // Inline, because the stylesheet's padding would override setPadding():
        header.setStyle("-fx-padding: 0 8 0 4;");
        header.setFocusTraversable(true);
        header.setAccessibleRole(AccessibleRole.BUTTON);
        header.setAccessibleText(group.name + " folder, " + group.classes.size() + " classes, "
                + (group.open ? "expanded" : "collapsed"));
        // A click opens or closes the folder; renaming is on the context menu and F2
        // (a double-click would toggle the folder first).
        header.setOnMouseClicked(e -> {
            // isStillSincePress: the release that ends a drag is not a click.
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress() && !group.name.equals(renaming))
            {
                folders.setOpen(group.name, !group.open);
            }
        });
        header.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)
            {
                folders.setOpen(group.name, !group.open);
                e.consume();
            }
            else if (e.getCode() == KeyCode.F2)
            {
                renaming = group.name;
                rebuild();
                e.consume();
            }
        });
        MenuItem rename = new MenuItem("Rename Folder...");
        rename.setOnAction(e -> {
            renaming = group.name;
            rebuild();
        });
        MenuItem delete = new MenuItem("Delete Folder (keeps its classes)");
        delete.setOnAction(e -> folders.deleteFolder(group.name));
        ContextMenu menu = new ContextMenu(rename, delete);
        header.setOnContextMenuRequested(e -> menu.show(header, e.getScreenX(), e.getScreenY()));
        box.getChildren().add(header);

        if (group.open)
        {
            for (ClassEntry entry : group.classes)
            {
                box.getChildren().add(classRow(entry, 1, true));
            }
            if (group.classes.isEmpty())
            {
                Label empty = new Label("Drag classes here");
                empty.getStyleClass().add("sg-empty-folder");
                box.getChildren().add(empty);
            }
        }
        makeDropTarget(box, group.name);
        return box;
    }

    private TextField renameField(String folder)
    {
        TextField field = new TextField(folder);
        field.getStyleClass().add("sg-rename");
        field.setPrefColumnCount(12);
        Tooltip problem = new Tooltip();
        JavaFXUtil.addChangeListenerPlatform(field.textProperty(), now -> {
            String message = folders.checkName(now, folder);
            field.getStyleClass().remove("sg-invalid");
            if (message != null)
            {
                field.getStyleClass().add("sg-invalid");
                problem.setText(message);
                field.setTooltip(problem);
            }
            else
            {
                field.setTooltip(null);
            }
        });
        Runnable commit = () -> {
            if (renaming == null)
            {
                return;
            }
            renaming = null;
            if (folders.hasFolder(folder) && folders.checkName(field.getText(), folder) == null)
            {
                // Rebuilds through the folders listener:
                folders.renameFolder(folder, field.getText());
            }
            rebuild();
        };
        field.setOnAction(e -> commit.run());
        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)
            {
                renaming = null;
                rebuild();
                e.consume();
            }
        });
        JavaFXUtil.addChangeListenerPlatform(field.focusedProperty(), is -> {
            if (!is)
            {
                commit.run();
            }
        });
        JavaFXUtil.runAfterCurrent(() -> {
            field.requestFocus();
            field.selectAll();
        });
        return field;
    }

    private Node classRow(ClassEntry entry, int depth, boolean showSuper)
    {
        String name = entry.getName();
        Label label = new Label(name);
        HBox row = new HBox(Widgets.tile(entry, 20), label);
        if (showSuper && entry.getSuperName() != null)
        {
            Label sup = new Label(entry.getSuperName());
            sup.getStyleClass().add("sg-sup");
            row.getChildren().addAll(Widgets.spacer(), sup);
        }
        row.getStyleClass().add("sg-row");
        if (name.equals(selectedClass.get()))
        {
            row.getStyleClass().add("sg-selected");
        }
        if (name.equals(dragging))
        {
            row.getStyleClass().add("sg-dragging");
        }
        // Inline, because the stylesheet's padding would override setPadding():
        row.setStyle("-fx-padding: 0 8 0 " + (8 + depth * 18) + ";");
        row.setFocusTraversable(true);
        row.setAccessibleRole(AccessibleRole.BUTTON);
        row.setAccessibleText(name + (entry.getSuperName() == null ? "" : ", extends " + entry.getSuperName()));
        Tooltip.install(row, new Tooltip(view.get() == View.FOLDERS
                ? "Double-click to edit. Drag onto a folder to move." : "Double-click to edit."));
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress())
            {
                selectedClass.set(name);
                if (e.getClickCount() == 2)
                {
                    onOpenClass.accept(name);
                }
            }
        });
        row.setOnContextMenuRequested(e -> {
            selectedClass.set(name);
            onShowClassMenu.showMenu(name, row, e.getScreenX(), e.getScreenY());
        });
        row.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
            {
                onOpenClass.accept(name);
                e.consume();
            }
            else if (e.getCode() == KeyCode.SPACE)
            {
                selectedClass.set(name);
                e.consume();
            }
        });
        if (view.get() == View.FOLDERS)
        {
            row.setOnDragDetected(e -> {
                Dragboard board = row.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(DRAG_PREFIX + name);
                board.setContent(content);
                board.setDragView(row.snapshot(null, null));
                dragging = name;
                row.getStyleClass().add("sg-dragging");
                e.consume();
            });
            row.setOnDragDone(e -> {
                dragging = null;
                rebuild();
            });
        }
        return row;
    }

    private void makeDropTarget(Node target, String folder)
    {
        target.setOnDragOver(e -> {
            if (draggedClass(e) != null)
            {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        target.setOnDragEntered(e -> {
            if (draggedClass(e) != null && !target.getStyleClass().contains("sg-drop"))
            {
                target.getStyleClass().add("sg-drop");
            }
        });
        target.setOnDragExited(e -> target.getStyleClass().remove("sg-drop"));
        target.setOnDragDropped(e -> {
            String cls = draggedClass(e);
            if (cls != null)
            {
                dragging = null;
                if (!folder.isEmpty())
                {
                    folders.setOpen(folder, true);
                }
                folders.setFolder(cls, folder);
                e.setDropCompleted(true);
            }
            e.consume();
        });
    }

    private static String draggedClass(DragEvent e)
    {
        Dragboard board = e.getDragboard();
        if (board.hasString() && board.getString().startsWith(DRAG_PREFIX))
        {
            return board.getString().substring(DRAG_PREFIX.length());
        }
        return null;
    }
}
