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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class LoginController
        extends Controller
        implements View.OnClickListener, SiteActions.LoginListener {
    private CrossfadeView crossfadeView;
    private TextView errors;
    private Button button;
    private EditText inputToken;
    private EditText inputPin;
    private TextView authenticated;

    private Site site;

    public LoginController(Context context, Site site) {
        super(context);
        this.site = site;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_pass);

        view = inflate(context, R.layout.controller_pass);
        crossfadeView = view.findViewById(R.id.crossfade);
        errors = view.findViewById(R.id.errors);
        button = view.findViewById(R.id.button);
        TextView bottomDescription = view.findViewById(R.id.bottom_description);
        inputToken = view.findViewById(R.id.input_token);
        inputPin = view.findViewById(R.id.input_pin);
        authenticated = view.findViewById(R.id.authenticated);

        errors.setVisibility(GONE);

        final boolean loggedIn = site.actions().isLoggedIn();
        if (loggedIn) {
            button.setText(R.string.setting_pass_logout);
        }
        button.setOnClickListener(this);

        bottomDescription.setText(Html.fromHtml(getString(R.string.setting_pass_bottom_description)));
        bottomDescription.setMovementMethod(LinkMovementMethod.getInstance());

        LoginRequest loginDetails = site.actions().getLoginDetails();
        inputToken.setText(loginDetails.user);
        inputPin.setText(loginDetails.pass);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        crossfadeView.toggle(!loggedIn, false);
    }

    @Override
    public void onClick(View v) {
        if (v == button) {
            if (site.actions().isLoggedIn()) {
                site.actions().logout();
                crossfadeView.toggle(true, true);
                button.setText(R.string.submit);
                hideError();
            } else {
                auth();
            }
        }
    }

    @Override
    public void onLoginComplete(HttpCall httpCall, LoginResponse loginResponse) {
        if (loginResponse.success) {
            authSuccess(loginResponse);
        } else {
            authFail(loginResponse);
        }

        authAfter();
    }

    @Override
    public void onLoginError(HttpCall httpCall) {
        authFail(null);
        authAfter();
    }

    private void authSuccess(LoginResponse response) {
        crossfadeView.toggle(false, true);
        button.setText(R.string.setting_pass_logout);
        authenticated.setText(response.message);
    }

    private void authFail(LoginResponse response) {
        String message = getString(R.string.setting_pass_error);
        if (response != null && response.message != null) {
            message = response.message;
        }

        showError(message);
        button.setText(R.string.submit);
    }

    private void authAfter() {
        button.setEnabled(true);
        inputToken.setEnabled(true);
        inputPin.setEnabled(true);
    }

    private void auth() {
        hideKeyboard(view);
        inputToken.setEnabled(false);
        inputPin.setEnabled(false);
        button.setEnabled(false);
        button.setText(R.string.setting_pass_logging_in);
        hideError();

        String user = inputToken.getText().toString();
        String pass = inputPin.getText().toString();
        site.actions().login(new LoginRequest(user, pass), this);
    }

    private void showError(String error) {
        errors.setText(error);
        errors.setVisibility(VISIBLE);
    }

    private void hideError() {
        errors.setText(null);
        errors.setVisibility(GONE);
    }
}
