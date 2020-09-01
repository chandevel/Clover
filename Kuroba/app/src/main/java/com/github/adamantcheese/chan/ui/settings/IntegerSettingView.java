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

import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

/**
 * Created by Zetsubou on 02.07.2015
 */
public class IntegerSettingView
        extends SettingView
        implements View.OnClickListener {
    private final Setting<Integer> setting;
    private final String dialogTitle;
    private int minimumValue;
    private int maximumValue;

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
        super.setView(view);
        view.setOnClickListener(this);
    }

    @Override
    public String getBottomDescription() {
        return setting.get() != null ? setting.get().toString() : null;
    }

    @Override
    public void onClick(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        container.setPadding(dp(24), dp(8), dp(24), 0);

        final EditText editText = new EditText(v.getContext());
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editText.setText(setting.get().toString());
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);
        editText.setSelection(editText.getText().length());

        container.addView(editText, MATCH_PARENT, WRAP_CONTENT);

        AlertDialog dialog = new AlertDialog.Builder(v.getContext()).setPositiveButton(R.string.ok, (d, which) -> {
            try {
                int value = Integer.parseInt(editText.getText().toString());
                if (value >= minimumValue && value <= maximumValue) {
                    setting.set(value);
                } else {
                    showToast(
                            v.getContext(),
                            "Value not in range <" + minimumValue + ", " + maximumValue + ">, using default of "
                                    + setting.getDefault()
                    );
                    setting.set(setting.getDefault());
                }
            } catch (Exception e) {
                setting.set(setting.getDefault());
            }

            settingsController.onPreferenceChange(IntegerSettingView.this);
        }).setNegativeButton(R.string.cancel, null).setTitle(dialogTitle).setView(container).create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }
}