package org.floens.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.AnimationUtils;

public class PassSettingsController extends Controller implements View.OnClickListener {
    private LinearLayout container;
    private CrossfadeView crossfadeView;
    private TextView errors;
    private Button button;
    private TextView bottomDescription;
    private EditText inputToken;
    private EditText inputPin;
    private TextView authenticated;

    public PassSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.settings_screen_pass);

        view = inflateRes(R.layout.settings_pass);
        container = (LinearLayout) view.findViewById(R.id.container);
        crossfadeView = (CrossfadeView) view.findViewById(R.id.crossfade);
        errors = (TextView) view.findViewById(R.id.errors);
        button = (Button) view.findViewById(R.id.button);
        bottomDescription = (TextView) view.findViewById(R.id.bottom_description);
        inputToken = (EditText) view.findViewById(R.id.input_token);
        inputPin = (EditText) view.findViewById(R.id.input_pin);
        authenticated = (TextView) view.findViewById(R.id.authenticated);

        AnimationUtils.setHeight(errors, false, false);

        final boolean loggedIn = loggedIn();
        button.setText(loggedIn ? R.string.setting_pass_logout : R.string.setting_pass_login);
        button.setOnClickListener(this);

        bottomDescription.setText(Html.fromHtml(string(R.string.setting_pass_bottom_description)));
        bottomDescription.setMovementMethod(LinkMovementMethod.getInstance());

        inputToken.setText(ChanSettings.passToken.get());
        inputPin.setText(ChanSettings.passPin.get());

        AndroidUtils.waitForLayout(view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                crossfadeView.getLayoutParams().height = crossfadeView.getHeight();
                crossfadeView.requestLayout();
                crossfadeView.toggle(!loggedIn, false);
                return true;
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == button) {
            if (loggedIn()) {
                ChanSettings.passId.set("");
                crossfadeView.toggle(true, true);
                button.setText(R.string.setting_pass_login);
                hideError();
                ((PassSettingControllerListener) previousSiblingController).onPassEnabledChanged(false);
            } else {
                auth();
            }
        }
    }

    private void auth() {
        AndroidUtils.hideKeyboard(view);
        inputToken.setEnabled(false);
        inputPin.setEnabled(false);
        button.setEnabled(false);
        button.setText(R.string.setting_pass_logging_in);
        hideError();

        ChanSettings.passToken.set(inputToken.getText().toString());
        ChanSettings.passPin.set(inputPin.getText().toString());

        ChanApplication.getReplyManager().postPass(ChanSettings.passToken.get(), ChanSettings.passPin.get(), new ReplyManager.PassListener() {
            @Override
            public void onResponse(ReplyManager.PassResponse response) {
                if (response.isError) {
                    if (response.unknownError) {
                        WebView webView = new WebView(context);
                        WebSettings settings = webView.getSettings();
                        settings.setSupportZoom(true);
                        webView.loadData(response.responseData, "text/html", null);

                        new AlertDialog.Builder(context)
                                .setView(webView)
                                .setNeutralButton(R.string.ok, null)
                                .show();
                    } else {
                        showError(response.message);
                    }
                    button.setText(R.string.setting_pass_login);
                } else {
                    crossfadeView.toggle(false, true);
                    button.setText(R.string.setting_pass_logout);
                    ChanSettings.passId.set(response.passId);
                    authenticated.setText(response.message);
                    ((PassSettingControllerListener) previousSiblingController).onPassEnabledChanged(true);
                }

                button.setEnabled(true);
                inputToken.setEnabled(true);
                inputPin.setEnabled(true);
            }
        });
    }

    private void showError(String error) {
        errors.setText(error);
        AnimationUtils.setHeight(errors, true, true, container.getWidth());
    }

    private void hideError() {
        errors.setText(null);
        AnimationUtils.setHeight(errors, false, true, container.getHeight());
    }

    private boolean loggedIn() {
        return ChanSettings.passId.get().length() > 0;
    }

    public interface PassSettingControllerListener {
        void onPassEnabledChanged(boolean enabled);
    }
}
