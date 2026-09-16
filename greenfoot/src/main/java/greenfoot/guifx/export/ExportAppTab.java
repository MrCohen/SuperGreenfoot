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
import greenfoot.export.mygame.ExportInfo;
import greenfoot.export.mygame.ScenarioInfo;

import java.io.File;

import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Export tab for a standalone application: a single executable jar containing
 * the scenario and the SuperGreenfoot runtime, runnable with "java -jar" on
 * any JDK 21 or later (no JavaFX needed). Restored from Greenfoot 3.8.1 and
 * simplified: the old JavaFX command-line instructions are gone.
 */
@OnThread(Tag.FXPlatform)
public class ExportAppTab extends ExportLocalTab
{
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
        ((Pane) getContent()).getChildren().addAll(runHint, lockScenario, hideControls);
    }

    @Override
    protected void updateInfoFromFields()
    {
        super.updateInfoFromFields();
        scenarioInfo.setLocked(isLockScenario());
        scenarioInfo.setHideControls(isHideControls());
    }

    @Override
    protected ExportInfo getExportInfo()
    {
        ExportInfo info = super.getExportInfo();
        info.setLocked(isLockScenario());
        info.setHideControls(isHideControls());
        return info;
    }
}
