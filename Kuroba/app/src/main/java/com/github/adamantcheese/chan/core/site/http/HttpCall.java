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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Http calls are an abstraction over a normal OkHttp call.
 * <p>These HttpCalls are used for emulating &lt;form&gt; elements used for posting, reporting, deleting, etc.
 * <p>Implement {@link #setup(Request.Builder, ProgressRequestBody.ProgressRequestListener)} and {@link #convert(Response)}.
 * {@code setup()} is called on the main thread, set up up the request builder here.
 */
public abstract class HttpCall<T>
        implements Callback, NetUtilsClasses.Converter<T, Response> {

    private final NetUtilsClasses.ResponseResult<T> callback;

    public abstract void setup(
            Request.Builder requestBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    );

    public HttpCall(@NonNull NetUtilsClasses.ResponseResult<T> callback) {
        this.callback = callback;
    }

    public abstract T convert(Response response)
            throws IOException;

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        T convert = null;
        try {
            convert = convert(response);
            response.close();
        } catch (Exception e) {
            Logger.e(this, "onResponse", e);
            callback.onFailure(e);
        }

        callback.onSuccess(convert);
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        Logger.e(this, "onFailure", e);
        callback.onFailure(e);
    }
}
