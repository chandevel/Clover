package org.floens.chan.ui.settings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.Setting;

import static org.floens.chan.utils.AndroidUtils.dp;

/**
 * Created by Zetsubou on 02.07.2015.
 */
public class IntegerSettingView extends SettingView implements View.OnClickListener {
    private final Setting<Integer> setting;
    private final String dialogTitle;

    public IntegerSettingView(SettingsController settingsController, Setting<Integer> setting, String name, String dialogTitle) {
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

        container.addView(editText, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        AlertDialog dialog = new AlertDialog.Builder(v.getContext())
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            setting.set(Integer.parseInt(editText.getText().toString()));
                        } catch (Exception e) {
                            setting.set(setting.getDefault());
                        }

                        settingsController.onPreferenceChange(IntegerSettingView.this);
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