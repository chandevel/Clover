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

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.manager.ReplyManager.PassResponse;
import org.floens.chan.core.model.Pass;
import org.floens.chan.ui.ThemeActivity;
import org.floens.chan.utils.AndroidUtils;

public class PassSettingsActivity extends ThemeActivity implements OnCheckedChangeListener {
    private SwitchCompat onSwitch;
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
                onSwitch.toggle();
            }
        });

        toggleStatus = (TextView) findViewById(R.id.toggle_status);
        onSwitch = (SwitchCompat) findViewById(R.id.toggle);
        onSwitch.setOnCheckedChangeListener(this);
        setSwitch(ChanPreferences.getPassEnabled());

        setFragment(ChanPreferences.getPassEnabled());
    }

    @Override
    public void onPause() {
        super.onPause();

        if (TextUtils.isEmpty(ChanPreferences.getPassId())) {
            ChanPreferences.setPassEnabled(false);
            setSwitch(false);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        setFragment(isChecked);
        setSwitch(isChecked);
    }

    private void setSwitch(boolean enabled) {
        onSwitch.setChecked(enabled);
        toggleStatus.setText(enabled ? R.string.on : R.string.off);

        ChanPreferences.setPassEnabled(enabled);
    }

    private void setFragment(boolean enabled) {
        if (enabled) {
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(R.id.content, new PassSettingsFragment());
            t.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            t.commit();
        } else {
            FragmentTransaction t = getFragmentManager().beginTransaction();
            t.replace(R.id.content, new TextFragment());
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
                    AndroidUtils.openLink(v.getContext().getString(R.string.pass_info_link));
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

            Preference login = findPreference("preference_pass_login");
            login.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Pass pass = new Pass(ChanPreferences.getPassToken(), ChanPreferences.getPassPin());
                    onLoginClick(pass);

                    return true;
                }
            });

            updateLoginButton();
        }

        private void updateLoginButton() {
            findPreference("preference_pass_login").setTitle(TextUtils.isEmpty(ChanPreferences.getPassId()) ? R.string.pass_login : R.string.pass_logout);
        }

        private void onLoginClick(Pass pass) {
            if (TextUtils.isEmpty(ChanPreferences.getPassId())) {
                // Login
                final ProgressDialog dialog = ProgressDialog.show(getActivity(), null, "Logging in");

                ChanApplication.getReplyManager().sendPass(pass, new ReplyManager.PassListener() {
                    @Override
                    public void onResponse(PassResponse response) {
                        dialog.dismiss();

                        if (getActivity() == null)
                            return;

                        if (response.unknownError) {
                            WebView webView = new WebView(getActivity());
                            WebSettings settings = webView.getSettings();
                            settings.setSupportZoom(true);

                            webView.loadData(response.responseData, "text/html", null);

                            new AlertDialog.Builder(getActivity()).setView(webView).setNeutralButton(R.string.ok, null).show();
                        } else {
                            new AlertDialog.Builder(getActivity()).setMessage(response.message)
                                    .setNeutralButton(R.string.ok, null).show();
                            ChanPreferences.setPassId(response.passId);
                        }

                        updateLoginButton();
                    }
                });
            } else {
                // Logout
                ChanPreferences.setPassId("");
                updateLoginButton();
            }
        }
    }
}
