package org.floens.chan.fragment;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.activity.AboutActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.widget.Toast;

public class SettingsFragment extends PreferenceFragment {
    private int clickCount = 0;
    
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
                        
                        SharedPreferences p = ChanApplication.getPreferences();
                        boolean e = !p.getBoolean("preference_br", false);
                        p.edit().putBoolean("preference_br", e).commit();
                        String m = e ? "Do a barrel roll" : "No barrel rolls this time";
                        Toast.makeText(getActivity(), m, Toast.LENGTH_LONG).show();
                        /*
                         * if (PreferenceManager.getDefaultSharedPreferences(baseActivity).getBoolean("preference_br", false)) {
                               view.animate().setDuration(1000).rotation(Math.random() < 0.5d ? 540f : -360f).setInterpolator(new DecelerateInterpolator(4f));
                           }
                         */
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
    }
}
