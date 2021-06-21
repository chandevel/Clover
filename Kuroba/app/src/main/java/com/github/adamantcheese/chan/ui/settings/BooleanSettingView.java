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
package com.github.adamantcheese.chan.ui.settings;

import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class BooleanSettingView
        extends SettingView
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private Switch switcher;
    private final Setting<Boolean> setting;
    private final String description;

    public BooleanSettingView(SettingsController controller, Setting<Boolean> setting, int name, int description) {
        this(controller, setting, getString(name), getString(description));
    }

    public BooleanSettingView(SettingsController controller, Setting<Boolean> setting, int name, String description) {
        this(controller, setting, getString(name), description);
    }

    public BooleanSettingView(
            SettingsController settingsController, Setting<Boolean> setting, String name, String description
    ) {
        super(settingsController, name);
        this.setting = setting;
        this.description = description;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        if (view == null) return;
        view.setOnClickListener(this);

        switcher = view.findViewById(R.id.switcher);
        switcher.setOnCheckedChangeListener(null);
        switcher.setChecked(setting.get());
        switcher.setOnCheckedChangeListener(this);
    }

    @Override
    public String getBottomDescription() {
        return description;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (switcher != null) {
            switcher.setEnabled(enabled);
        }
    }

    @Override
    public void onClick(View v) {
        switcher.toggle();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (view != null) {
            setting.set(isChecked);
            settingsController.onPreferenceChange(this);
        }
    }
}
