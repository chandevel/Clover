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
package com.github.adamantcheese.chan.core.site.common;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;

import java.io.File;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

public abstract class MultipartHttpCall
        extends HttpCall {
    private final MultipartBody.Builder formBuilder;

    private HttpUrl url;

    public MultipartHttpCall(Site site) {
        super(site);

        formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);
    }

    public MultipartHttpCall url(HttpUrl url) {
        this.url = url;
        return this;
    }

    public MultipartHttpCall parameter(String name, String value) {
        formBuilder.addFormDataPart(name, value);
        return this;
    }

    public MultipartHttpCall fileParameter(String name, String filename, File file) {
        formBuilder.addFormDataPart(name,
                filename,
                RequestBody.create(MediaType.parse("application/octet-stream"), file)
        );
        return this;
    }

    @Override
    public void setup(
            Request.Builder requestBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        requestBuilder.url(url);
        String r = url.scheme() + "://" + url.host();
        if (url.port() != 80 && url.port() != 443) {
            r += ":" + url.port();
        }
        requestBuilder.addHeader("Referer", r);
        requestBuilder.post(formBuilder.build());
    }
}
