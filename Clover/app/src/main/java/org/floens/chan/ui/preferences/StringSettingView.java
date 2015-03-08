package org.floens.chan.ui.preferences;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.Setting;

import static org.floens.chan.utils.AndroidUtils.dp;

public class StringSettingView extends SettingView implements View.OnClickListener {
    private final Setting<String> setting;
    private final String dialogTitle;

    public StringSettingView(SettingsController settingsController, Setting<String> setting, String name, String dialogTitle) {
        super(settingsController, name);
        this.setting = setting;
        this.dialogTitle = dialogTitle;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(this);
    }

    @Override
    public String getBottomDescription() {
        return setting.get().length() > 0 ? setting.get() : null;
    }

    @Override
    public void onClick(View v) {
        LinearLayout container = new LinearLayout(v.getContext());
        container.setPadding(dp(24), dp(8), dp(24), 0);

        final EditText editText = new EditText(v.getContext());
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editText.setText(setting.get());
        editText.setSingleLine(true);

        container.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        AlertDialog dialog = new AlertDialog.Builder(v.getContext())
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        setting.set(editText.getText().toString());
                        settingsController.onPreferenceChange(StringSettingView.this);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(dialogTitle)
                .setView(container)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }
}
