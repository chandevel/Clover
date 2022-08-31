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

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.core.manager.SettingNotificationManager.SettingNotificationType.APK_UPDATE;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;

import android.animation.*;
import android.content.Context;

import com.github.adamantcheese.chan.*;
import com.github.adamantcheese.chan.core.database.*;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.ui.controller.FiltersController;
import com.github.adamantcheese.chan.ui.controller.SitesSetupController;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.BuildConfigUtils;

import javax.inject.Inject;

import kotlin.random.Random;
import pl.droidsonroids.gif.GifImageView;

public class MainSettingsController
        extends SettingsController {
    private LinkSettingView watchLink;
    private LinkSettingView sitesSetting;
    private LinkSettingView filtersSetting;

    @Inject
    private DatabaseSiteManager databaseSiteManager;

    @Inject
    private DatabaseFilterManager databaseFilterManager;

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

        sitesSetting.setDescription(getQuantityString(R.plurals.site, siteCount));
        filtersSetting.setDescription(getQuantityString(R.plurals.filter, filterCount));
        watchLink.setDescription(ChanSettings.watchEnabled.get() ? (ChanSettings.watchBackground.get()
                ? R.string.setting_watch_summary_enabled_background
                : R.string.setting_watch_summary_enabled) : R.string.setting_watch_summary_disabled);
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

        LinkSettingView updateSettingView =
                new LinkSettingView(this, BuildConfigUtils.VERSION, "Tap to check for updates", (v, sv) -> {
                    ((StartActivity) context).getUpdateManager().manualUpdateCheck();
                    if (PersistableChanState.noFunAllowed.get()) return;
                    showToast(context, "Shoutouts to  nnuudev and BlueClover!");
                    for (int i = 0; i < 10; i++) {
                        addPony(i);
                    }
                });
        updateSettingView.forType.addType(APK_UPDATE);
        about.add(updateSettingView);

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

    private final int[] PONIES = {
            R.drawable.trotcycle_berry_right,
            R.drawable.trotcycle_berry_left,
            R.drawable.trotcycle_pinkiepie_right_n_n,
            R.drawable.trotcycle_pinkiepie_left_n_n
    };
    private final boolean[] FACING_LEFT = {false, true, false, true};

    private void addPony(int i) {
        final GifImageView iv = new GifImageView(context);
        int picked = Random.Default.nextInt(PONIES.length);
        iv.setImageResource(PONIES[picked]);
        iv.setX(FACING_LEFT[picked]
                ? navigationController.view.getWidth() + 100
                : -iv.getDrawable().getIntrinsicWidth() - 100);
        iv.setY(navigationController.view.getHeight() - iv.getDrawable().getIntrinsicHeight());
        navigationController.view.addView(iv);
        iv.getLayoutParams().width = WRAP_CONTENT;
        iv.getLayoutParams().height = WRAP_CONTENT;
        iv.setLayoutParams(iv.getLayoutParams());

        ValueAnimator animator = ValueAnimator.ofFloat(FACING_LEFT[picked]
                        ? navigationController.view.getWidth() + 100
                        : -iv.getDrawable().getIntrinsicWidth() - 100,
                FACING_LEFT[picked]
                        ? -iv.getDrawable().getIntrinsicWidth() - 100
                        : navigationController.view.getWidth() + 100
        );
        animator.setDuration(7500);
        animator.addUpdateListener(animation -> iv.setX((float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                navigationController.view.removeView(iv);
            }
        });
        animator.setStartDelay((long) Random.Default.nextInt(1, 4) * i * 250);
        animator.start();
    }
}
