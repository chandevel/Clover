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
import android.view.View;
import android.widget.Toast;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.presenter.SettingsPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.export.ImportExportSettingsController;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class MainSettingsController extends SettingsController implements SettingsPresenter.Callback {
    @Inject
    private SettingsPresenter presenter;

    private LinkSettingView watchLink;
    private SettingView developerView;
    private LinkSettingView sitesSetting;
    private LinkSettingView filtersSetting;

    public MainSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        navigation.setTitle(R.string.settings_screen);

        setupLayout();

        populatePreferences();

        buildPreferences();

        if (!ChanSettings.developer.get()) {
            developerView.view.setVisibility(View.GONE);
        }

        presenter.create(this);
    }

    @Override
    public void onShow() {
        super.onShow();

        presenter.show();
    }

    @Override
    public void setFiltersCount(int count) {
        String filters = context.getResources().getQuantityString(R.plurals.filter, count, count);
        filtersSetting.setDescription(filters);
    }

    @Override
    public void setSiteCount(int count) {
        String sites = context.getResources().getQuantityString(R.plurals.site, count, count);
        sitesSetting.setDescription(sites);
    }

    @Override
    public void setWatchEnabled(boolean enabled) {
        watchLink.setDescription(enabled ?
                R.string.setting_watch_summary_enabled : R.string.setting_watch_summary_disabled);
    }

    private void populatePreferences() {
        // General group
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_settings);

            watchLink = (LinkSettingView) general.add(new LinkSettingView(this,
                    R.string.settings_watch, 0,
                    v -> navigationController.pushController(
                            new WatchSettingsController(context))));

            sitesSetting = (LinkSettingView) general.add(new LinkSettingView(this,
                    R.string.settings_sites, 0,
                    v -> navigationController.pushController(
                            new SitesSetupController(context))));

            general.add(new LinkSettingView(this,
                    R.string.settings_appearance, R.string.settings_appearance_description,
                    v -> navigationController.pushController(
                            new AppearanceSettingsController(context))));

            general.add(new LinkSettingView(this,
                    R.string.settings_behavior, R.string.settings_behavior_description,
                    v -> navigationController.pushController(
                            new BehaviourSettingsController(context))));

            general.add(new LinkSettingView(this,
                    R.string.settings_media, R.string.settings_media_description,
                    v -> navigationController.pushController(
                            new MediaSettingsController(context))));

            general.add(new LinkSettingView(this,
                    R.string.settings_import_export,
                    R.string.settings_import_export_description,
                    v -> navigationController.pushController(new ImportExportSettingsController(context, () -> navigationController.popController())
                    )));

            filtersSetting = (LinkSettingView) general.add(new LinkSettingView(this,
                    R.string.settings_filters, 0,
                    v -> navigationController.pushController(new FiltersController(context))));

            groups.add(general);
        }

        setupAboutGroup();
    }

    private void setupAboutGroup() {
        SettingsGroup about = new SettingsGroup(R.string.settings_group_about);

        about.add(new LinkSettingView(this,
                (String) getApplicationLabel(), BuildConfig.VERSION_NAME,
                v -> {
                    ChanSettings.developer.toggle();
                    Toast.makeText(context, (ChanSettings.developer.get() ? "Enabled" : "Disabled") +
                            " developer options", Toast.LENGTH_LONG).show();
                    developerView.view.setVisibility(ChanSettings.developer.get() ? View.VISIBLE : View.GONE);
                }));

        about.add(new LinkSettingView(this,
                R.string.settings_update_check, 0,
                v -> ((StartActivity) context).getUpdateManager().manualUpdateCheck()));

        about.add(new LinkSettingView(this,
                "Find " + getApplicationLabel() + " on GitHub", "View the source code, give feedback, submit bug reports",
                v -> AndroidUtils.openLink(BuildConfig.ISSUES_ENDPOINT.substring(0, BuildConfig.ISSUES_ENDPOINT.length() - 6))));

        about.add(new LinkSettingView(this,
                R.string.settings_about_license, R.string.settings_about_license_description,
                v -> navigationController.pushController(
                        new LicensesController(context,
                                getString(R.string.settings_about_license),
                                "file:///android_asset/html/license.html"))));

        about.add(new LinkSettingView(this,
                R.string.settings_about_licenses, R.string.settings_about_licenses_description,
                v -> navigationController.pushController(
                        new LicensesController(context,
                                getString(R.string.settings_about_licenses),
                                "file:///android_asset/html/licenses.html"))));

        developerView = about.add(new LinkSettingView(this,
                R.string.settings_developer, 0,
                v -> navigationController.pushController(
                        new DeveloperSettingsController(context))));

        groups.add(about);
    }
}
