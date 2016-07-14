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

import android.content.Context;

import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Reply;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * To send an reply to 4chan.
 */
public class ReplyManager {
    private static final int TIMEOUT = 30000;

    private final Context context;
    private String userAgent;
    private OkHttpClient client;

    private Map<Loadable, Reply> drafts = new HashMap<>();

    public ReplyManager(Context context, String userAgent) {
        this.context = context;
        this.userAgent = userAgent;

        client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
    }

    public Reply getReply(Loadable loadable) {
        Reply reply = drafts.get(loadable);
        if (reply == null) {
            reply = new Reply();
            drafts.put(loadable, reply);
        }
        return reply;
    }

    public void putReply(Loadable loadable, Reply reply) {
        // Remove files from all other replies because there can only be one picked_file at the same time.
        // Not doing this would be confusing and cause invalid fileNames.
        for (Map.Entry<Loadable, Reply> entry : drafts.entrySet()) {
            if (!entry.getKey().equals(loadable)) {
                Reply value = entry.getValue();
                value.file = null;
                value.fileName = "";
            }
        }

        drafts.put(loadable, reply);
    }

    public File getPickFile() {
        return new File(context.getCacheDir(), "picked_file");
    }

    public void makeHttpCall(HttpCall httpCall, HttpCallback<? extends HttpCall> callback) {
        httpCall.setCallback(callback);

        Request.Builder requestBuilder = new Request.Builder();

        httpCall.setup(requestBuilder);

        requestBuilder.header("User-Agent", userAgent);
        Request request = requestBuilder.build();

        client.newCall(request).enqueue(httpCall);
    }

    public interface HttpCallback<T extends HttpCall> {
        void onHttpSuccess(T httpPost);

        void onHttpFail(T httpPost);
    }
}
