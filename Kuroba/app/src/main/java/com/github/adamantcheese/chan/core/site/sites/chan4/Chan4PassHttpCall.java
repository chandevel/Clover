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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;

import java.net.HttpCookie;
import java.util.List;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public class Chan4PassHttpCall
        extends HttpCall {
    private final LoginRequest loginRequest;
    public final LoginResponse loginResponse = new LoginResponse();

    public Chan4PassHttpCall(Site site, LoginRequest loginRequest) {
        super(site);
        this.loginRequest = loginRequest;
    }

    @Override
    public void setup(
            Request.Builder requestBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        FormBody.Builder formBuilder = new FormBody.Builder();

        formBuilder.add("act", "do_login");

        formBuilder.add("id", loginRequest.user);
        formBuilder.add("pin", loginRequest.pass);

        requestBuilder.url(getSite().endpoints().login());
        requestBuilder.post(formBuilder.build());
    }

    @Override
    public void process(Response response, String result) {
        boolean authSuccess = false;
        if (result.contains("Success! Your device is now authorized")) {
            authSuccess = true;
        } else {
            String message;
            if (result.contains("Your Token must be exactly 10 characters")) {
                message = "Incorrect token";
            } else if (result.contains("You have left one or more fields blank")) {
                message = "You have left one or more fields blank";
            } else if (result.contains("Incorrect Token or PIN")) {
                message = "Incorrect Token or PIN";
            } else {
                message = "Unknown error";
            }
            loginResponse.message = message;
        }

        if (authSuccess) {
            List<String> cookies = response.headers("Set-Cookie");
            String passId = null;
            for (String cookie : cookies) {
                try {
                    List<HttpCookie> parsedList = HttpCookie.parse(cookie);
                    for (HttpCookie parsed : parsedList) {
                        if (parsed.getName().equals("pass_id") && !parsed.getValue().equals("0")) {
                            passId = parsed.getValue();
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (passId != null) {
                loginResponse.token = passId;
                loginResponse.message = "Success! Your device is now authorized.";
                loginResponse.success = true;
            } else {
                loginResponse.message = "Could not get pass id";
            }
        }
    }
}
