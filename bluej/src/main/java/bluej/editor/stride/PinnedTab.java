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
package bluej.editor.stride;

import java.util.Collections;
import java.util.List;

import bluej.utility.javafx.FXPlatformRunnable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A tab that is always there in an embedded editor host and cannot be closed or moved
 * to another window: the SuperGreenfoot IDE's World tab.  It has no menus and never
 * shows the Stride catalogue.
 */
@OnThread(Tag.FXPlatform)
public class PinnedTab extends FXTab
{
    private final StringProperty title = new SimpleStringProperty("");
    private final FXPlatformRunnable onSelected;
    private final FXPlatformRunnable onUnselected;
    private final FXPlatformRunnable onShown;
    private FXTabbedEditor parent;

    /**
     * @param graphic      The tab header (must not be null: tab headers are found through it)
     * @param content      What the tab shows
     * @param onSelected   Run when the tab is selected while its window has focus, and when
     *                     its window gains focus while the tab is selected
     * @param onUnselected Run when the tab stops being selected, or its window loses focus
     * @param onShown      Run just after the tab is selected (e.g. to focus its content)
     */
    public PinnedTab(Node graphic, Node content, FXPlatformRunnable onSelected,
                     FXPlatformRunnable onUnselected, FXPlatformRunnable onShown)
    {
        super(false);
        this.onSelected = onSelected;
        this.onUnselected = onUnselected;
        this.onShown = onShown;
        setClosable(false);
        setGraphic(graphic);
        setContent(content);
        getStyleClass().add("pinned-tab");
    }

    /**
     * The title used for the window while this tab is selected.
     */
    public StringProperty titleProperty()
    {
        return title;
    }

    @Override
    void initialiseFX()
    {
        // Everything is set up by the constructor.
    }

    @Override
    void focusWhenShown()
    {
        if (onShown != null)
        {
            onShown.run();
        }
    }

    @Override
    List<Menu> getMenus()
    {
        return Collections.emptyList();
    }

    @Override
    void setParent(FXTabbedEditor parent, boolean partOfMove)
    {
        this.parent = parent;
    }

    @Override
    FXTabbedEditor getParent()
    {
        return parent;
    }

    @Override
    String getWebAddress()
    {
        return null;
    }

    @Override
    ObservableStringValue windowTitleProperty()
    {
        return title;
    }

    @Override
    public void notifySelected()
    {
        if (onSelected != null)
        {
            onSelected.run();
        }
    }

    @Override
    public void notifyUnselected()
    {
        if (onUnselected != null)
        {
            onUnselected.run();
        }
    }
}
