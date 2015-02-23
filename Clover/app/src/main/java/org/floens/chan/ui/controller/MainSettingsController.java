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

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.preferences.ChanPreferences;
import org.floens.chan.ui.preferences.LinkPreference;
import org.floens.chan.ui.preferences.ListPreference;
import org.floens.chan.ui.preferences.PreferenceGroup;
import org.floens.chan.ui.preferences.PreferenceItem;
import org.floens.chan.ui.preferences.PreferencesController;

public class MainSettingsController extends PreferencesController {
    private ListPreference theme;
    private LinkPreference link;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //TODO correct header colors, background, themeing

        navigationItem.title = context.getString(R.string.action_settings);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();
    }

    @Override
    public void onPreferenceChange(PreferenceItem item) {
        super.onPreferenceChange(item);

        if (item == theme) {
            link.setEnabled(((ListPreference)item).getPreference().get().equals("dark"));
        }
    }

    private void populatePreferences() {
        PreferenceGroup settings = new PreferenceGroup("Settings");

        ListPreference.Item[] themeItems = new ListPreference.Item[2];
        themeItems[0] = new ListPreference.Item<>("Light", "light");
        themeItems[1] = new ListPreference.Item<>("Dark", "dark");
        theme = new ListPreference(this, ChanPreferences.testTheme, "Theme", themeItems);
        settings.preferenceItems.add(theme);

//        BooleanPreference bool = new BooleanPreference(p, "A name", "akey", false);
//        settings.preferenceItems.add(bool);

        link = new LinkPreference(this, "A link", new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(context).setMessage("click").setPositiveButton(R.string.ok, null).show();
            }
        });
        settings.preferenceItems.add(link);

        groups.add(settings);

        /*PreferenceGroup posting = new PreferenceGroup("Posting");

        ListPreference.Item[] postItems = new ListPreference.Item[4];
        postItems[0] = new ListPreference.Item<>("Top", "one");
        postItems[1] = new ListPreference.Item<>("Top", "two");
        postItems[2] = new ListPreference.Item<>("Top", "three");
        postItems[3] = new ListPreference.Item<>("Top", "four");
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));
        posting.preferenceItems.add(new ListPreference(p, "Something", "something", postItems));

        groups.add(posting);*/
    }
}
