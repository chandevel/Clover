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
package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView.Item;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class AppearanceSettingsController
        extends SettingsController {
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
                    getString(R.string.setting_theme),
                    ThemeHelper.getTheme().name,
                    v -> navigationController.pushController(new ThemeSettingsController(context))
            ));

            groups.add(appearance);
        }

        // Layout group (over-arching UI changes)
        {
            SettingsGroup layout = new SettingsGroup(R.string.settings_group_layout);

            setupLayoutModeSetting(layout);

            setupGridColumnsSetting(layout);

            requiresRestart.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.neverHideToolbar,
                    R.string.setting_never_hide_toolbar,
                    R.string.empty
            )));

            requiresRestart.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.enableReplyFab,
                    R.string.setting_enable_reply_fab,
                    R.string.setting_enable_reply_fab_description
            )));

            layout.add(new BooleanSettingView(this,
                    ChanSettings.repliesButtonsBottom,
                    R.string.setting_buttons_bottom,
                    R.string.empty
            ));

            requiresUiRefresh.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.moveInputToBottom,
                    "Bottom input",
                    "Makes the reply input float to the bottom of the screen"
            )));

            layout.add(new BooleanSettingView(this,
                    ChanSettings.captchaOnBottom,
                    "Bottom captcha",
                    "Makes the JS captcha float to the bottom of the screen"
            ));

            requiresUiRefresh.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.useImmersiveModeForGallery,
                    R.string.setting_images_immersive_mode_title,
                    R.string.setting_images_immersive_mode_description
            )));

            requiresRestart.add(layout.add(new BooleanSettingView(this,
                    ChanSettings.moveSortToToolbar,
                    R.string.setting_move_sort_to_toolbar,
                    R.string.setting_move_sort_to_toolbar_description
            )));

            layout.add(new BooleanSettingView(this,
                    ChanSettings.neverShowPages,
                    "Never show page number",
                    "Never display the page number in the catalog"
            ));

            groups.add(layout);
        }

        // Post group (post-specific UI changes)
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            setupThumbnailSizeSetting(post);
            setupFontSizeSetting(post);

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.fontAlternate,
                    R.string.setting_font_alt,
                    R.string.setting_font_alt_description
            )));

            requiresRestart.add(post.add(new BooleanSettingView(this,
                    ChanSettings.shiftPostFormat,
                    R.string.setting_shift_post,
                    R.string.setting_shift_post_description
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.accessibleInfo,
                    "Enable accessible post info",
                    "Enabling places info in the first post option menu"
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFullDate,
                    R.string.setting_post_full_date,
                    R.string.empty
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFileInfo,
                    R.string.setting_post_file_info,
                    R.string.empty
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.postFilename,
                    R.string.setting_post_filename,
                    R.string.empty
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.textOnly,
                    R.string.setting_text_only,
                    R.string.setting_text_only_description
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.revealTextSpoilers,
                    R.string.settings_reveal_text_spoilers,
                    R.string.settings_reveal_text_spoilers_description
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.anonymize,
                    R.string.setting_anonymize,
                    "Sets everyone's name field to be \"Anonymous\""
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.showAnonymousName,
                    R.string.setting_show_anonymous_name,
                    "Displays \"Anonymous\" rather than an empty field"
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.anonymizeIds,
                    R.string.setting_anonymize_ids,
                    R.string.empty
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.addDubs,
                    R.string.add_dubs_title,
                    R.string.add_dubs_description
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.parseYoutubeTitles,
                    R.string.setting_youtube_title,
                    R.string.setting_youtube_title_description
            )));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.parseYoutubeDuration,
                    R.string.setting_youtube_dur_title,
                    R.string.setting_youtube_dur_description
            )));

            //this is also in Behavior settings
            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.enableEmoji,
                    R.string.setting_enable_emoji,
                    R.string.setting_enable_emoji_description
            )));

            groups.add(post);
        }

        //Image group (image cell specific UI changes)
        {
            SettingsGroup images = new SettingsGroup(R.string.settings_group_images);

            requiresUiRefresh.add(images.add(new BooleanSettingView(this,
                    ChanSettings.hideImages,
                    R.string.setting_hide_images,
                    R.string.setting_hide_images_description
            )));

            images.add(new BooleanSettingView(this,
                    ChanSettings.removeImageSpoilers,
                    R.string.settings_remove_image_spoilers,
                    R.string.settings_remove_image_spoilers_description
            ));

            images.add(new BooleanSettingView(this,
                    ChanSettings.revealimageSpoilers,
                    R.string.settings_reveal_image_spoilers,
                    R.string.settings_reveal_image_spoilers_description
            ));

            requiresUiRefresh.add(images.add(new BooleanSettingView(this,
                    ChanSettings.parsePostImageLinks,
                    R.string.setting_image_link_loading_title,
                    R.string.setting_image_link_loading_description
            )));

            images.add(new BooleanSettingView(this,
                    ChanSettings.transparencyOn,
                    "Image opacity",
                    "Default state for image transparency in the viewer"
            ));

            images.add(new BooleanSettingView(this,
                    ChanSettings.neverShowWebmControls,
                    "Never show WEBM controls",
                    "Treats WEBMs like GIFs; tap to close, double tap to play/pause, always automatically loops."
            ));

            groups.add(images);
        }
    }

    private void setupLayoutModeSetting(SettingsGroup layout) {
        List<Item<LayoutMode>> layoutModes = new ArrayList<>();
        for (LayoutMode mode : LayoutMode.values()) {
            layoutModes.add(new Item<>(StringUtils.caseAndSpace(mode.name(), null) + " mode", mode));
        }

        requiresRestart.add(layout.add(new ListSettingView<>(this,
                ChanSettings.layoutMode,
                R.string.setting_layout_mode,
                layoutModes
        )));
    }

    private void setupGridColumnsSetting(SettingsGroup layout) {
        boolean isPortrait = AndroidUtils.getScreenOrientation() == ORIENTATION_PORTRAIT;

        List<Item<Integer>> gridColumnsBoard = new ArrayList<>();
        List<Item<Integer>> gridColumnsAlbum = new ArrayList<>();
        gridColumnsBoard.add(new Item<>(getString(R.string.setting_grid_span_count_default), 0));
        gridColumnsAlbum.add(new Item<>(getString(R.string.setting_grid_span_count_default), 0));
        for (int columns = 1; columns <= (isPortrait ? 5 : 12); columns++) {
            gridColumnsBoard.add(new Item<>(getString(R.string.setting_grid_span_count_item, columns), columns));
            gridColumnsAlbum.add(new Item<>(getString(R.string.setting_grid_span_count_item, columns), columns));
        }

        requiresUiRefresh.add(layout.add(new ListSettingView<>(this,
                isPortrait ? ChanSettings.boardGridSpanCountPortrait : ChanSettings.boardGridSpanCountLandscape,
                isPortrait
                        ? R.string.setting_board_grid_span_count_portrait
                        : R.string.setting_board_grid_span_count_landscape,
                gridColumnsBoard
        )));

        requiresUiRefresh.add(layout.add(new ListSettingView<>(this,
                isPortrait ? ChanSettings.albumGridSpanCountPortrait : ChanSettings.albumGridSpanCountLandscape,
                isPortrait
                        ? R.string.setting_album_grid_span_count_portrait
                        : R.string.setting_album_grid_span_count_landscape,
                gridColumnsAlbum
        )));
    }

    private void setupFontSizeSetting(SettingsGroup post) {
        List<Item<String>> fontSizes = new ArrayList<>();
        for (int size = 10; size <= 19; size++) {
            String name = size + (String.valueOf(size).equals(ChanSettings.fontSize.getDefault()) ? " "
                    + getString(R.string.setting_font_size_default) : "");
            fontSizes.add(new Item<>(name, String.valueOf(size)));
        }

        requiresUiRefresh.add(post.add(new ListSettingView<>(this,
                ChanSettings.fontSize,
                R.string.setting_font_size,
                fontSizes
        )));
    }

    private void setupThumbnailSizeSetting(SettingsGroup post) {
        List<Item<Integer>> thumbnailSizes = new ArrayList<>();
        for (int size = 100; size <= 200; size+=20) {
            String name = size + "%";
            thumbnailSizes.add(new Item<>(name, size));
        }

        requiresUiRefresh.add(post.add(new ListSettingView<>(this,
                ChanSettings.thumbnailSize,
                R.string.setting_thumbnail_size,
                thumbnailSizes
        )));
    }
}
