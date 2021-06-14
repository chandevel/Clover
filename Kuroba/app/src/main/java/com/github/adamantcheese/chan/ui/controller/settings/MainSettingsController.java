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

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseSiteManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.FiltersController;
import com.github.adamantcheese.chan.ui.controller.ReportProblemController;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.controller.crashlogs.ReviewCrashLogsController;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;

public class MainSettingsController
        extends SettingsController {
    private LinkSettingView watchLink;
    private LinkSettingView sitesSetting;
    private LinkSettingView filtersSetting;
    private BooleanSettingView collectCrashLogsSettingView;

    @Inject
    private DatabaseSiteManager databaseSiteManager;

    @Inject
    private DatabaseFilterManager databaseFilterManager;

    @Inject
    private ReportManager reportManager;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen);
    }

    @Override
    public void onShow() {
        super.onShow();

        int siteCount = DatabaseUtils.runTask(databaseSiteManager.getCount());
        int filterCount = DatabaseUtils.runTask(databaseFilterManager.getCount());

        sitesSetting.setDescription(getQuantityString(R.plurals.site, siteCount, siteCount));
        filtersSetting.setDescription(getQuantityString(R.plurals.filter, filterCount, filterCount));
        watchLink.setDescription(ChanSettings.watchEnabled.get() ? (ChanSettings.watchBackground.get()
                ? R.string.setting_watch_summary_enabled_background
                : R.string.setting_watch_summary_enabled) : R.string.setting_watch_summary_disabled);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == collectCrashLogsSettingView) {
            if (!ChanSettings.collectCrashLogs.get()) {
                // If disabled delete all already collected crash logs to cancel the notification
                // (if it's shown) and to avoid showing notification afterwards.

                reportManager.deleteAllCrashLogs();
            }
        }
    }

    @Override
    public void populatePreferences() {
        // General group
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_settings);

            watchLink = general.add(new LinkSettingView(this,
                    R.string.settings_watch,
                    R.string.empty,
                    (v, sv) -> navigationController.pushController(new WatchSettingsController(context))
            ));

            sitesSetting = general.add(new LinkSettingView(this,
                    R.string.settings_sites,
                    R.string.empty,
                    (v, sv) -> navigationController.pushController(new SitesSetupController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_appearance,
                    R.string.settings_appearance_description,
                    (v, sv) -> navigationController.pushController(new AppearanceSettingsController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_behavior,
                    R.string.settings_behavior_description,
                    (v, sv) -> navigationController.pushController(new BehaviourSettingsController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_media,
                    R.string.settings_media_description,
                    (v, sv) -> navigationController.pushController(new MediaSettingsController(context))
            ));

            general.add(new LinkSettingView(this,
                    R.string.settings_import_export,
                    R.string.settings_import_export_description,
                    (v, sv) -> navigationController.pushController(new ImportExportSettingsController(context,
                            () -> navigationController.popController()
                    ))
            ));

            filtersSetting = general.add(new LinkSettingView(this,
                    R.string.settings_filters,
                    R.string.empty,
                    (v, sv) -> navigationController.pushController(new FiltersController(context))
            ));

            groups.add(general);
        }

        setupAboutGroup();
    }

    private void setupAboutGroup() {
        SettingsGroup about = new SettingsGroup(R.string.settings_group_about);

        LinkSettingView updateSettingView = new LinkSettingView(this,
                BuildConfig.APP_LABEL + " " + BuildConfig.VERSION_NAME,
                "Tap to check for updates",
                (v, sv) -> ((StartActivity) context).getUpdateManager().manualUpdateCheck()
        );
        updateSettingView.settingNotificationType = SettingNotification.ApkUpdate;
        about.add(updateSettingView);

        LinkSettingView reportSettingView = new LinkSettingView(this,
                R.string.settings_report,
                R.string.settings_report_description,
                (v, sv) -> onReportSettingClick()
        );
        reportSettingView.settingNotificationType = SettingNotification.CrashLog;
        about.add(reportSettingView);

        about.add(collectCrashLogsSettingView = new BooleanSettingView(this,
                ChanSettings.collectCrashLogs,
                R.string.settings_collect_crash_logs,
                R.string.settings_collect_crash_logs_description
        ));
        about.add(new LinkSettingView(this,
                "Find " + BuildConfig.APP_LABEL + " on GitHub",
                "View the source code, give feedback, submit bug reports",
                (v, sv) -> openLink(BuildConfig.GITHUB_ENDPOINT)
        ));

        about.add(new LinkSettingView(this,
                R.string.settings_about_license,
                R.string.settings_about_license_description,
                (v, sv) -> openLinkInBrowser(context, "https://www.gnu.org/licenses/gpl-3.0.en.html")
        ));

        about.add(new LinkSettingView(this,
                R.string.settings_about_licenses,
                R.string.settings_about_licenses_description,
                (v, sv) -> openLinkInBrowser(context,
                        "https://htmlpreview.github.io/?" + BuildConfig.RESOURCES_ENDPOINT + "licenses.html"
                )
        ));

        about.add(new LinkSettingView(this,
                R.string.settings_developer,
                R.string.empty,
                (v, sv) -> navigationController.pushController(new DeveloperSettingsController(context))
        ));

        groups.add(about);
    }

    private void onReportSettingClick() {
        int crashLogsCount = reportManager.countCrashLogs();

        if (crashLogsCount > 0) {
            getDefaultAlertBuilder(context).setTitle(getString(R.string.settings_report_suggest_sending_logs_title,
                    crashLogsCount
            ))
                    .setMessage(R.string.settings_report_suggest_sending_logs)
                    .setPositiveButton(R.string.settings_report_review_button_text,
                            (dialog, which) -> navigationController.pushController(new ReviewCrashLogsController(context))
                    )
                    .setNeutralButton(R.string.settings_report_review_later_button_text,
                            (dialog, which) -> openReportProblemController()
                    )
                    .setNegativeButton(R.string.settings_report_delete_all_crash_logs, (dialog, which) -> {
                        reportManager.deleteAllCrashLogs();
                        openReportProblemController();
                    })
                    .create()
                    .show();
            return;
        }

        openReportProblemController();
    }

    private void openReportProblemController() {
        navigationController.pushController(new ReportProblemController(context));
    }
}
