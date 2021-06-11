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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.HttpCall;

import java.io.File;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;

@SuppressWarnings("UnusedReturnValue")
public abstract class MultipartHttpCall<T>
        extends HttpCall<T> {
    private final MultipartBody.Builder formBuilder;

    private HttpUrl url;

    public MultipartHttpCall(@NonNull NetUtilsClasses.ResponseResult<T> callback) {
        super(callback);

        formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);
    }

    public MultipartHttpCall<T> url(HttpUrl url) {
        this.url = url;
        return this;
    }

    public MultipartHttpCall<T> parameter(String name, String value) {
        formBuilder.addFormDataPart(name, value);
        return this;
    }

    public MultipartHttpCall<T> fileParameter(String name, String filename, File file) {
        formBuilder.addFormDataPart(name,
                filename,
                RequestBody.create(file, MediaType.parse("application/octet-stream"))
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
