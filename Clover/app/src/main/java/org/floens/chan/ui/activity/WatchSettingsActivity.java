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

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.ThemeActivity;

public class WatchSettingsActivity extends ThemeActivity implements OnCheckedChangeListener {
    private SwitchCompat watchSwitch;
    private TextView toggleStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme();
        setContentView(R.layout.header_switch_layout);
        setToolbar();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.toggle_bar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                watchSwitch.toggle();
            }
        });

        toggleStatus = (TextView) findViewById(R.id.toggle_status);
        watchSwitch = (SwitchCompat) findViewById(R.id.toggle);
        watchSwitch.setOnCheckedChangeListener(this);
        setSwitch(ChanSettings.getWatchEnabled());

        setFragment(ChanSettings.getWatchEnabled());
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setFragment(isChecked);
        setSwitch(isChecked);
    }

    private void setSwitch(boolean enabled) {
        watchSwitch.setChecked(enabled);
        toggleStatus.setText(enabled ? R.string.on : R.string.off);

        ChanSettings.setWatchEnabled(enabled);
    }

    private void setFragment(boolean enabled) {
        FragmentTransaction t = getFragmentManager().beginTransaction();
        if (enabled) {
            t.replace(R.id.content, new WatchSettingsFragment());
        } else {
            t.replace(R.id.content, TextFragment.newInstance(R.string.watch_info_text));
        }
        t.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        t.commit();
    }

    public static class TextFragment extends Fragment {
        public static TextFragment newInstance(int textResource) {
            TextFragment f = new TextFragment();
            Bundle bundle = new Bundle();
            bundle.putInt("text_resource", textResource);
            f.setArguments(bundle);
            return f;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup group, Bundle savedInstanceState) {
            ViewGroup container = (ViewGroup) inflater.inflate(R.layout.watch_description, null);

            TextView text = (TextView) container.findViewById(R.id.text);
            text.setText(getArguments().getInt("text_resource"));

            return container;
        }
    }

    public static class WatchSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preference_watch);

            final ListPreference backgroundTimeout = (ListPreference) findPreference("preference_watch_background_timeout");
            String currentValue = backgroundTimeout.getValue();
            if (currentValue == null) {
                backgroundTimeout.setValue((String) backgroundTimeout.getEntryValues()[0]);
                currentValue = backgroundTimeout.getValue();
            }
            updateListSummary(backgroundTimeout, currentValue);

            backgroundTimeout.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateListSummary(backgroundTimeout, newValue.toString());
                    return true;
                }
            });

            final ListPreference notifyMode = (ListPreference) findPreference("preference_watch_notify_mode");
            String currentNotifyMode = notifyMode.getValue();
            if (currentNotifyMode == null) {
                notifyMode.setValue((String) notifyMode.getEntryValues()[0]);
                currentNotifyMode = notifyMode.getValue();
            }
            updateListSummary(notifyMode, currentNotifyMode);

            notifyMode.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateListSummary(notifyMode, newValue.toString());
                    return true;
                }
            });

            final ListPreference sound = (ListPreference) findPreference("preference_watch_sound");
            String currentSound = sound.getValue();
            if (currentSound == null) {
                sound.setValue((String) sound.getEntryValues()[0]);
                currentSound = sound.getValue();
            }
            updateListSummary(sound, currentSound);

            sound.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateListSummary(sound, newValue.toString());
                    return true;
                }
            });

            final ListPreference led = (ListPreference) findPreference("preference_watch_led");
            String currentLed = led.getValue();
            if (currentLed == null) {
                led.setValue((String) led.getEntryValues()[0]);
                currentLed = led.getValue();
            }
            updateListSummary(led, currentLed);

            led.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateListSummary(led, newValue.toString());
                    return true;
                }
            });

            findPreference("preference_watch_background_enabled").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    ChanApplication.getWatchManager().onBackgroundWatchingChanged((Boolean) newValue);

                    return true;
                }
            });
        }

        private void updateListSummary(ListPreference backgroundTimeout, String value) {
            int index = backgroundTimeout.findIndexOfValue(value);
            backgroundTimeout.setSummary(backgroundTimeout.getEntries()[index]);
        }
    }
}
