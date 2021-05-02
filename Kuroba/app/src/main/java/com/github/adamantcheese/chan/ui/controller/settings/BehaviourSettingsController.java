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
import android.widget.Toast;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.controller.WebViewController;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.THREAD_HIDES_CLEARED;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;

public class BehaviourSettingsController
        extends SettingsController {
    @Inject
    DatabaseHideManager hideManager;

    public BehaviourSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        navigation.setTitle(R.string.settings_screen_behavior);
    }

    @Override
    protected void populatePreferences() {
        requiresUiRefresh.clear();
        groups.clear();
        requiresRestart.clear();

        // General group (general application behavior)
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_general);

            general.add(new BooleanSettingView(this,
                    ChanSettings.autoRefreshThread,
                    R.string.setting_auto_refresh_thread,
                    R.string.empty
            ));

            requiresRestart.add(general.add(new BooleanSettingView(this,
                    ChanSettings.controllerSwipeable,
                    R.string.setting_controller_swipeable,
                    R.string.empty
            )));

            general.add(new BooleanSettingView(this,
                    ChanSettings.openLinkConfirmation,
                    R.string.setting_open_link_confirmation,
                    R.string.empty
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.openLinkBrowser,
                    R.string.setting_open_link_browser,
                    R.string.empty
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.imageViewerGestures,
                    R.string.setting_image_viewer_gestures,
                    R.string.setting_image_viewer_gestures_description
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.alwaysOpenDrawer,
                    R.string.settings_always_open_drawer,
                    R.string.empty
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.applyImageFilterToPost,
                    R.string.apply_image_filter_to_post,
                    R.string.apply_image_filter_to_post_description
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_captcha_setup,
                    R.string.settings_captcha_setup_description,
                    v -> navigationController.pushController(new SitesSetupController(context))
            ));
            general.add(new LinkSettingView(this,
                    "Google Login",
                    "Sign into Google to grab your cookies, for Captcha ease.",
                    v -> navigationController.pushController(new WebViewController(context,
                            "Google Login",
                            HttpUrl.get("https://accounts.google.com")
                    ))
            ));

            general.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, R.string.empty, v -> {
                // TODO: don't do this here.
                DatabaseUtils.runTask(hideManager.clearAllThreadHides());
                showToast(context, R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG);
                postToEventBus(new RefreshUIMessage(THREAD_HIDES_CLEARED));
            }));

            groups.add(general);
        }

        // Reply group (reply input specific behavior)
        {
            SettingsGroup reply = new SettingsGroup(R.string.settings_group_reply);

            reply.add(new BooleanSettingView(this,
                    ChanSettings.postPinThread,
                    R.string.setting_post_pin,
                    R.string.empty
            ));

            reply.add(new StringSettingView(this,
                    ChanSettings.postDefaultName,
                    R.string.setting_post_default_name,
                    R.string.setting_post_default_name,
                    R.string.empty
            ));

            reply.add(new BooleanSettingView(this,
                    ChanSettings.alwaysSetNewFilename,
                    "Always randomize filename",
                    "When attaching an image, the filename will be automatically randomized"
            ));

            groups.add(reply);
        }

        // Post group (post/thread specific behavior)
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            post.add(new BooleanSettingView(this,
                    ChanSettings.volumeKeysScrolling,
                    R.string.setting_volume_key_scrolling,
                    R.string.empty
            ));

            post.add(new BooleanSettingView(this,
                    ChanSettings.enableLongPressURLCopy,
                    R.string.settings_image_long_url,
                    R.string.settings_image_long_url_description
            ));

            post.add(new BooleanSettingView(this,
                    ChanSettings.shareUrl,
                    R.string.setting_share_url,
                    R.string.setting_share_url_description
            ));

            //this is also in Appearance settings
            post.add(new BooleanSettingView(this,
                    ChanSettings.enableEmoji,
                    R.string.setting_enable_emoji,
                    R.string.setting_enable_emoji_description
            ));

            groups.add(post);
        }

        {
            SettingsGroup other = new SettingsGroup("Other Options");

            other.add(new StringSettingView(this,
                    ChanSettings.parseYoutubeAPIKey,
                    R.string.yt_api_key_dialog,
                    R.string.yt_api_key_dialog,
                    R.string.yt_api_key_dialog_description
            ));

            requiresRestart.add(other.add(new BooleanSettingView(this,
                    ChanSettings.fullUserRotationEnable,
                    R.string.setting_full_screen_rotation,
                    R.string.empty
            )));

            other.add(new BooleanSettingView(this,
                    ChanSettings.allowFilePickChooser,
                    "Allow alternate file pickers",
                    "If you'd prefer to use a different file chooser, turn this on"
            ));

            groups.add(other);
        }

        // Proxy group (proxy settings)
        {
            SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);

            requiresRestart.add(proxy.add(new BooleanSettingView(this,
                    ChanSettings.proxyEnabled,
                    R.string.setting_proxy_enabled,
                    R.string.empty
            )));

            requiresRestart.add(proxy.add(new StringSettingView(this,
                    ChanSettings.proxyAddress,
                    R.string.setting_proxy_address,
                    R.string.setting_proxy_address,
                    R.string.empty
            )));

            requiresRestart.add(proxy.add(new IntegerSettingView(this,
                    ChanSettings.proxyPort,
                    R.string.setting_proxy_port,
                    R.string.setting_proxy_port,
                    new Pair<>(0, 0xFFFF)
            )));

            groups.add(proxy);
        }
    }
}
