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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private static final class Line
    {
        final String text;
        final Kind kind;

        Line(String text, Kind kind)
        {
            this.text = text;
            this.kind = kind;
        }
    }

    private final ObservableList<Line> lines = FXCollections.observableArrayList();
    private final ListView<Line> listView = new ListView<>(lines);
    private final VBox problemsBox = new VBox();
    private final Label badge = new Label("0");
    private final ToggleButton outputTab = new ToggleButton("Output");
    private final ToggleButton problemsTab = new ToggleButton("Problems");
    private final ReadOnlyStringWrapper lastLine = new ReadOnlyStringWrapper("Nothing printed yet");
    private final Label placeholder = new Label("Nothing printed yet. System.out.println shows up here.");
    private final ReadOnlyStringWrapper problemSummary = new ReadOnlyStringWrapper("Problems 0");
    private Runnable onCollapse = () -> {};

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

        HBox header = new HBox(outputTab, problemsTab, Widgets.spacer(),
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
        StackPane content = new StackPane(listView, problemsBox);
        VBox.setVgrow(content, Priority.ALWAYS);
        getChildren().addAll(header, content);
        setProblems(List.of(), 0);
        showTab();
    }

    public void setOnCollapse(Runnable action)
    {
        onCollapse = action;
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
        lines.clear();
        lastLine.set("Nothing printed yet");
    }

    /**
     * Show compile problems; an empty list shows "No problems. All N classes
     * compiled."
     */
    public void setProblems(List<String> problems, int classCount)
    {
        problemsBox.getChildren().clear();
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
            for (String problem : problems)
            {
                Label label = new Label(problem);
                label.getStyleClass().add("sg-problem");
                label.setWrapText(true);
                problemsBox.getChildren().add(label);
            }
        }
        badge.setText(Integer.toString(problems.size()));
        badge.getStyleClass().remove("sg-error");
        if (!problems.isEmpty())
        {
            badge.getStyleClass().add("sg-error");
        }
        problemSummary.set("Problems " + problems.size());
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
        problemsBox.setVisible(!output);
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
