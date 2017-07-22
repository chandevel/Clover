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

import org.floens.chan.R;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.Sites;
import org.floens.chan.ui.activity.StartActivity;
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
import org.floens.chan.ui.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.getString;

public class MainSettingsController extends SettingsController implements ToolbarMenuItem.ToolbarMenuItemCallback, WatchSettingsController.WatchSettingControllerListener, PassSettingsController.PassSettingControllerListener {
    private static final int ADVANCED_SETTINGS = 1;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;

    private LinkSettingView boardEditorView;
    private LinkSettingView saveLocation;
    private LinkSettingView watchLink;
    private LinkSettingView passLink;
    private int clickCount;
    private SettingView developerView;
    private SettingView fontView;
    private SettingView layoutModeView;
    private SettingView fontCondensed;
    private SettingView textOnly;
    private SettingView gridColumnsView;
    private ToolbarMenuItem overflow;

    private ChanSettings.LayoutMode previousLayoutMode;

    private PopupWindow advancedSettingsHint;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    BoardManager boardManager;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getGraph().inject(this);
        EventBus.getDefault().register(this);

        navigationItem.setTitle(R.string.settings_screen);
        navigationItem.menu = new ToolbarMenu(context);
        overflow = navigationItem.createOverflow(context, this, Collections.singletonList(
                new FloatingMenuItem(ADVANCED_SETTINGS, R.string.settings_screen_advanced)
        ));

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        previousLayoutMode = ChanSettings.layoutMode.get();

        populatePreferences();

        onWatchEnabledChanged(ChanSettings.watchEnabled.get());
        // TODO(multi-site)
        onPassEnabledChanged(Sites.defaultSite().isLoggedIn());

        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        if (!ChanSettings.developer.get()) {
            developerView.view.getLayoutParams().height = 0;
        }

        if (ChanSettings.settingsOpenCounter.increase() == 3) {
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
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);

