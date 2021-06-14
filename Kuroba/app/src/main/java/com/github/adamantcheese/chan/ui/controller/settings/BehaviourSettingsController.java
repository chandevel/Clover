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
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.ProxyMode;
import com.github.adamantcheese.chan.features.gesture_editor.Android10GesturesExclusionZonesHolder;
import com.github.adamantcheese.chan.features.gesture_editor.AttachSide;
import com.github.adamantcheese.chan.features.gesture_editor.ExclusionZone;
import com.github.adamantcheese.chan.ui.controller.AdjustAndroid10GestureZonesController;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.controller.WebViewController;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.THREAD_HIDES_CLEARED;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getScreenOrientation;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;

public class BehaviourSettingsController
        extends SettingsController {
    @Inject
    DatabaseHideManager hideManager;

    private BooleanSettingView proxyEnabledSetting;
    private StringSettingView proxyAddressSetting;
    private IntegerSettingView proxyPortSetting;
    private ListSettingView<ProxyMode> proxyTypeSetting;

    public BehaviourSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        navigation.setTitle(R.string.settings_screen_behavior);

        proxyAddressSetting.setEnabled(ChanSettings.proxyEnabled.get());
        proxyPortSetting.setEnabled(ChanSettings.proxyEnabled.get());
        proxyTypeSetting.setEnabled(ChanSettings.proxyEnabled.get());
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == proxyEnabledSetting) {
            updateProxySettings();
        }
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

            if (isAndroid10()) {
                requiresRestart.add(general.add(new LinkSettingView(this,
                        R.string.setting_exclusion_zones_editor,
                        R.string.setting_exclusion_zones_editor_description,
                        (v, sv) -> doExclusionZoneSelection(sv)
                )));
            }

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
                    (v, sv) -> navigationController.pushController(new SitesSetupController(context))
            ));
            general.add(new LinkSettingView(this,
                    "Google Login",
                    "Sign into Google to grab your cookies, for Captcha ease.",
                    (v, sv) -> navigationController.pushController(new WebViewController(context,
                            "Google Login",
                            HttpUrl.get("https://accounts.google.com")
                    ))
            ));

            general.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, R.string.empty, (v, sv) -> {
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

            requiresRestart.add(other.add(new BooleanSettingView(this,
                    ChanSettings.okHttpAllowHttp2,
                    R.string.setting_allow_okhttp_http2,
                    R.string.setting_allow_okhttp_http2_ipv6_description
            )));

            requiresRestart.add(other.add(new BooleanSettingView(this,
                    ChanSettings.okHttpAllowIpv6,
                    R.string.setting_allow_okhttp_ipv6,
                    R.string.setting_allow_okhttp_http2_ipv6_description
            )));

            groups.add(other);
        }

        // Proxy group (proxy settings)
        {
            SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);

            proxyEnabledSetting = proxy.add(new BooleanSettingView(this,
                    ChanSettings.proxyEnabled,
                    R.string.setting_proxy_enabled,
                    R.string.empty
            ));

            proxyAddressSetting = proxy.add(new StringSettingView(this,
                    ChanSettings.proxyAddress,
                    R.string.setting_proxy_address,
                    R.string.setting_proxy_address,
                    R.string.empty
            ));

            proxyPortSetting = proxy.add(new IntegerSettingView(this,
                    ChanSettings.proxyPort,
                    R.string.setting_proxy_port,
                    R.string.setting_proxy_port,
                    new Pair<>(0, 0xFFFF)
            ));

            List<ListSettingView.Item<ProxyMode>> proxyTypes = new ArrayList<>();
            for (ProxyMode type : ProxyMode.values()) {
                proxyTypes.add(new ListSettingView.Item<>(type.name().toUpperCase(), type));
            }
            proxyTypeSetting = proxy.add(new ListSettingView<>(this,
                    ChanSettings.proxyType,
                    R.string.setting_proxy_type,
                    proxyTypes
            ));

            requiresRestart.addAll(proxy.settingViews);
            groups.add(proxy);
        }
    }

    private void updateProxySettings() {
        proxyAddressSetting.setEnabled(ChanSettings.proxyEnabled.get());
        proxyPortSetting.setEnabled(ChanSettings.proxyEnabled.get());
        proxyTypeSetting.setEnabled(ChanSettings.proxyEnabled.get());
    }

    @SuppressWarnings("ConstantConditions")
    private void doExclusionZoneSelection(SettingView settingView) {
        if (!isAndroid10()) return;
        // adapter setup
        ArrayAdapter<TripleString<Integer, AttachSide>> arrayAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);
        int screenOrientation = getScreenOrientation();
        // only show what's needed for the orientation
        if (screenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            arrayAdapter.add(new TripleString<>(getString(R.string.setting_exclusion_zones_left_zone),
                    Configuration.ORIENTATION_PORTRAIT,
                    AttachSide.Left
            ));
            arrayAdapter.add(new TripleString<>(getString(R.string.setting_exclusion_zones_right_zone),
                    Configuration.ORIENTATION_PORTRAIT,
                    AttachSide.Right
            ));
        } else if (screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            arrayAdapter.add(new TripleString<>(getString(R.string.setting_exclusion_zones_left_zone),
                    Configuration.ORIENTATION_LANDSCAPE,
                    AttachSide.Left
            ));
            arrayAdapter.add(new TripleString<>(getString(R.string.setting_exclusion_zones_right_zone),
                    Configuration.ORIENTATION_LANDSCAPE,
                    AttachSide.Right
            ));
        } else {
            showToast(context, "Unknown orientation!");
            return;
        }
        // dialog setup
        getDefaultAlertBuilder(context).setTitle(screenOrientation == Configuration.ORIENTATION_PORTRAIT
                ? R.string.setting_exclusion_zones_actions_dialog_title_portrait
                : R.string.setting_exclusion_zones_actions_dialog_title_landscape)
                .setAdapter(arrayAdapter, (dialog, selectedIndex) -> {
                    // zone picked
                    int orientation = arrayAdapter.getItem(selectedIndex).second;
                    AttachSide attachSide = arrayAdapter.getItem(selectedIndex).third;

                    // get existing zone for the picked spot
                    final ExclusionZone skipZone =
                            Android10GesturesExclusionZonesHolder.getZone(orientation, attachSide);
                    if (skipZone != null) {
                        // zone exists, edit or remove
                        getDefaultAlertBuilder(context).setTitle(R.string.setting_exclusion_zones_edit_or_remove_zone_title)
                                .setPositiveButton(R.string.edit,
                                        (dialog1, which) -> addEditGestureZone(attachSide,
                                                skipZone,
                                                settingView,
                                                dialog1
                                        )
                                )
                                .setNegativeButton(R.string.remove, ((dialog1, which) -> {
                                    Android10GesturesExclusionZonesHolder.removeZone(orientation, attachSide);
                                    onPreferenceChange(settingView);
                                    dialog1.dismiss();
                                }))
                                .create()
                                .show();
                    } else {
                        // zone doesn't exist, add
                        addEditGestureZone(attachSide, null, settingView, dialog);
                    }
                })
                .setNeutralButton(R.string.setting_exclusion_zones_reset_zones, ((dialog, which) -> {
                    Android10GesturesExclusionZonesHolder.resetZones();
                    onPreferenceChange(settingView);
                    dialog.dismiss();
                    // immediately restart the app
                    navigationController.popController();
                }))
                .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                .create()
                .show();
    }

    private void addEditGestureZone(
            AttachSide attachSide, ExclusionZone skipZone, SettingView settingView, DialogInterface dialog
    ) {
        AdjustAndroid10GestureZonesController adjustGestureZonesController =
                new AdjustAndroid10GestureZonesController(context, attachSide, skipZone) {
                    @RequiresApi(api = Build.VERSION_CODES.Q)
                    @Override
                    public void onDestroy() {
                        super.onDestroy();
                        if (adjusted) onPreferenceChange(settingView);
                    }
                };

        navigationController.presentController(adjustGestureZonesController);
        dialog.dismiss();
    }

    public static class TripleString<B, C> {
        @NonNull
        public String first;
        @NonNull
        public B second;
        @NonNull
        public C third;

        public TripleString(@NonNull String first, @NonNull B second, @NonNull C third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        @NonNull
        @Override
        public String toString() {
            return first;
        }
    }
}
