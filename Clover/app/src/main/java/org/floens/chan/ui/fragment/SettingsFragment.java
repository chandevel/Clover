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
package org.floens.chan.ui.fragment;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.AboutActivity;
import org.floens.chan.ui.activity.BaseActivity;
import org.floens.chan.ui.activity.SettingsActivity;
import org.floens.chan.utils.ThemeHelper;

public class SettingsFragment extends PreferenceFragment {
    private int clickCount = 0;
    private boolean argumentsRead = false;

    private Preference developerPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference);

        Preference aboutLicences = findPreference("about_licences");
        if (aboutLicences != null) {
            aboutLicences.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getActivity(), AboutActivity.class));

                    return true;
                }
            });
        }

        Preference aboutVersion = findPreference("about_version");
        if (aboutVersion != null) {
            aboutVersion.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (++clickCount >= 5) {
                        clickCount = 0;

                        boolean enabled = !ChanSettings.getDeveloper();
                        ChanSettings.setDeveloper(enabled);
                        updateDeveloperPreference();

                        Toast.makeText(getActivity(), (enabled ? "Enabled " : "Disabled ") + "developer options",
                                Toast.LENGTH_LONG).show();
                    }

                    return true;
                }
            });

            String version = "";
            try {
                version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }

            aboutVersion.setTitle(R.string.app_name);
            aboutVersion.setSummary(version);
        }

        developerPreference = findPreference("about_developer");
        ((PreferenceGroup) findPreference("group_about")).removePreference(developerPreference);
        updateDeveloperPreference();

        final ListPreference theme = (ListPreference) findPreference("preference_theme");
        String currentValue = theme.getValue();
        if (currentValue == null) {
            theme.setValue((String) theme.getEntryValues()[0]);
            currentValue = theme.getValue();
        }
        updateSummary(theme, currentValue);

        theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateSummary(theme, newValue.toString());

                // Thanks! https://github.com/CyanogenMod/android_packages_apps_Calculator/blob/cm-10.2/src/com/android/calculator2/view/PreferencesFragment.java
                if (!newValue.toString().equals(ThemeHelper.getInstance().getTheme().name)) {
                    Intent intent = new Intent(getActivity(), SettingsActivity.class);

                    intent.putExtra("pos", getListView().getFirstVisiblePosition());
                    View child = getListView().getChildAt(0);
                    intent.putExtra("off", child != null ? child.getTop() : 0);

                    ((SettingsActivity) getActivity()).restart(intent);
                }

                return true;
            }
        });

        final ListPreference font = (ListPreference) findPreference("preference_font");
        String currentFontValue = font.getValue();
        if (currentFontValue == null) {
            font.setValue((String) font.getEntryValues()[0]);
            currentFontValue = font.getValue();
        }
        updateSummary(font, currentFontValue);

        font.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                updateSummary(font, newValue.toString());
                BaseActivity.doRestartOnResume = true;

                return true;
            }
        });
    }

    public void onStart() {
        super.onStart();

        final Bundle args = getArguments();
        if (args != null && !argumentsRead) {
            argumentsRead = true;
            getListView().setSelectionFromTop(args.getInt("pos", 0), args.getInt("off", 0));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final Preference watchPreference = findPreference("watch_settings");
        if (watchPreference != null) {
            watchPreference.setSummary(ChanSettings.getWatchEnabled() ? R.string.watch_summary_enabled
                    : R.string.watch_summary_disabled);
        }

        final Preference passPreference = findPreference("pass_settings");
        if (passPreference != null) {
            passPreference.setSummary(ChanSettings.getPassEnabled() ? R.string.pass_summary_enabled
                    : R.string.pass_summary_disabled);
        }
    }

    private ListView getListView() {
        return (ListView) getView().findViewById(android.R.id.list);
    }

    private void updateDeveloperPreference() {
        if (ChanSettings.getDeveloper()) {
            ((PreferenceGroup) findPreference("group_about")).addPreference(developerPreference);
        } else {
            ((PreferenceGroup) findPreference("group_about")).removePreference(developerPreference);
        }
    }

    private void updateSummary(ListPreference list, String value) {
        int index = list.findIndexOfValue(value);
        list.setSummary(list.getEntries()[index]);
    }
}
