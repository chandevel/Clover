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
package org.floens.chan.ui.activity;

import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.ui.fragment.FolderPickFragment;
import org.floens.chan.utils.ThemeHelper;

import java.io.File;

public class AdvancedSettingsActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.setTheme(this);

        getFragmentManager().beginTransaction().replace(android.R.id.content, new AdvancedSettingsFragment()).commit();
    }

    public static class AdvancedSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preference_advanced);

            findPreference("preference_force_phone_layout").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    BaseActivity.doRestartOnResume = true;
                    return true;
                }
            });

            findPreference("preference_anonymize").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    BaseActivity.doRestartOnResume = true;
                    return true;
                }
            });

            findPreference("preference_anonymize_ids").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    BaseActivity.doRestartOnResume = true;
                    return true;
                }
            });

            final ListPreference boardMode = (ListPreference) findPreference("preference_board_mode");
            String currentModeValue = boardMode.getValue();
            if (currentModeValue == null) {
                boardMode.setValue((String) boardMode.getEntryValues()[0]);
                currentModeValue = boardMode.getValue();
            }
            updateSummary(boardMode, currentModeValue);
            boardMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateSummary(boardMode, newValue.toString());
                    BaseActivity.doRestartOnResume = true;
                    return true;
                }
            });

            reloadSavePath();
            final Preference saveLocation = findPreference("preference_image_save_location");
            saveLocation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    File dir = ChanPreferences.getImageSaveDirectory();
                    dir.mkdirs();

                    FolderPickFragment frag = FolderPickFragment.newInstance(new FolderPickFragment.FolderPickListener() {
                        @Override
                        public void folderPicked(File path) {
                            ChanPreferences.setImageSaveDirectory(path);
                            reloadSavePath();
                        }
                    }, dir);
                    getActivity().getFragmentManager().beginTransaction().add(frag, null).commit();

                    return true;
                }
            });
        }

        private void reloadSavePath() {
            Preference saveLocation = findPreference("preference_image_save_location");
            saveLocation.setSummary(ChanPreferences.getImageSaveDirectory().getAbsolutePath());
        }

        private void updateSummary(ListPreference list, String value) {
            int index = list.findIndexOfValue(value);
            list.setSummary(list.getEntries()[index]);
        }
    }
}
