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
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * The SuperGreenfoot IDE's bottom panel: what the scenario prints (Output) and
 * compile errors (Problems).
 */
@OnThread(Tag.FXPlatform)
public class OutputPane extends VBox
{
    static final double HEIGHT = 200;
    /** Oldest lines are dropped beyond this, so a chatty act() can't eat memory. */
    static final int MAX_LINES = 5000;

    /** One line of output, and how to colour it. */
    @OnThread(Tag.Any)
    public enum Kind { PRINTED, CALL, ERROR }

    @OnThread(Tag.Any)
    static final class Line
    {
        final String text;
        final Kind kind;

        Line(String text, Kind kind)
        {
            this.text = text;
            this.kind = kind;
        }
    }

    /** A compile error or warning shown in the Problems tab. */
    @OnThread(Tag.FXPlatform)
    public static final class Problem
    {
        /** e.g. "Player.java:12" */
        public final String location;
        public final String message;
        public final boolean isError;
        /** Opens the class at the problem; may be null. */
        public final Runnable onOpen;

        public Problem(String location, String message, boolean isError, Runnable onOpen)
        {
            this.location = location;
            this.message = message;
            this.isError = isError;
            this.onOpen = onOpen;
        }
    }

    private final ObservableList<Line> lines = FXCollections.observableArrayList();
    private final ListView<Line> listView = new ListView<>(lines);
    private final VBox problemsBox = new VBox();
    private final ScrollPane problemsScroll;
    private final Label badge = new Label("0");
    private final ToggleButton outputTab = new ToggleButton("Output");
    private final ToggleButton problemsTab = new ToggleButton("Problems");
    private final ReadOnlyStringWrapper lastLine = new ReadOnlyStringWrapper("Nothing printed yet");
    private final Label placeholder = new Label("Nothing printed yet. System.out.println shows up here.");
    private final ReadOnlyStringWrapper problemSummary = new ReadOnlyStringWrapper("Problems 0");
    private Runnable onCollapse = () -> {};
    private Runnable onOpenTerminal = null;
    private final Button terminalButton;
    /** Whether the newest line has not ended yet (printed output arrives in chunks). */
    private boolean lastLineOpen = false;

