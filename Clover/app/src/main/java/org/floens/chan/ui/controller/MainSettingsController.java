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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.HintPopup;
import org.floens.chan.ui.helper.RefreshUIMessage;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.ui.settings.StringSettingView;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.AnimationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.getString;

public class MainSettingsController extends SettingsController implements ToolbarMenuItem.ToolbarMenuItemCallback, WatchSettingsController.WatchSettingControllerListener, PassSettingsController.PassSettingControllerListener {
    private static final int ADVANCED_SETTINGS = 1;
    private SettingView imageAutoLoadView;
    private SettingView videoAutoLoadView;

    private LinkSettingView watchLink;
    private LinkSettingView passLink;
    private int clickCount;
    private SettingView developerView;
    private SettingView fontView;
    private ToolbarMenuItem overflow;

    private PopupWindow advancedSettingsHint;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.setTitle(R.string.settings_screen);
        navigationItem.menu = new ToolbarMenu(context);
        overflow = navigationItem.createOverflow(context, this, Collections.singletonList(
                new FloatingMenuItem(ADVANCED_SETTINGS, R.string.settings_screen_advanced)
        ));

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        onWatchEnabledChanged(ChanSettings.watchEnabled.get());
        onPassEnabledChanged(ChanSettings.passLoggedIn());

        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        if (!ChanSettings.developer.get()) {
            developerView.view.getLayoutParams().height = 0;
        }

