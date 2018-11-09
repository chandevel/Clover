/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.net;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.toolbox.HurlStack;

import org.apache.http.HttpResponse;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.settings.SettingProvider;
import org.floens.chan.core.settings.SharedPreferencesSettingProvider;
import org.floens.chan.core.settings.StringSetting;
import org.floens.chan.utils.AndroidUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.Map;

public class ProxiedHurlStack extends HurlStack {
    public ProxiedHurlStack(String userAgent) {
        super(userAgent, null);
    }
    
    @Override
    public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders) throws IOException, AuthFailureError {
        if (request.getUrl().matches("https://2ch.hk/(fur|gg|ga|hc|h|ho|e|fet)/.*$")) {
            SettingProvider p = new SharedPreferencesSettingProvider(AndroidUtils.getPreferences());
            StringSetting nsfwToken = new StringSetting(p, "2ch_usercode", "");
            additionalHeaders.put("Cookie", "ageallow=1; usercode_auth=" + nsfwToken.get());
        }

        return super.performRequest(request, additionalHeaders);
    }

    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        // Start the connection by specifying a proxy server
        Proxy proxy = ChanSettings.getProxy();
        if (proxy != null) {
            return (HttpURLConnection) url.openConnection(proxy);
        } else {
            return (HttpURLConnection) url.openConnection();
        }
    }
}
