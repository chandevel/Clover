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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.limitcallbacks.LimitCallback;

public class PrimitiveSettingView<T>
        extends SettingView {
    protected final Setting<T> setting;
    private final LimitCallback<T> limitCallback;
    private final String dialogTitle;
    private final String appendBottomDescription;

    public PrimitiveSettingView(
            SettingsController controller,
            Setting<T> setting,
            int name,
            int dialogTitle,
            String appendBottomDescription,
            LimitCallback<T> limit
    ) {
        this(controller, setting, getString(name), getString(dialogTitle), appendBottomDescription, limit);
    }

    public PrimitiveSettingView(
            SettingsController settingsController,
            Setting<T> setting,
            String name,
            String dialogTitle,
            String appendBottomDescription,
            LimitCallback<T> limits
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
        String bot = setting.get() != null ? setting.get().toString() + appendBottomDescription : "";
        return bot.isEmpty() ? "\" \"" : bot;
    }

    public void createEditView(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        updatePaddings(container, dp(24), dp(24), dp(8), 0);

        DialogInterface.OnClickListener clickListener;

        final EditText settingValue = new EditText(v.getContext());
        settingValue.setText(String.valueOf(setting.getForDisplay()));
        settingValue.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        settingValue.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));
        container.addView(settingValue);

        settingValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    if (!limitCallback.isInLimit(setting.convertStringToSettingType(s.toString()))) {
                        settingValue.setError("Valid range is "
                                + limitCallback.getMinimumLimit()
                                + " to "
                                + limitCallback.getMaximumLimit()
                                + ".");
                    } else {
                        settingValue.setError(null);
                    }
                } catch (Exception e) {
                    settingValue.setError("Invalid number entry.");
                }
            }
        });

        clickListener = (d, which) -> {
            if (settingValue.getError() == null) {
                try {
                    setting.set(setting.convertStringToSettingType(settingValue.getText().toString()));
                } catch (Exception e) {
                    showToast(v.getContext(), "Invalid entry entered, set to default.");
                    setting.set(setting.getDefault());
                }
            } else {
                showToast(v.getContext(), "Invalid entry entered, set to default.");
                setting.set(setting.getDefault());
            }
            settingsController.onPreferenceChange(PrimitiveSettingView.this);
        };

        AlertDialog dialog = getDefaultAlertBuilder(v.getContext())
                .setPositiveButton(R.string.ok, clickListener)
                .setNeutralButton(R.string.default_, (d, which) -> {
                    setting.set(setting.getDefault());
                    settingsController.onPreferenceChange(PrimitiveSettingView.this);
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(dialogTitle)
                .setView(container)
                .create();

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
}