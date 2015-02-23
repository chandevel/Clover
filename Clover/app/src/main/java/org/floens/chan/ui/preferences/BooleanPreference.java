package org.floens.chan.ui.preferences;

import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.CompoundButton;

import org.floens.chan.R;
import org.floens.chan.core.preferences.Preference;

public class BooleanPreference extends PreferenceItem implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private SwitchCompat switcher;
    private Preference<Boolean> preference;

    public BooleanPreference(PreferencesController preferencesController, Preference<Boolean> preference, String name) {
        super(preferencesController, name);
        this.preference = preference;
    }

    @Override
    public void setView(View view) {
        super.setView(view);

        view.setOnClickListener(this);

        switcher = (SwitchCompat) view.findViewById(R.id.switcher);
        switcher.setOnCheckedChangeListener(this);

        switcher.setChecked(preference.get());
    }

    @Override
    public void onClick(View v) {
        switcher.toggle();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        preference.set(isChecked);
    }
}
