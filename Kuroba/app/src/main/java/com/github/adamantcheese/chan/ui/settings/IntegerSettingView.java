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

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

/**
 * Created by Zetsubou on 02.07.2015
 */
public class IntegerSettingView
        extends SettingView
        implements View.OnClickListener {
    private final Setting<Integer> setting;
    private final String dialogTitle;
    private final int minimumValue;
    private final int maximumValue;

    public IntegerSettingView(
            SettingsController controller,
            Setting<Integer> setting,
            int name,
            int dialogTitle,
            Pair<Integer, Integer> limits
    ) {
        this(controller, setting, getString(name), getString(dialogTitle), limits);
    }

    public IntegerSettingView(
            SettingsController settingsController,
            Setting<Integer> setting,
            String name,
            String dialogTitle,
            Pair<Integer, Integer> limits
    ) {
        super(settingsController, name);
        this.setting = setting;
        this.dialogTitle = dialogTitle;
        minimumValue = limits.first;
        maximumValue = limits.second;
    }

    @Override
    public void setView(View view) {
        view.setOnClickListener(this);
        super.setView(view);
    }

    @Override
    public String getBottomDescription() {
        return setting.get() != null ? setting.get().toString() : "";
    }

    @Override
    public void onClick(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        container.setPadding(dp(24), dp(24), dp(24), 0);

        final SeekBar rangeSlider = new SeekBar(v.getContext());
        rangeSlider.setProgress(convertRangeToProgress(setting.get()));
        rangeSlider.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
        container.addView(rangeSlider);

        final TextView max = new TextView(v.getContext());
        max.setText(String.valueOf(setting.get()));
        max.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(max, WRAP_CONTENT, MATCH_PARENT);

        rangeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                max.setText(String.valueOf(convertProgressToRange(progress)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        AlertDialog dialog = getDefaultAlertBuilder(v.getContext()).setPositiveButton(R.string.ok, (d, which) -> {
            setting.set(convertProgressToRange(rangeSlider.getProgress()));
            settingsController.onPreferenceChange(IntegerSettingView.this);
        }).setNeutralButton(R.string.default_, (d, which) -> {
            setting.set(setting.getDefault());
            settingsController.onPreferenceChange(IntegerSettingView.this);
        }).setNegativeButton(R.string.cancel, null).setTitle(dialogTitle).setView(container).create();

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * @param progress Progress in range 0 to 100
     * @return Converted to range minimumValue to maximumValue
     */
    private int convertProgressToRange(int progress) {
        return Math.round(minimumValue + (progress / 100f * (maximumValue - minimumValue)));
    }

    /**
     * @param value Value in range minimumValue to maximumValue
     * @return Converted to range 0 to 100
     */
    private int convertRangeToProgress(int value) {
        return Math.round((((float) value) - minimumValue) / (maximumValue - minimumValue) * 100);
    }
}