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

import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.limitcallbacks.LimitCallback;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

public class SeekbarSettingView
        extends SettingView {
    protected final Setting<Integer> setting;
    private final LimitCallback<Integer> limitCallback;
    private final String dialogTitle;
    private final String appendBottomDescription;

    public SeekbarSettingView(
            SettingsController controller,
            Setting<Integer> setting,
            int name,
            int dialogTitle,
            String appendBottomDescription,
            LimitCallback<Integer> limits
    ) {
        this(controller, setting, getString(name), getString(dialogTitle), appendBottomDescription, limits);
    }

    public SeekbarSettingView(
            SettingsController settingsController,
            Setting<Integer> setting,
            String name,
            String dialogTitle,
            String appendBottomDescription,
            LimitCallback<Integer> limits
    ) {
        super(settingsController, name);
        this.setting = setting;
        this.dialogTitle = dialogTitle;
        limitCallback = limits;
        this.appendBottomDescription = appendBottomDescription == null ? "" : appendBottomDescription;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        if (view == null) return;
        view.setOnClickListener(this::createEditView);
    }

    @Override
    public String getBottomDescription() {
        return setting.get() != null ? setting.get().toString() + appendBottomDescription : "";
    }

    public void createEditView(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        updatePaddings(container, dp(24), dp(24), dp(24), 0);

        DialogInterface.OnClickListener clickListener;

        final SeekBar rangeSlider = new SeekBar(v.getContext());
        rangeSlider.setKeyProgressIncrement(1);
        rangeSlider.setProgress(convertRangeToProgress(setting.get(), rangeSlider.getMax()));
        rangeSlider.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
        container.addView(rangeSlider);

        final TextView max = new TextView(v.getContext());
        max.setText(String.valueOf(setting.get()));
        max.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(max, WRAP_CONTENT, MATCH_PARENT);

        rangeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                max.setText(String.valueOf(convertProgressToRange(progress, seekBar.getMax())));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        clickListener = (d, which) -> {
            setting.set(convertProgressToRange(rangeSlider.getProgress(), rangeSlider.getMax()));
            settingsController.onPreferenceChange(SeekbarSettingView.this);
        };

        AlertDialog dialog = getDefaultAlertBuilder(v.getContext()).setPositiveButton(R.string.ok, clickListener)
                .setNeutralButton(R.string.default_, (d, which) -> {
                    setting.set(setting.getDefault());
                    settingsController.onPreferenceChange(SeekbarSettingView.this);
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(dialogTitle)
                .setView(container)
                .create();

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    /**
     * @param progress Progress in range 0 to SEEK_BAR_MAX
     * @return Converted to range minimumValue to maximumValue
     */
    private int convertProgressToRange(int progress, int max) {
        int minLimit = limitCallback.getMinimumLimit();
        int maxLimit = limitCallback.getMaximumLimit();
        return Math.round(minLimit + (progress / (float) max * (maxLimit - minLimit)));
    }

    /**
     * @param value Value in range minimumValue to maximumValue
     * @return Converted to range 0 to SEEK_BAR_MAX
     */
    private int convertRangeToProgress(int value, int max) {
        int minLimit = limitCallback.getMinimumLimit();
        int maxLimit = limitCallback.getMaximumLimit();
        return Math.round((value - minLimit) / (float) (maxLimit - minLimit) * max);
    }
}