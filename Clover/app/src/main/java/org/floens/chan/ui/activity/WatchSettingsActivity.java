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
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.utils.ThemeHelper;
import org.floens.chan.utils.Utils;

public class WatchSettingsActivity extends Activity implements OnCheckedChangeListener {
    private Switch watchSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.setTheme(this);

        setFragment(ChanPreferences.getWatchEnabled());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_switch, menu);

        watchSwitch = (Switch) menu.findItem(R.id.action_bar_switch).getActionView();
        watchSwitch.setOnCheckedChangeListener(this);
        watchSwitch.setPadding(0, 0, Utils.dp(14), 0);

        setSwitch(ChanPreferences.getWatchEnabled());

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setFragment(isChecked);
        setSwitch(isChecked);
    }

    private void setSwitch(boolean enabled) {
        watchSwitch.setChecked(enabled);

        ChanPreferences.setWatchEnabled(enabled);

        watchSwitch.setEnabled(false);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                watchSwitch.setEnabled(true);
            }
        }, 500);
    }

    private void setFragment(boolean enabled) {
        if (enabled) {
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(android.R.id.content, new WatchSettingsFragment());
            t.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            t.commit();
        } else {
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(android.R.id.content, TextFragment.newInstance(R.string.watch_info_text));
            t.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            t.commit();
        }
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
            LinearLayout container = new LinearLayout(inflater.getContext());

            int p = Utils.dp(14);
            container.setPadding(p, p, p, p);

            TextView text = new TextView(inflater.getContext());
            text.setTextSize(20);
            text.setText(getArguments().getInt("text_resource"));
            text.setGravity(Gravity.CENTER);

            container.setGravity(Gravity.CENTER);
            container.addView(text);

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
            updateListSummary(backgroundTimeout, currentValue.toString());

            // Timeout is reset when board activity is started
            backgroundTimeout.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    updateListSummary(backgroundTimeout, newValue.toString());
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
