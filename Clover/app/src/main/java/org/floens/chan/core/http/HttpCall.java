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
package org.floens.chan.core.http;

import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public abstract class HttpCall implements Callback {
    private static final String TAG = "HttpCall";

    private boolean successful = false;
    private ReplyManager.HttpCallback callback;

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public abstract void setup(Request.Builder requestBuilder);

    public abstract void process(Response response, String result) throws IOException;

    @SuppressWarnings("unchecked")
    public void postUI(boolean successful) {
        if (successful) {
            callback.onHttpSuccess(this);
        } else {
            callback.onHttpFail(this);
        }
    }

    @Override
    public void onResponse(Call call, Response response) {
        try {
            if (response.isSuccessful() && response.body() != null) {
                String responseString = response.body().string();
                process(response, responseString);
                successful = true;
            } else {
                onFailure(call, null);
            }
        } catch (IOException e) {
            Logger.e(TAG, "IOException processing response", e);
        } finally {
            IOUtils.closeQuietly(response.body());
        }

        AndroidUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                postUI(successful);
            }
        });
    }

    @Override
    public void onFailure(Call call, IOException e) {
        AndroidUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                postUI(false);
            }
        });
    }

    void setCallback(ReplyManager.HttpCallback<? extends HttpCall> callback) {
        this.callback = callback;
    }
}