        ChanSettings.settingsOpenCounter.set(ChanSettings.settingsOpenCounter.get() + 1);
        if (ChanSettings.settingsOpenCounter.get() == 3) {
            ImageView view = overflow.getView();
            view.startAnimation(android.view.animation.AnimationUtils.loadAnimation(context, R.anim.menu_overflow_shake));
            advancedSettingsHint = HintPopup.show(context, view, R.string.settings_advanced_hint);
        }
    }

    @Override
    public void onHide() {
        super.onHide();

        if (advancedSettingsHint != null) {
            advancedSettingsHint.dismiss();
            advancedSettingsHint = null;
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
            videoAutoLoadView.setEnabled(!ChanSettings.imageAutoLoadNetwork.get().equals(ChanSettings.ImageAutoLoadMode.NONE.name));
        } else if (item == fontView) {
            EventBus.getDefault().post(new RefreshUIMessage("fontsize"));
        }
    }

    @Override
    public void onWatchEnabledChanged(boolean enabled) {
        watchLink.setDescription(enabled ? R.string.setting_watch_summary_enabled : R.string.setting_watch_summary_disabled);
    }

    @Override
    public void onPassEnabledChanged(boolean enabled) {
        passLink.setDescription(enabled ? R.string.setting_pass_summary_enabled : R.string.setting_pass_summary_disabled);
    }

    private void populatePreferences() {
        // General group
        SettingsGroup general = new SettingsGroup(R.string.settings_group_general);
        general.add(new LinkSettingView(this, R.string.settings_board_edit, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new BoardEditController(context));
            }
        }));

        watchLink = (LinkSettingView) general.add(new LinkSettingView(this, R.string.settings_watch, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new WatchSettingsController(context));
            }
        }));

        groups.add(general);

        SettingsGroup appearance = new SettingsGroup(R.string.settings_group_appearance);

        appearance.add(new LinkSettingView(this, R.string.settings_screen_theme, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new ThemeSettingsController(context));
            }
        }));

        List<ListSettingView.Item> fontSizes = new ArrayList<>();
        for (int size = 10; size <= 19; size++) {
            String name = size + (String.valueOf(size).equals(ChanSettings.fontSize.getDefault()) ? " " + getString(R.string.setting_font_size_default) : "");
            fontSizes.add(new ListSettingView.Item(name, String.valueOf(size)));
        }

        fontView = appearance.add(new ListSettingView(this, ChanSettings.fontSize, R.string.setting_font_size, fontSizes.toArray(new ListSettingView.Item[fontSizes.size()])));

        groups.add(appearance);

        // Browsing group
        SettingsGroup browsing = new SettingsGroup(R.string.settings_group_browsing);

        browsing.add(new LinkSettingView(this, R.string.filters_screen, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new FiltersController(context));
            }
        }));
        browsing.add(new BooleanSettingView(this, ChanSettings.openLinkConfirmation, R.string.setting_open_link_confirmation, 0));
        browsing.add(new BooleanSettingView(this, ChanSettings.autoRefreshThread, R.string.setting_auto_refresh_thread, 0));

        List<ListSettingView.Item> imageAutoLoadTypes = new ArrayList<>();
        for (ChanSettings.ImageAutoLoadMode mode : ChanSettings.ImageAutoLoadMode.values()) {
            int name = 0;
            switch (mode) {
                case ALL:
                    name = R.string.setting_image_auto_load_all;
                    break;
                case WIFI:
                    name = R.string.setting_image_auto_load_wifi;
                    break;
                case NONE:
                    name = R.string.setting_image_auto_load_none;
                    break;
            }

            imageAutoLoadTypes.add(new ListSettingView.Item(getString(name), mode.name));
        }

        imageAutoLoadView = browsing.add(new ListSettingView(this, ChanSettings.imageAutoLoadNetwork, R.string.setting_image_auto_load, imageAutoLoadTypes.toArray(new ListSettingView.Item[imageAutoLoadTypes.size()])));
        videoAutoLoadView = browsing.add(new BooleanSettingView(this, ChanSettings.videoAutoLoad, R.string.setting_video_auto_load, R.string.setting_video_auto_load_description));
        browsing.add(new BooleanSettingView(this, ChanSettings.videoOpenExternal, R.string.setting_video_open_external, R.string.setting_video_open_external_description));
        browsing.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Chan.getDatabaseManager().clearAllThreadHides();
                Toast.makeText(context, R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG).show();
                EventBus.getDefault().post(new RefreshUIMessage("clearhides"));
            }
        }));

        groups.add(browsing);

        // Posting group
        SettingsGroup posting = new SettingsGroup(R.string.settings_group_posting);

        passLink = (LinkSettingView) posting.add(new LinkSettingView(this, R.string.settings_pass, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new PassSettingsController(context));
            }
        }));

        posting.add(new BooleanSettingView(this, ChanSettings.postPinThread, R.string.setting_post_pin, 0));
        posting.add(new StringSettingView(this, ChanSettings.postDefaultName, R.string.setting_post_default_name, R.string.setting_post_default_name));

        groups.add(posting);

        // About group
        SettingsGroup about = new SettingsGroup(R.string.settings_group_about);
        String version = "";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        about.add(new LinkSettingView(this, getString(R.string.app_name), version, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((++clickCount) % 5 == 0) {
                    boolean developer = !ChanSettings.developer.get();

                    ChanSettings.developer.set(developer);

                    Toast.makeText(context, (developer ? "Enabled" : "Disabled") + " developer options", Toast.LENGTH_LONG).show();

                    AnimationUtils.animateHeight(developerView.view, developer);
                }
            }
        }));

        int extraAbouts = context.getResources().getIdentifier("extra_abouts", "array", context.getPackageName());
        if (extraAbouts != 0) {
            String[] abouts = context.getResources().getStringArray(extraAbouts);
            if (abouts.length % 3 == 0) {
                for (int i = 0, aboutsLength = abouts.length; i < aboutsLength; i += 3) {
                    String aboutName = abouts[i];
                    String aboutDescription = abouts[i + 1];
                    if (TextUtils.isEmpty(aboutDescription)) {
                        aboutDescription = null;
                    }
                    String aboutLink = abouts[i + 2];
                    if (TextUtils.isEmpty(aboutLink)) {
                        aboutLink = null;
                    }

                    final String finalAboutLink = aboutLink;
                    about.add(new LinkSettingView(this, aboutName, aboutDescription, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (finalAboutLink != null) {
                                if (finalAboutLink.contains("__EMAIL__")) {
                                    String[] email = finalAboutLink.split("__EMAIL__");
                                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                                    intent.setData(Uri.parse("mailto:"));
                                    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email[0]});
                                    intent.putExtra(Intent.EXTRA_SUBJECT, email[1]);
                                    AndroidUtils.openIntent(intent);
                                } else {
                                    AndroidUtils.openLink(finalAboutLink);
                                }
                            }
                        }
                    }));
                }
            }
        }

        about.add(new LinkSettingView(this, R.string.settings_about_license, R.string.settings_about_license_description, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new LicensesController(context,
                        getString(R.string.settings_about_license), "file:///android_asset/html/license.html"));
            }
        }));

        about.add(new LinkSettingView(this, R.string.settings_about_licenses, R.string.settings_about_licenses_description, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new LicensesController(context,
                        getString(R.string.settings_about_licenses), "file:///android_asset/html/licenses.html"));
            }
        }));

        developerView = about.add(new LinkSettingView(this, R.string.settings_developer, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new DeveloperSettingsController(context));
            }
        }));

        groups.add(about);
    }
}