        if (previousLayoutMode != ChanSettings.layoutMode.get()) {
            ((StartActivity) context).restart();
        }
    }

    public void onEvent(BoardManager.BoardsChangedMessage message) {
        updateBoardLinkDescription();
    }

    public void onEvent(ChanSettings.SettingChanged setting) {
        if (setting.setting == ChanSettings.saveLocation) {
            setSaveLocationDescription();
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
            updateVideoLoadModes();
        } else if (item == fontView || item == fontCondensed) {
            EventBus.getDefault().post(new RefreshUIMessage("font"));
        } else if (item == gridColumnsView) {
            EventBus.getDefault().post(new RefreshUIMessage("gridcolumns"));
        } else if (item == textOnly) {
            EventBus.getDefault().post(new RefreshUIMessage("textonly"));
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
        boardEditorView = new LinkSettingView(this, R.string.settings_board_edit, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new BoardEditController(context));
            }
        });
        general.add(boardEditorView);
        updateBoardLinkDescription();

        watchLink = (LinkSettingView) general.add(new LinkSettingView(this, R.string.settings_watch, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new WatchSettingsController(context));
            }
        }));

        groups.add(general);

        SettingsGroup appearance = new SettingsGroup(R.string.settings_group_appearance);

        appearance.add(new LinkSettingView(this, getString(R.string.settings_screen_theme), theme().displayName, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new ThemeSettingsController(context));
            }
        }));

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

        layoutModeView = new ListSettingView<>(this, ChanSettings.layoutMode, R.string.setting_layout_mode, layoutModes);
        appearance.add(layoutModeView);

        List<ListSettingView.Item> fontSizes = new ArrayList<>();
        for (int size = 10; size <= 19; size++) {
            String name = size + (String.valueOf(size).equals(ChanSettings.fontSize.getDefault()) ? " " + getString(R.string.setting_font_size_default) : "");
            fontSizes.add(new ListSettingView.Item<>(name, String.valueOf(size)));
        }

        fontView = appearance.add(new ListSettingView<>(this, ChanSettings.fontSize, R.string.setting_font_size, fontSizes.toArray(new ListSettingView.Item[fontSizes.size()])));
        fontCondensed = appearance.add(new BooleanSettingView(this, ChanSettings.fontCondensed, R.string.setting_font_condensed, R.string.setting_font_condensed_description));

        List<ListSettingView.Item> gridColumns = new ArrayList<>();
        gridColumns.add(new ListSettingView.Item<>(getString(R.string.setting_board_grid_span_count_default), 0));
        for (int columns = 2; columns <= 5; columns++) {
            gridColumns.add(new ListSettingView.Item<>(context.getString(R.string.setting_board_grid_span_count_item, columns), columns));
        }
        gridColumnsView = appearance.add(new ListSettingView<>(this, ChanSettings.boardGridSpanCount, R.string.setting_board_grid_span_count, gridColumns));

        groups.add(appearance);

        // Browsing group
        SettingsGroup browsing = new SettingsGroup(R.string.settings_group_browsing);

        browsing.add(new LinkSettingView(this, R.string.filters_screen, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new FiltersController(context));
            }
        }));
        saveLocation = (LinkSettingView) browsing.add(new LinkSettingView(this, R.string.save_location_screen, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigationController.pushController(new SaveLocationController(context));
            }
        }));
        setSaveLocationDescription();
        browsing.add(new BooleanSettingView(this, ChanSettings.saveBoardFolder, R.string.setting_save_board_folder, R.string.setting_save_board_folder_description));
        browsing.add(new BooleanSettingView(this, ChanSettings.openLinkConfirmation, R.string.setting_open_link_confirmation, 0));
        browsing.add(new BooleanSettingView(this, ChanSettings.autoRefreshThread, R.string.setting_auto_refresh_thread, 0));

        List<ListSettingView.Item> imageAutoLoadTypes = new ArrayList<>();
        List<ListSettingView.Item> videoAutoLoadTypes = new ArrayList<>();
        for (ChanSettings.MediaAutoLoadMode mode : ChanSettings.MediaAutoLoadMode.values()) {
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

            imageAutoLoadTypes.add(new ListSettingView.Item<>(getString(name), mode));
            videoAutoLoadTypes.add(new ListSettingView.Item<>(getString(name), mode));
        }

        imageAutoLoadView = new ListSettingView<>(this, ChanSettings.imageAutoLoadNetwork, R.string.setting_image_auto_load, imageAutoLoadTypes);
        browsing.add(imageAutoLoadView);
        videoAutoLoadView = new ListSettingView<>(this, ChanSettings.videoAutoLoadNetwork, R.string.setting_video_auto_load, videoAutoLoadTypes);
        browsing.add(videoAutoLoadView);
        updateVideoLoadModes();

        browsing.add(new BooleanSettingView(this, ChanSettings.videoOpenExternal, R.string.setting_video_open_external, R.string.setting_video_open_external_description));
        textOnly = new BooleanSettingView(this, ChanSettings.textOnly, R.string.setting_text_only, R.string.setting_text_only_description);
        browsing.add(textOnly);
        browsing.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                databaseManager.clearAllThreadHides();
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
        final String finalVersion = version;

        String userVersion = version + " " + getString(R.string.app_flavor_name);
        about.add(new LinkSettingView(this, getString(R.string.app_name), userVersion, new View.OnClickListener() {
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

        if (((StartActivity) context).getVersionHandler().isUpdatingAvailable()) {
            about.add(new LinkSettingView(this, R.string.settings_update_check, 0, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((StartActivity) context).getVersionHandler().manualUpdateCheck();
                }
            }));
        }

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
                                    String subject = email[1];
                                    subject = subject.replace("__VERSION__", finalVersion);
                                    intent.putExtra(Intent.EXTRA_SUBJECT, subject);
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

    private void updateBoardLinkDescription() {
        List<Board> savedBoards = boardManager.getSavedBoards();
        boardEditorView.setDescription(context.getResources().getQuantityString(R.plurals.board, savedBoards.size(), savedBoards.size()));
    }

    private void setSaveLocationDescription() {
        saveLocation.setDescription(ChanSettings.saveLocation.get());
    }

    private void updateVideoLoadModes() {
        ChanSettings.MediaAutoLoadMode currentImageLoadMode = ChanSettings.imageAutoLoadNetwork.get();
        ChanSettings.MediaAutoLoadMode[] modes = ChanSettings.MediaAutoLoadMode.values();
        boolean enabled = false;
        boolean resetVideoMode = false;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].getName().equals(currentImageLoadMode.getName())) {
                enabled = true;
                if (resetVideoMode) {
                    ChanSettings.videoAutoLoadNetwork.set(modes[i]);
                    videoAutoLoadView.updateSelection();
                    onPreferenceChange(videoAutoLoadView);
                }
            }
            videoAutoLoadView.items.get(i).enabled = enabled;
            if (!enabled && ChanSettings.videoAutoLoadNetwork.get().getName().equals(modes[i].getName())) {
                resetVideoMode = true;
            }
        }
    }
}
