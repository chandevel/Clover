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

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.getString;

public class AppearanceSettingsController extends SettingsController {
    public AppearanceSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_appearance);

        setupLayout();

        populatePreferences();

        buildPreferences();
    }

    private void populatePreferences() {
        // Appearance group
        {
            SettingsGroup appearance = new SettingsGroup(R.string.settings_group_appearance);

            appearance.add(new LinkSettingView(this,
                    getString(R.string.setting_theme), theme().displayName,
                    v -> navigationController.pushController(
                            new ThemeSettingsController(context))));

            groups.add(appearance);
        }

        // Layout group
        {
            SettingsGroup layout = new SettingsGroup(R.string.settings_group_layout);

            setupLayoutModeSetting(layout);

            setupGridColumnsSetting(layout);

            requiresRestart.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.neverHideToolbar,
                    R.string.setting_never_hide_toolbar, 0)));

            requiresRestart.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.enableReplyFab,
                    R.string.setting_enable_reply_fab,
                    R.string.setting_enable_reply_fab_description)));

            requiresUiRefresh.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.accessibleInfo,
                    "Enable accessible post info",
                    "Enabling places info in the first post option menu")));
            groups.add(layout);
        }

        // Post group
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            setupFontSizeSetting(post);

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.fontCondensed,
                    R.string.setting_font_condensed,
                    R.string.setting_font_condensed_description)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFullDate,
                    R.string.setting_post_full_date, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFileInfo,
                    R.string.setting_post_file_info, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFilename,
                    R.string.setting_post_filename, 0)));

            groups.add(post);
        }
    }

    private void setupLayoutModeSetting(SettingsGroup layout) {
        List<ListSettingView.Item> layoutModes = new ArrayList<>();
        for (ChanSettings.LayoutMode mode : ChanSettings.LayoutMode.values()) {
            int name = 0;
            switch (mode) {
                case AUTO:
                    name = R.string.setting_layout_mode_auto;
                    break;
                case PHONE:
                    name = R.string.setting_layout_mode_phone;
                    break;
                case SLIDE:
                    name = R.string.setting_layout_mode_slide;
                    break;
                case SPLIT:
                    name = R.string.setting_layout_mode_split;
                    break;
            }
            layoutModes.add(new ListSettingView.Item<>(getString(name), mode));
        }

        requiresRestart.add(layout.add(new ListSettingView<>(this,
                ChanSettings.layoutMode,
                R.string.setting_layout_mode, layoutModes)));
    }

    private void setupGridColumnsSetting(SettingsGroup layout) {
        List<ListSettingView.Item> gridColumns = new ArrayList<>();
        gridColumns.add(new ListSettingView.Item<>(
                getString(R.string.setting_board_grid_span_count_default), 0));
        for (int columns = 2; columns <= 5; columns++) {
            gridColumns.add(new ListSettingView.Item<>(
                    context.getString(R.string.setting_board_grid_span_count_item, columns),
                    columns));
        }
        requiresUiRefresh.add(layout.add(new ListSettingView<>(this,
                ChanSettings.boardGridSpanCount,
                R.string.setting_board_grid_span_count, gridColumns)));
    }

    private void setupFontSizeSetting(SettingsGroup post) {
        List<ListSettingView.Item> fontSizes = new ArrayList<>();
        for (int size = 10; size <= 19; size++) {
            String name = size + (String.valueOf(size)
                    .equals(ChanSettings.fontSize.getDefault()) ?
                    " " + getString(R.string.setting_font_size_default) :
                    "");
            fontSizes.add(new ListSettingView.Item<>(name, String.valueOf(size)));
        }

        requiresUiRefresh.add(post.add(new ListSettingView<>(this,
                ChanSettings.fontSize,
                R.string.setting_font_size,
                fontSizes.toArray(new ListSettingView.Item[fontSizes.size()]))));
    }
}
