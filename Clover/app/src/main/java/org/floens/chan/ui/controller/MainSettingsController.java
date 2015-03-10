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
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.preferences.BooleanSettingView;
import org.floens.chan.ui.preferences.LinkSettingView;
import org.floens.chan.ui.preferences.ListSettingView;
import org.floens.chan.ui.preferences.SettingView;
import org.floens.chan.ui.preferences.SettingsController;
import org.floens.chan.ui.preferences.SettingsGroup;
import org.floens.chan.ui.preferences.StringSettingView;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainSettingsController extends SettingsController implements ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int ADVANCED_SETTINGS = 1;
    private SettingView imageAutoLoadView;
    private SettingView videoAutoLoadView;

    private int clickCount;
    private SettingView developerView;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = s(R.string.settings_screen);
        navigationItem.menu = new ToolbarMenu(context);
        navigationItem.createOverflow(context, this, Arrays.asList(
                new FloatingMenuItem(ADVANCED_SETTINGS, s(R.string.settings_screen_advanced))
        ));

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        if (!ChanSettings.developer.get()) {
            developerView.view.getLayoutParams().height = 0;
        }
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        if (((Integer) item.getId()) == ADVANCED_SETTINGS) {
            navigationController.pushController(new AdvancedSettingsController(context));
        }
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == imageAutoLoadView) {
            videoAutoLoadView.setEnabled(ChanSettings.imageAutoLoad.get());
        }
    }

    private void populatePreferences() {
        // General group
        SettingsGroup general = new SettingsGroup(s(R.string.settings_group_general));
        general.add(new LinkSettingView(this, s(R.string.settings_board_edit), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        }));

        general.add(new LinkSettingView(this, s(R.string.settings_watch), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new WatchSettingsController(context));
            }
        }));

        general.add(new LinkSettingView(this, s(R.string.settings_pass), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        }));

        groups.add(general);

        // Browsing group
        SettingsGroup browsing = new SettingsGroup(s(R.string.settings_group_browsing));

        browsing.add(new ListSettingView(this, ChanSettings.theme, s(R.string.setting_theme), new ListSettingView.Item[]{
                new ListSettingView.Item(s(R.string.setting_theme_light), "light"),
                new ListSettingView.Item(s(R.string.setting_theme_dark), "dark"),
                new ListSettingView.Item(s(R.string.setting_theme_black), "black")
        }));

        List<ListSettingView.Item> fontSizes = new ArrayList<>();
        for (int size = 10; size <= 19; size++) {
            String name = size + (String.valueOf(size).equals(ChanSettings.fontSize.getDefault()) ? " " + s(R.string.setting_font_size_default) : "");
            fontSizes.add(new ListSettingView.Item(name, String.valueOf(size)));
        }

        browsing.add(new ListSettingView(this, ChanSettings.fontSize, s(R.string.setting_font_size), fontSizes.toArray(new ListSettingView.Item[fontSizes.size()])));

        browsing.add(new BooleanSettingView(this, ChanSettings.openLinkConfirmation, s(R.string.setting_open_link_confirmation), null));
        browsing.add(new BooleanSettingView(this, ChanSettings.autoRefreshThread, s(R.string.setting_auto_refresh_thread), null));
        imageAutoLoadView = browsing.add(new BooleanSettingView(this, ChanSettings.imageAutoLoad, s(R.string.setting_image_auto_load), null));
        videoAutoLoadView = browsing.add(new BooleanSettingView(this, ChanSettings.videoAutoLoad, s(R.string.setting_video_auto_load), null));
        browsing.add(new BooleanSettingView(this, ChanSettings.videoOpenExternal, s(R.string.setting_video_open_external), s(R.string.setting_video_open_external_description)));

        groups.add(browsing);

        // Posting group
        SettingsGroup posting = new SettingsGroup(s(R.string.settings_group_posting));
        posting.add(new StringSettingView(this, ChanSettings.postDefaultName, s(R.string.setting_post_default_name), s(R.string.setting_post_default_name)));
        posting.add(new BooleanSettingView(this, ChanSettings.postPinThread, s(R.string.setting_post_pin), null));

        groups.add(posting);

        // About group
        SettingsGroup about = new SettingsGroup(s(R.string.settings_group_about));
        about.add(new LinkSettingView(this, s(R.string.settings_about_licenses), s(R.string.settings_about_licences_description), new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        }));

        String version = "";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        about.add(new LinkSettingView(this, s(R.string.app_name), version, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((++clickCount) % 5 == 0) {
                    boolean developer = !ChanSettings.developer.get();

                    ChanSettings.developer.set(developer);

                    Toast.makeText(context, (developer ? "Enabled" : "Disabled") + " developer options", Toast.LENGTH_LONG).show();

                    AndroidUtils.animateHeight(developerView.view, developer);
                }
            }
        }));

        developerView = about.add(new LinkSettingView(this, s(R.string.settings_developer), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        }));

        groups.add(about);
    }

    private String s(int id) {
        return string(id);
    }
}
