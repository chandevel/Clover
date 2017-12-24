/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.core.presenter.SiteSetupPresenter;
import org.floens.chan.core.site.Site;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;

public class SiteSetupController extends SettingsController implements SiteSetupPresenter.Callback {
    @Inject
    SiteSetupPresenter presenter;

    private Site site;
    private LinkSettingView boardsLink;

    public SiteSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getGraph().inject(this);

        // Navigation
        navigationItem.setTitle(R.string.settings_screen);
        navigationItem.title = context.getString(R.string.setup_site_title, site.name());

        // View binding
        view = inflateRes(R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);

        // Preferences
        populatePreferences();

        // Presenter
        presenter.create(this, site);

        buildPreferences();
    }

    public void setSite(Site site) {
        this.site = site;
    }

    @Override
    public void onShow() {
        super.onShow();
        presenter.show();
    }

    @Override
    public void setBoardCount(int boardCount) {
        String boardsString = context.getResources().getQuantityString(
                R.plurals.board, boardCount, boardCount);
        String descriptionText = context.getString(
                R.string.setup_site_boards_description, boardsString);
        boardsLink.setDescription(descriptionText);
    }

    private void populatePreferences() {
        SettingsGroup general = new SettingsGroup(R.string.setup_site_group_general);

        boardsLink = new LinkSettingView(
                this,
                context.getString(R.string.setup_site_boards),
                "",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BoardSetupController boardSetupController = new BoardSetupController(context);
                        boardSetupController.setSite(site);
                        navigationController.pushController(boardSetupController);
                    }
                });
        general.add(boardsLink);

        groups.add(general);
    }
}
