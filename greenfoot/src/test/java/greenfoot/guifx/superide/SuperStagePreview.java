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

import bluej.Config;
import greenfoot.guifx.superide.folders.ClassFolders;
import javafx.application.Application;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Dev only: opens the SuperGreenfoot IDE window shell with the approved
 * mockup's sample scenario, without a project or debug VM.
 *
 * <pre>./gradlew :greenfoot:runSuperIdePreview -PpreviewArgs="dark collapsed inherit tree class=Enemy editor pixel"</pre>
 *
 * The first argument (added by the Gradle task) is the lib directory.
 */
public class SuperStagePreview
{
    public static void main(String[] args)
    {
        Application.launch(PreviewApp.class, args);
    }

    public static class PreviewApp extends Application
    {
        @Override
        public void start(Stage ignored)
        {
            List<String> args = new ArrayList<>(getParameters().getRaw());
            File libDir = new File(args.remove(0));
            // The theme sets the editor font through BlueJ's preferences, which need
            // Config. A throwaway user home keeps the real preferences untouched.
            Properties props = new Properties();
            try
            {
                props.setProperty("bluej.userHome", Files.createTempDirectory("sg-preview").toString());
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
            Config.initialise(libDir, props, true);
            SuperTheme.init(libDir);
            SuperTheme.darkProperty().set(args.contains("dark"));
            SuperTheme.activate();

            SuperStage stage = new SuperStage();
            stage.setScenarioName("CoinQuest");
            ContextMenu menu = new ContextMenu();
            for (String item : Arrays.asList("New Scenario...", "Open...", "Open Recent", "Save", "Save As...", "Close"))
            {
                menu.getItems().add(new MenuItem(item));
            }
            stage.setScenarioMenu(menu);

            List<ClassEntry> classes = new ArrayList<>();
            String[][] defs = {
                {"MyWorld", "greenfoot.World", "M", "#3F74A3", "#FFFFFF"},
                {"Level1", "MyWorld", "L1", "#356894", "#FFFFFF"},
                {"Level2", "MyWorld", "L2", "#356894", "#FFFFFF"},
                {"Player", "greenfoot.Actor", "P", "#E8A33D", "#2B2118"},
                {"Enemy", "greenfoot.Actor", "E", "#6E716A", "#FFFFFF"},
                {"Slime", "Enemy", "S", "#5DAF5A", "#10240F"},
                {"Bat", "Enemy", "B", "#7457A8", "#FFFFFF"},
                {"Coin", "greenfoot.Actor", "C", "#E9C23A", "#3A2C05"},
                {"Heart", "greenfoot.Actor", "H", "#C4443F", "#FFFFFF"},
                {"ScoreBar", "greenfoot.Actor", "Sb", "#3F5F80", "#FFFFFF"},
                {"HealthBar", "greenfoot.Actor", "Hb", "#A8433F", "#FFFFFF"},
                {"SuperTextBox", "greenfoot.Actor", "Tx", "#56625A", "#FFFFFF"},
                {"SuperWindow", "greenfoot.Actor", "Wn", "#56625A", "#FFFFFF"},
                {"Utility", null, "U", "#72746C", "#FFFFFF"},
            };
            for (String[] d : defs)
            {
                classes.add(new ClassEntry(d[0], d[1], tileImage(d[2], d[3], d[4])));
            }
            ClassFolders folders = new ClassFolders();
            String[][] folderDefs = {
                {"Worlds", "MyWorld", "Level1", "Level2"},
                {"Characters", "Player", "Enemy", "Slime", "Bat"},
                {"Pickups", "Coin", "Heart"},
                {"UI", "ScoreBar", "HealthBar"},
                {"Library", "SuperTextBox", "SuperWindow"},
            };
            for (String[] f : folderDefs)
            {
                folders.addFolder(f[0]);
                for (int i = 1; i < f.length; i++)
                {
                    folders.setFolder(f[i], f[0]);
                }
            }
            folders.setOpen("UI", false);
            folders.setOpen("Library", false);
            stage.setClassFolders(folders);
            stage.setClasses(classes);
            if (args.contains("inherit"))
            {
                stage.getClassBrowser().viewProperty().set(ClassBrowserPane.View.INHERITANCE);
            }
            if (args.contains("tree"))
            {
                stage.getClassBrowser().inheritanceInFoldersProperty().set(true);
            }

            ImageView world = new ImageView(worldImage());
            stage.setWorld(new StackPane(world), 960, 540, "Level1");
            if (args.contains("pixel"))
            {
                stage.getWorldHost().zoomProperty().set(WorldHost.Zoom.PIXEL_PERFECT);
            }

            for (String line : Arrays.asList("Level 1 started with 3 lives", "Score: 10", "Score: 20", "Score: 30"))
            {
                stage.appendOutput(line);
            }
            stage.getOutput().setProblems(List.of(), classes.size());

            String selected = args.stream().filter(a -> a.startsWith("class=")).map(a -> a.substring(6)).findFirst().orElse(null);
            if (selected != null)
            {
                stage.getClassBrowser().selectedClassProperty().set(selected);
                showClass(stage, classes, selected);
            }
            else
            {
                showPlayer(stage);
            }
            stage.getClassBrowser().selectedClassProperty().addListener((prop, old, now) -> {
                if (now != null)
                {
                    showClass(stage, classes, now);
                }
            });

            stage.runningProperty().addListener((prop, was, is) -> stage.appendOutput(is ? "Running" : "Paused"));
            stage.setOnRunPause(() -> stage.runningProperty().set(!stage.runningProperty().get()));
            stage.setOnAct(() -> stage.appendCall("act()"));
            stage.setOnReset(() -> stage.runningProperty().set(false));
            stage.setOnOpenClass(name -> openEditor(stage, name));
            if (args.contains("editor"))
            {
                openEditor(stage, "Player");
            }
            if (args.contains("collapsed"))
            {
                stage.leftOpenProperty().set(false);
                stage.rightOpenProperty().set(false);
                stage.bottomOpenProperty().set(false);
            }
            stage.show();
        }

        /** The preview's stand-in for the editor host: a plain tab pane with a World tab. */
        private static TabPane editorTabs;

        private static void openEditor(SuperStage stage, String name)
        {
            if (editorTabs == null)
            {
                editorTabs = new TabPane();
                editorTabs.getStyleClass().add("tabbed-editor");
                Tab world = new Tab();
                world.setGraphic(stage.makeWorldTabGraphic());
                world.setContent(stage.getWorldArea());
                world.setClosable(false);
                editorTabs.getTabs().add(world);
                StackPane host = new StackPane(editorTabs);
                host.getStyleClass().add("sg-editor-host");
                SuperTheme.installEditorHost(host);
                stage.setCentreContent(host);
            }
            for (Tab t : editorTabs.getTabs())
            {
                if (name.equals(t.getUserData()))
                {
                    editorTabs.getSelectionModel().select(t);
                    return;
                }
            }
            Label placeholder = new Label("public class " + name + " ...\n\n(Real editors dock here in the IDE.)");
            placeholder.getStyleClass().add("sg-mono");
            Tab tab = new Tab();
            tab.setGraphic(new Label(name + ".java"));
            tab.setContent(new StackPane(placeholder));
            tab.setUserData(name);
            tab.getStyleClass().add("moe-tab");
            editorTabs.getTabs().add(tab);
            editorTabs.getSelectionModel().select(tab);
        }

        private static void showPlayer(SuperStage stage)
        {
            InspectorPane.ActorDetails a = new InspectorPane.ActorDetails();
            a.variableName = "player";
            a.className = "Player";
            a.image = tileImage("P", "#E8A33D", "#2B2118");
            a.worldName = "Level1";
            a.location = "(300, 431)";
            a.preciseLocation = "(300.00, 431.00)";
            a.rotation = "0.0°";
            a.z = "2.0";
            a.ownMethods.add(new InspectorPane.MethodEntry("act()", "void", () -> stage.appendCall("player.act()")));
            a.ownMethods.add(new InspectorPane.MethodEntry("jump()", "void", () -> stage.appendCall("player.jump()")));
            for (String m : Arrays.asList("move(double)", "turn(double)", "setLocation(double, double)", "setRotation(double)", "setZ(double)"))
            {
                a.inheritedMethods.add(new InspectorPane.MethodEntry(m, "void", () -> stage.appendCall("player." + m)));
            }
            a.onInspect = () -> {};
            a.onRemove = () -> {};
            stage.showActor(a);
        }

        private static void showClass(SuperStage stage, List<ClassEntry> classes, String name)
        {
            ClassEntry entry = classes.stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
            if (entry == null)
            {
                return;
            }
            InspectorPane.ClassDetails d = new InspectorPane.ClassDetails();
            d.name = name;
            d.superName = entry.getSuperName();
            d.image = entry.getImage();
            d.constructorLabel = "new " + name + "()";
            d.subclasses.addAll(ClassTreeModel.subclassesOf(name, classes));
            d.onOpenEditor = () -> openEditor(stage, name);
            d.onConstruct = () -> stage.appendCall("new " + name + "()");
            d.onSetImage = () -> {};
            d.onDuplicate = () -> {};
            d.onDelete = () -> {};
            stage.showClass(d);
        }

        /** A lettered, coloured square standing in for a class image. */
        private static Image tileImage(String letters, String bg, String ink)
        {
            Canvas canvas = new Canvas(40, 40);
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setFill(Color.web(bg));
            g.fillRect(0, 0, 40, 40);
            g.setFill(Color.web(ink));
            g.setFont(Font.font(SuperTheme.SANS_SEMIBOLD, letters.length() > 1 ? 16 : 20));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(letters, 20, letters.length() > 1 ? 26 : 27);
            return canvas.snapshot(null, null);
        }

        /** The mockup's Level1 scene, drawn in code. */
        private static Image worldImage()
        {
            Canvas canvas = new Canvas(960, 540);
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setFill(Color.web("#CDE7F2"));
            g.fillRect(0, 0, 960, 540);
            g.setFill(Color.web("#F5D77E"));
            g.fillOval(824, 56, 72, 72);
            g.setFill(Color.web("#FFFFFF", 0.85));
            g.fillRoundRect(96, 84, 120, 30, 30, 30);
            g.fillRoundRect(130, 68, 64, 30, 30, 30);
            g.setFill(Color.web("#FFFFFF", 0.75));
            g.fillRoundRect(520, 120, 150, 28, 28, 28);
            g.setFill(Color.web("#A8D5AF"));
            g.beginPath();
            g.moveTo(0, 380);
            g.bezierCurveTo(120, 300, 220, 300, 340, 360);
            g.bezierCurveTo(450, 410, 560, 300, 700, 320);
            g.bezierCurveTo(820, 338, 900, 300, 960, 320);
            g.lineTo(960, 540);
            g.lineTo(0, 540);
            g.closePath();
            g.fill();
            g.setFill(Color.web("#8CC59A"));
            g.beginPath();
            g.moveTo(0, 430);
            g.bezierCurveTo(140, 380, 260, 400, 380, 420);
            g.bezierCurveTo(520, 444, 640, 390, 780, 410);
            g.bezierCurveTo(860, 420, 920, 408, 960, 400);
            g.lineTo(960, 540);
            g.lineTo(0, 540);
            g.closePath();
            g.fill();
            g.setFill(Color.web("#7A5238"));
            g.fillRect(0, 462, 960, 78);
            g.setFill(Color.web("#62B36A"));
            g.fillRect(0, 462, 960, 14);
            g.setStroke(Color.web("#6A4630"));
            g.setLineWidth(2);
            for (int x = 48; x < 960; x += 48)
            {
                g.strokeLine(x, 476, x, 540);
            }
            g.setFill(Color.web("#7A5238"));
            g.fillRoundRect(380, 336, 200, 26, 8, 8);
            g.setFill(Color.web("#62B36A"));
            g.fillRoundRect(380, 336, 200, 9, 6, 6);
            for (int[] c : new int[][] {{420, 310}, {470, 310}, {520, 310}, {800, 420}})
            {
                g.setFill(Color.web("#F2C94C"));
                g.fillOval(c[0] - 11, c[1] - 11, 22, 22);
                g.setStroke(Color.web("#C99A1E"));
                g.setLineWidth(2.5);
                g.strokeOval(c[0] - 11, c[1] - 11, 22, 22);
                g.strokeLine(c[0], c[1] - 6, c[0], c[1] + 6);
            }
            // Slime
            g.setFill(Color.web("#5DAF5A"));
            g.fillArc(582, 426, 56, 72, 0, 180, ArcType.CHORD);
            g.setFill(Color.web("#10240F"));
            g.fillOval(596, 441, 8, 8);
            g.fillOval(614, 441, 8, 8);
            // Bat
            g.setFill(Color.web("#5A3F8C"));
            g.fillOval(684, 158, 36, 16);
            g.fillOval(720, 158, 36, 16);
            g.setFill(Color.web("#7457A8"));
            g.fillOval(708, 158, 24, 28);
            // Player
            g.setFill(Color.web("#E8A33D"));
            g.fillRoundRect(278, 404, 44, 54, 24, 24);
            g.setStroke(Color.web("#B87819"));
            g.setLineWidth(2.5);
            g.strokeRoundRect(278, 404, 44, 54, 24, 24);
            g.setFill(Color.web("#2B2118"));
            g.fillOval(287.5, 419.5, 9, 9);
            g.fillOval(304.5, 419.5, 9, 9);
            // Heart
            g.setFill(Color.web("#D9534F"));
            g.fillOval(146, 285, 16, 16);
            g.fillOval(158, 285, 16, 16);
            g.fillPolygon(new double[] {147, 173, 160}, new double[] {296, 296, 311}, 3);
            // Score
            g.setFill(Color.web("#141814", 0.62));
            g.fillRoundRect(16, 14, 176, 40, 16, 16);
            g.setFill(Color.WHITE);
            g.setFont(Font.font(SuperTheme.MONO_MEDIUM, 19));
            g.setTextAlign(TextAlignment.LEFT);
            g.fillText("SCORE 30", 32, 41);
            return canvas.snapshot(null, null);
        }
    }
}
