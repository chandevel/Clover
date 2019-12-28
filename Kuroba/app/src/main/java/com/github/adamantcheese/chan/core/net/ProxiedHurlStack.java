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
package com.github.adamantcheese.chan.core.net;

import com.android.volley.toolbox.HurlStack;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

public class ProxiedHurlStack
        extends HurlStack {
    public ProxiedHurlStack() {
        super();
    }

    @Override
    protected HttpURLConnection createConnection(URL url)
            throws IOException {
        // Start the connection by specifying a proxy server
        Proxy proxy = ChanSettings.getProxy();
        HttpURLConnection connection;
        if (proxy != null) {
            connection = (HttpURLConnection) url.openConnection(proxy);
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }

        // Use the same workaround as described in Volley's HurlStack:
        // Workaround for the M release HttpURLConnection not observing the
        // HttpURLConnection.setFollowRedirects() property.
        // https://code.google.com/p/android/issues/detail?id=194495
        connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());
        connection.setRequestProperty("User-Agent", NetModule.USER_AGENT);

        return connection;
    }
}
