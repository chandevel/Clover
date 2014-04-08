package org.floens.chan.ui.activity;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.utils.Utils;

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

public class WatchSettingsActivity extends Activity implements OnCheckedChangeListener {
    private Switch watchSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.watch_settings, menu);

        watchSwitch = (Switch) menu.findItem(R.id.enable_watch_switch).getActionView();
        watchSwitch.setOnCheckedChangeListener(this);
        watchSwitch.setPadding(0, 0, Utils.dp(this, 14), 0);

        setEnabled(ChanPreferences.getWatchEnabled());

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setEnabled(isChecked);
    }

    private void setEnabled(boolean enabled) {
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

            int p = Utils.dp(inflater.getContext(), 14);
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

            // final Preference backgroundEnabled =
            // findPreference("preference_watch_background_enabled");

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
