/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

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
                    getString(R.string.setting_theme), ThemeHelper.getTheme().displayName,
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

            requiresUiRefresh.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.moveInputToBottom,
                    "Bottom input", "Makes the captcha and reply input float to the bottom of the screen")));

            groups.add(layout);
        }

        // Post group
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            setupFontSizeSetting(post);

            requiresRestart.add(post.add(new BooleanSettingView(this,
                    ChanSettings.shiftPostFormat,
                    context.getString(R.string.setting_shift_post), context.getString(R.string.setting_shift_post_description))));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.fontAlternate,
                    R.string.setting_font_alt,
                    R.string.setting_font_alt_description)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFullDate,
                    R.string.setting_post_full_date, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFileInfo,
                    R.string.setting_post_file_info, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFilename,
                    R.string.setting_post_filename, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.enableEmoji,
                    context.getString(R.string.setting_enable_emoji), context.getString(R.string.setting_enable_emoji_description))));

            groups.add(post);
        }

        //Image group
        {
            SettingsGroup images = new SettingsGroup(R.string.settings_group_images);

            requiresUiRefresh.add(images.add(new BooleanSettingView(this,
                    ChanSettings.highResCells,
                    context.getString(R.string.setting_images_high_res),
                    context.getString(R.string.setting_images_high_res_description))));

            requiresUiRefresh.add(images.add(new BooleanSettingView(this,
                    ChanSettings.useImmersiveModeForGallery,
                    R.string.setting_images_immersive_mode_title,
                    R.string.setting_images_immersive_mode_description)));

            groups.add(images);
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
                fontSizes.toArray(new ListSettingView.Item[0]))));
    }
}
