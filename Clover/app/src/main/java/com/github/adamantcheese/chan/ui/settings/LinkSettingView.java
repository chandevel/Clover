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
package com.github.adamantcheese.chan.ui.settings;

import android.view.View;

import com.adamantcheese.github.chan.R;

public class LinkSettingView extends SettingView {
    private final View.OnClickListener clickListener;
    private String description;
    private boolean built = false;

    public LinkSettingView(SettingsController settingsController, int name, int description, View.OnClickListener clickListener) {
        this(settingsController, getString(name), getString(description), clickListener);
    }

    public LinkSettingView(SettingsController settingsController, String name, String description, View.OnClickListener clickListener) {
        super(settingsController, name);
        this.description = description;
        this.clickListener = clickListener;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(clickListener);
        built = true;
    }

    @Override
    public String getBottomDescription() {
        return description;
    }

    @Override
    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
        view.findViewById(R.id.top).setEnabled(enabled);
        View bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setEnabled(enabled);
        }
    }

    public void setDescription(int description) {
        setDescription(getString(description));
    }

    public void setDescription(String description) {
        this.description = description;
        if (built) {
            settingsController.onPreferenceChange(this);
        }
    }
}
