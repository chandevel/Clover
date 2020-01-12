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

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.controller.settings.captcha.JsCaptchaCookiesEditorController;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;

import kotlin.Unit;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class BehaviourSettingsController
        extends SettingsController {
    public BehaviourSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        navigation.setTitle(R.string.settings_screen_behavior);

        setupLayout();
        rebuildPreferences();
    }

    private void rebuildPreferences() {
        populatePreferences();
        buildPreferences();
    }

    private void populatePreferences() {
        requiresUiRefresh.clear();
        groups.clear();
        requiresRestart.clear();

        // General group (general application behavior)
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_general);

            general.add(new BooleanSettingView(this,
                    ChanSettings.autoRefreshThread,
                    R.string.setting_auto_refresh_thread,
                    0
            ));

            requiresRestart.add(general.add(new BooleanSettingView(this,
                    ChanSettings.controllerSwipeable,
                    R.string.setting_controller_swipeable,
                    0
            )));

            general.add(new BooleanSettingView(this,
                    ChanSettings.openLinkConfirmation,
                    R.string.setting_open_link_confirmation,
                    0
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.openLinkBrowser,
                    R.string.setting_open_link_browser,
                    0
            ));

            requiresRestart.add(general.add(new BooleanSettingView(this,
                    ChanSettings.fullUserRotationEnable,
                    R.string.setting_full_screen_rotation,
                    0
            )));

            general.add(new BooleanSettingView(this,
                    ChanSettings.alwaysOpenDrawer,
                    R.string.settings_always_open_drawer,
                    0
            ));

            general.add(new BooleanSettingView(this,
                    ChanSettings.allowMediaScannerToScanLocalThreads,
                    R.string.settings_allow_media_scanner_scan_local_threads_title,
                    R.string.settings_allow_media_scanner_scan_local_threads_description
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_captcha_setup,
                    R.string.settings_captcha_setup_description,
                    v -> navigationController.pushController(new SitesSetupController(context))
            ));
            general.add(
                    new LinkSettingView(this,
                            R.string.settings_js_captcha_cookies_title,
                            R.string.settings_js_captcha_cookies_description,
                            v -> {
                                JsCaptchaCookiesEditorController controller
                                        = new JsCaptchaCookiesEditorController(context);

                                controller.setOnFinishedCallback(() -> {
                                    navigationController.popController();
                                    return Unit.INSTANCE;
                                });
                                navigationController.pushController(controller);
                            }
                    )
            );

            setupClearThreadHidesSetting(general);

            groups.add(general);
        }

        // Reply group (reply input specific behavior)
        {
            SettingsGroup reply = new SettingsGroup(R.string.settings_group_reply);

            reply.add(new BooleanSettingView(this, ChanSettings.postPinThread, R.string.setting_post_pin, 0));

            reply.add(new StringSettingView(this,
                    ChanSettings.postDefaultName,
                    R.string.setting_post_default_name,
                    R.string.setting_post_default_name
            ));

            groups.add(reply);
        }

        // Post group (post/thread specific behavior)
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            post.add(new BooleanSettingView(this,
                    ChanSettings.repliesButtonsBottom,
                    R.string.setting_buttons_bottom,
                    0
            ));

            post.add(new BooleanSettingView(this,
                    ChanSettings.volumeKeysScrolling,
                    R.string.setting_volume_key_scrolling,
                    0
            ));

            post.add(new BooleanSettingView(this, ChanSettings.tapNoReply, R.string.setting_tap_no_rely, 0));

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

        // Proxy group (proxy settings)
        {
            SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);

            requiresRestart.add(proxy.add(new BooleanSettingView(this,
                    ChanSettings.proxyEnabled,
                    R.string.setting_proxy_enabled,
                    0
            )));

            requiresRestart.add(proxy.add(new StringSettingView(this,
                    ChanSettings.proxyAddress,
                    R.string.setting_proxy_address,
                    R.string.setting_proxy_address
            )));

            requiresRestart.add(proxy.add(new IntegerSettingView(this,
                    ChanSettings.proxyPort,
                    R.string.setting_proxy_port,
                    R.string.setting_proxy_port
            )));

            groups.add(proxy);
        }

        {
            SettingsGroup other = new SettingsGroup("Other Options");

            other.add(new StringSettingView(this,
                    ChanSettings.parseYoutubeAPIKey,
                    "Youtube API Key",
                    "Youtube API Key"
            ));
        }
    }

    private void setupClearThreadHidesSetting(SettingsGroup post) {
        post.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, 0, v -> {
            // TODO: don't do this here.
            DatabaseManager databaseManager = instance(DatabaseManager.class);
            databaseManager.runTask(databaseManager.getDatabaseHideManager().clearAllThreadHides());
            showToast(R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG);
            postToEventBus(new RefreshUIMessage("clearhides"));
        }));
    }
}
