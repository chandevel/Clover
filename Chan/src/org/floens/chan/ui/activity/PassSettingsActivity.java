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
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;

public class PassSettingsActivity extends Activity implements OnCheckedChangeListener {
    private Switch enableSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setFragment(ChanPreferences.getPassEnabled());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_switch, menu);

        enableSwitch = (Switch) menu.findItem(R.id.action_bar_switch).getActionView();
        enableSwitch.setOnCheckedChangeListener(this);
        enableSwitch.setPadding(0, 0, Utils.dp(14), 0);

        setSwitch(ChanPreferences.getPassEnabled());

        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setFragment(isChecked);
        setSwitch(isChecked);
    }

    private void setSwitch(boolean enabled) {
        enableSwitch.setChecked(enabled);

        ChanPreferences.setPassEnabled(enabled);

        enableSwitch.setEnabled(false);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                enableSwitch.setEnabled(true);
            }
        }, 500);
    }

    private void setFragment(boolean enabled) {
        if (enabled) {
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(android.R.id.content, new PassSettingsFragment());
            t.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            t.commit();
        } else {
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(android.R.id.content, new TextFragment());
            t.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            t.commit();
        }
    }

    public static class TextFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup group, Bundle savedInstanceState) {
            View container = inflater.inflate(R.layout.preference_pass, null);

            TextView link = (TextView) container.findViewById(R.id.pass_link);
            link.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Utils.openLink(v.getContext(), v.getContext().getString(R.string.pass_info_link));
                }
            });

            return container;
        }
    }

    public static class PassSettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preference_pass);
        }
    }
}
