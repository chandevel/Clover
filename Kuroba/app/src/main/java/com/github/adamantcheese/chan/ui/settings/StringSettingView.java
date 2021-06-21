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

import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class StringSettingView
        extends SettingView
        implements View.OnClickListener {
    private final Setting<String> setting;
    private final String dialogTitle;
    private final String subTitle;

    public StringSettingView(
            SettingsController controller, Setting<String> setting, int name, int dialogTitle, int subTitle
    ) {
        this(controller, setting, getString(name), getString(dialogTitle), getString(subTitle));
    }

    public StringSettingView(
            SettingsController settingsController,
            Setting<String> setting,
            String name,
            String dialogTitle,
            String subTitle
    ) {
        super(settingsController, name);
        this.setting = setting;
        this.dialogTitle = dialogTitle;
        this.subTitle = TextUtils.isEmpty(subTitle) ? null : subTitle;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        if (view == null) return;
        view.setOnClickListener(this);
    }

    @Override
    public String getBottomDescription() {
        return setting.get().length() > 0 ? setting.get() : "\" \"";
    }

    @Override
    public void onClick(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        container.setPadding(dp(24), dp(8), dp(24), 0);

        final EditText editText = new EditText(v.getContext());
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editText.setText(setting.get());
        editText.setHint(setting.getDefault().isEmpty() ? "\" \"" : setting.getDefault());
        editText.setSingleLine(true);
        editText.setSelection(editText.getText().length());

        container.addView(editText, MATCH_PARENT, WRAP_CONTENT);

        AlertDialog dialog = getDefaultAlertBuilder(v.getContext()).setPositiveButton(R.string.ok, (d, which) -> {
            setting.set(editText.getText().toString());
            settingsController.onPreferenceChange(StringSettingView.this);
        })
                .setNeutralButton(R.string.default_, ((dialog1, which) -> {
                    setting.set(setting.getDefault());
                    settingsController.onPreferenceChange(StringSettingView.this);
                }))
                .setNegativeButton(R.string.cancel, null)
                .setTitle(dialogTitle)
                .setMessage(subTitle)
                .setView(container)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }
}
