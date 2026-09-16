/*
 This file is part of the Greenfoot program.
 Copyright (C) 2005-2009,2010,2011,2013,2015,2016,2018,2019,2021  Poul Henriksen and Michael Kolling
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
package greenfoot.guifx.export;

import static greenfoot.export.Exporter.ExportFunction;

import bluej.Config;
import greenfoot.export.NativePackager;
import greenfoot.export.mygame.ExportInfo;
import greenfoot.export.mygame.ScenarioInfo;

import java.io.File;
import java.util.List;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Export tab for a standalone application: a single executable jar containing
 * the scenario and the SuperGreenfoot runtime, runnable with "java -jar" on
 * any JDK 21 or later (no JavaFX needed), and optionally a native app built
 * with jpackage (no Java needed), signed and notarized on macOS.
 */
@OnThread(Tag.FXPlatform)
public class ExportAppTab extends ExportLocalTab
{
    private static final String PREF_SIGN = "supergreenfoot.export.macSigningName";
    private static final String PREF_NOTARIZE = "supergreenfoot.export.notarizeProfile";
    private static final String PREF_NATIVE = "supergreenfoot.export.nativeApp";

    // Created in buildContentPane, which the superclass constructor calls before
    // field initialisers would run.
    private CheckBox nativeApp;
    private ComboBox<String> nativeKind;
    private ComboBox<String> signingIdentity;
    private TextField notarizeProfile;
    private Label nativeStatus;

    public ExportAppTab(Window parent, ScenarioInfo scenarioInfo, String scenarioName, File defaultExportDir)
    {
        super(parent, scenarioInfo, scenarioName, defaultExportDir, "app", ".jar");
    }

    @Override
    public ExportFunction getFunction()
    {
        return ExportFunction.APP;
    }

    @Override
    protected void buildContentPane(final File targetFile)
    {
        super.buildContentPane(targetFile);
        Label runHint = new Label(Config.getString("export.app.runHint"));
        runHint.setWrapText(true);

        nativeApp = new CheckBox(Config.getString("export.app.native"));
        nativeKind = new ComboBox<>();
        signingIdentity = new ComboBox<>();
        notarizeProfile = new TextField();
        nativeStatus = new Label();

        boolean haveJpackage = NativePackager.findJpackage() != null;
        nativeApp.setSelected(haveJpackage && "true".equals(Config.getPropString(PREF_NATIVE, "false")));
        nativeApp.setDisable(!haveJpackage);
        nativeStatus.setWrapText(true);
        nativeStatus.setText(haveJpackage ? Config.getString("export.app.nativeHint")
                                          : Config.getString("export.app.noJpackage"));

        if (NativePackager.isMac()) {
            nativeKind.getItems().addAll("APP_IMAGE", "DMG");
        }
        else if (NativePackager.isWindows()) {
            nativeKind.getItems().addAll("APP_IMAGE", "MSI");
        }
        else {
            nativeKind.getItems().addAll("APP_IMAGE");
        }
        nativeKind.getSelectionModel().selectFirst();

        VBox nativeBox = new VBox(4, nativeApp, nativeStatus);
        HBox kindRow = new HBox(8, new Label(Config.getString("export.app.nativeKind")), nativeKind);
        nativeBox.getChildren().add(kindRow);

        if (NativePackager.isMac()) {
            List<String> ids = NativePackager.findMacSigningIdentities();
            signingIdentity.getItems().add("");
            signingIdentity.getItems().addAll(ids);
            String remembered = Config.getPropString(PREF_SIGN, "");
            signingIdentity.getSelectionModel().select(ids.contains(remembered) ? remembered : (ids.isEmpty() ? "" : ids.get(0)));
            notarizeProfile.setText(Config.getPropString(PREF_NOTARIZE, ""));
            notarizeProfile.setPromptText("SuperGreenfoot");
            notarizeProfile.setPrefColumnCount(14);
            Label signLabel = new Label(Config.getString("export.app.sign"));
            Label notarizeLabel = new Label(Config.getString("export.app.notarize"));
            HBox signRow = new HBox(8, signLabel, signingIdentity, notarizeLabel, notarizeProfile);
            nativeBox.getChildren().add(signRow);
            Label signHint = new Label(ids.isEmpty() ? Config.getString("export.app.noIdentity")
                                                     : Config.getString("export.app.signHint"));
            signHint.setWrapText(true);
            nativeBox.getChildren().add(signHint);
            signRow.disableProperty().bind(nativeApp.selectedProperty().not());
        }
        kindRow.disableProperty().bind(nativeApp.selectedProperty().not());

        ((Pane) getContent()).getChildren().addAll(runHint, lockScenario, hideControls, nativeBox);
    }

    @Override
    protected void updateInfoFromFields()
    {
        super.updateInfoFromFields();
        scenarioInfo.setLocked(isLockScenario());
        scenarioInfo.setHideControls(isHideControls());
        if (nativeApp == null) {
            return;
        }
        Config.putPropString(PREF_NATIVE, Boolean.toString(nativeApp.isSelected()));
        if (NativePackager.isMac()) {
            String id = signingIdentity.getValue() == null ? "" : signingIdentity.getValue();
            Config.putPropString(PREF_SIGN, id);
            Config.putPropString(PREF_NOTARIZE, notarizeProfile.getText().trim());
        }
    }

    @Override
    protected ExportInfo getExportInfo()
    {
        ExportInfo info = super.getExportInfo();
        info.setLocked(isLockScenario());
        info.setHideControls(isHideControls());
        info.setNativeApp(nativeApp != null && nativeApp.isSelected());
        info.setNativeKind(nativeKind == null ? "APP_IMAGE" : nativeKind.getValue());
        if (NativePackager.isMac() && signingIdentity != null) {
            String id = signingIdentity.getValue();
            info.setMacSigningName(id == null || id.isEmpty() ? null : id);
            String prof = notarizeProfile.getText().trim();
            info.setNotarizeProfile(prof.isEmpty() ? null : prof);
        }
        return info;
    }
}
