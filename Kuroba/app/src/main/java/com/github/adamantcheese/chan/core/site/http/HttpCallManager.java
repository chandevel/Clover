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
package com.github.adamantcheese.chan.core.site.http;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteRequestModifier;

import javax.inject.Inject;

import okhttp3.Request;

/**
 * Manages the {@link HttpCall} executions.
 */
public class HttpCallManager {
    private NetModule.ProxiedOkHttpClient okHttpClient;

    @Inject
    public HttpCallManager(NetModule.ProxiedOkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    public void makeHttpCall(
            HttpCall httpCall, HttpCall.HttpCallback<? extends HttpCall> callback
    ) {
        makeHttpCall(httpCall, callback, null);
    }

    public void makeHttpCall(
            HttpCall httpCall,
            HttpCall.HttpCallback<? extends HttpCall> callback,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        httpCall.setCallback(callback);

        Request.Builder requestBuilder = new Request.Builder();

        Site site = httpCall.site;
        httpCall.setup(requestBuilder, progressListener);

        if (site != null) {
            final SiteRequestModifier siteRequestModifier = site.requestModifier();
            if (siteRequestModifier != null) {
                siteRequestModifier.modifyHttpCall(httpCall, requestBuilder);
            }
        }

        requestBuilder.header("User-Agent", NetModule.USER_AGENT);
        Request request = requestBuilder.build();

        okHttpClient.getProxiedClient().newCall(request).enqueue(httpCall);
    }
}