    public OutputPane()
    {
        getStyleClass().add("sg-bottom");
        setMinHeight(HEIGHT);
        setPrefHeight(HEIGHT);
        setMaxHeight(HEIGHT);

        ToggleGroup tabs = new ToggleGroup();
        for (ToggleButton tab : new ToggleButton[] {outputTab, problemsTab})
        {
            tab.getStyleClass().add("sg-ptab");
            tab.setToggleGroup(tabs);
            // One tab always stays selected:
            tab.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (tab.isSelected())
                {
                    e.consume();
                }
            });
        }
        badge.getStyleClass().add("sg-badge");
        problemsTab.setGraphic(badge);
        outputTab.setSelected(true);
        JavaFXUtil.addChangeListenerPlatform(tabs.selectedToggleProperty(), now -> showTab());

        terminalButton = Widgets.iconButton(SuperIcons.TERMINAL,
                "Open the terminal window (for keyboard input with System.in)", 15, () -> {
                    if (onOpenTerminal != null)
                    {
                        onOpenTerminal.run();
                    }
                });
        terminalButton.setVisible(false);
        terminalButton.managedProperty().bind(terminalButton.visibleProperty());
        HBox header = new HBox(outputTab, problemsTab, Widgets.spacer(), terminalButton,
                Widgets.iconButton(SuperIcons.CLEAR, "Clear the output", 15, this::clear),
                Widgets.iconButton(SuperIcons.CHEVRON_DOWN, "Collapse the output panel", 16, () -> onCollapse.run()));
        header.getStyleClass().add("sg-bottom-header");

        listView.getStyleClass().add("sg-output");
        listView.setFocusTraversable(true);
        listView.setPlaceholder(placeholder);
        listView.setCellFactory(v -> new ListCell<>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            protected void updateItem(Line item, boolean empty)
            {
                super.updateItem(item, empty);
                getStyleClass().removeAll("sg-call", "sg-err");
                if (empty || item == null)
                {
                    setText(null);
                }
                else
                {
                    setText(item.text);
                    if (item.kind == Kind.CALL)
                    {
                        getStyleClass().add("sg-call");
                    }
                    else if (item.kind == Kind.ERROR)
                    {
                        getStyleClass().add("sg-err");
                    }
                }
            }
        });
        problemsBox.getStyleClass().add("sg-problems");
        problemsBox.setFillWidth(true);
        problemsScroll = new ScrollPane(problemsBox);
        problemsScroll.getStyleClass().add("sg-scroll");
        problemsScroll.setFitToWidth(true);
        problemsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        StackPane content = new StackPane(listView, problemsScroll);
        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().addAll(header, content);
        setProblems(List.of(), 0);
        showTab();
    }

    public void setOnCollapse(Runnable action)
    {
        onCollapse = action;
    }

    /** What the "open the terminal window" button does; null hides the button. */
    public void setOnOpenTerminal(Runnable action)
    {
        onOpenTerminal = action;
        terminalButton.setVisible(action != null);
    }

    /** A line the scenario printed. */
    /**
     * The text shown while there is no output (for example, where output goes instead).
     */
    public void setPlaceholderText(String text)
    {
        placeholder.setText(text);
    }

    public void appendOutput(String text)
    {
        append(text, Kind.PRINTED);
    }

    /**
     * Text the scenario printed (System.out, or System.err when isError). It arrives
     * in chunks that need not end at a line break, so an unfinished line is continued
     * by the next chunk of the same kind.
     */
    public void appendPrinted(String chunk, boolean isError)
    {
        lastLineOpen = addChunk(lines, lastLineOpen, chunk, isError ? Kind.ERROR : Kind.PRINTED);
        if (lines.size() > MAX_LINES)
        {
            lines.remove(0, lines.size() - MAX_LINES);
        }
        if (!lines.isEmpty())
        {
            lastLine.set(lines.get(lines.size() - 1).text);
            listView.scrollTo(lines.size() - 1);
        }
    }

    /**
     * Add a chunk of printed text to the lines.  If the last line is unfinished (open)
     * and of the same kind, the chunk's first part continues it.  Returns whether the
     * last line is unfinished afterwards (the chunk did not end with a line break).
     */
    @OnThread(Tag.Any)
    static boolean addChunk(List<Line> into, boolean wasOpen, String chunk, Kind kind)
    {
        String[] parts = chunk.split("\n", -1);
        boolean open = wasOpen;
        for (int i = 0; i < parts.length; i++)
        {
            String part = parts[i].replace("\r", "");
            boolean last = i == parts.length - 1;
            if (last && part.isEmpty())
            {
                // The chunk ended with a line break:
                return false;
            }
            if (i == 0 && open && !into.isEmpty() && into.get(into.size() - 1).kind == kind)
            {
                Line old = into.get(into.size() - 1);
                into.set(into.size() - 1, new Line(old.text + part, kind));
            }
            else
            {
                into.add(new Line(part, kind));
            }
            open = last;
        }
        return open;
    }

    /** A method call the user made from the IDE, e.g. "player.move(10.0)". */
    public void appendCall(String text)
    {
        append("> " + text, Kind.CALL);
    }

    /** An error line (e.g. an exception trace). */
    public void appendError(String text)
    {
        append(text, Kind.ERROR);
    }

    private void append(String text, Kind kind)
    {
        lastLineOpen = false;
        lines.add(new Line(text, kind));
        if (lines.size() > MAX_LINES)
        {
            lines.remove(0, lines.size() - MAX_LINES);
        }
        lastLine.set(text);
        listView.scrollTo(lines.size() - 1);
    }

    public void clear()
    {
        lastLineOpen = false;
        lines.clear();
        lastLine.set("Nothing printed yet");
    }

    /**
     * Show compile problems; an empty list shows "No problems. All N classes
     * compiled."
     */
    public void setProblems(List<String> problems, int classCount)
    {
        List<Problem> list = new java.util.ArrayList<>();
        for (String problem : problems)
        {
            list.add(new Problem(null, problem, true, null));
        }
        setProblemList(list, classCount);
    }

    /**
     * Show compile problems (errors and warnings); an empty list shows "No problems.
     * All N classes compiled."  The badge counts the errors.
     */
    public void setProblemList(List<Problem> problems, int classCount)
    {
        problemsBox.getChildren().clear();
        int errors = (int) problems.stream().filter(p -> p.isError).count();
        if (problems.isEmpty())
        {
            HBox ok = new HBox(8, SuperIcons.icon(SuperIcons.CHECK, 14),
                    new Label("No problems. All " + classCount + " classes compiled."));
            ok.getStyleClass().add("sg-problem-ok");
            ok.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            SuperIcons.shapeOf((StackPane) ok.getChildren().get(0)).setStyle("-fx-stroke: -sg-accent; -fx-stroke-width: 2.2;");
            problemsBox.getChildren().add(ok);
        }
        else
        {
            for (Problem problem : problems)
            {
                problemsBox.getChildren().add(problemRow(problem));
            }
        }
        badge.setText(Integer.toString(errors));
        badge.getStyleClass().remove("sg-error");
        if (errors > 0)
        {
            badge.getStyleClass().add("sg-error");
        }
        problemSummary.set("Problems " + errors);
    }

    private Node problemRow(Problem problem)
    {
        Label message = new Label(problem.message);
        message.getStyleClass().add(problem.isError ? "sg-problem" : "sg-problem-warning");
        message.setWrapText(true);
        message.setMinHeight(Region.USE_PREF_SIZE);
        if (problem.location == null && problem.onOpen == null)
        {
            return message;
        }
        Label location = new Label(problem.location == null ? "" : problem.location);
        location.getStyleClass().add("sg-problem-location");
        location.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, location, message);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        HBox.setHgrow(message, Priority.ALWAYS);
        Button button = new Button();
        button.setGraphic(row);
        button.getStyleClass().add("sg-problem-row");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        button.setFocusTraversable(true);
        if (problem.onOpen != null)
        {
            button.setOnAction(e -> problem.onOpen.run());
            button.setTooltip(new javafx.scene.control.Tooltip("Open the class at this line"));
        }
        return button;
    }

    public void showProblems()
    {
        problemsTab.setSelected(true);
    }

    public void showOutput()
    {
        outputTab.setSelected(true);
    }

    private void showTab()
    {
        boolean output = outputTab.isSelected();
        listView.setVisible(output);
        problemsScroll.setVisible(!output);
    }

    /** The newest line, for the collapsed bar. */
    public ReadOnlyStringProperty lastLineProperty()
    {
        return lastLine.getReadOnlyProperty();
    }

    /** e.g. "Problems 0", for the collapsed bar. */
    public ReadOnlyStringProperty problemSummaryProperty()
    {
        return problemSummary.getReadOnlyProperty();
    }
}
