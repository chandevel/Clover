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
package org.floens.chan.core.reply;

import android.content.Context;
import android.content.Intent;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Reply;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.ui.activity.ImagePickActivity;
import org.floens.chan.utils.AndroidUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * To send an reply to 4chan.
 */
public class ReplyManager {
    private static final String TAG = "ReplyManager";

    private static final Pattern POST_THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final int TIMEOUT = 10000;

    private final Context context;
    private FileListener fileListener;
    private OkHttpClient client;

    private Map<Loadable, Reply> drafts = new HashMap<>();

    public ReplyManager(Context context) {
        this.context = context;

        client = new OkHttpClient();
        client.setConnectTimeout(TIMEOUT, TimeUnit.MILLISECONDS);
        client.setReadTimeout(TIMEOUT, TimeUnit.MILLISECONDS);
        client.setWriteTimeout(TIMEOUT, TimeUnit.MILLISECONDS);
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

    /**
     * Pick an file. Starts up the ImagePickActivity.
     *
     * @param listener FileListener to listen on.
     */
    public void pickFile(FileListener listener) {
        fileListener = listener;

        Intent intent = new Intent(context, ImagePickActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public File getPickFile() {
        return new File(context.getCacheDir(), "picked_file");
    }

    public void _onFilePickLoading() {
        if (fileListener != null) {
            fileListener.onFilePickLoading();
        }
    }

    public void _onFilePicked(String name, File file) {
        if (fileListener != null) {
            fileListener.onFilePicked(name, file);
            fileListener = null;
        }
    }

    public void _onFilePickError(boolean cancelled) {
        if (fileListener != null) {
            fileListener.onFilePickError(cancelled);
            fileListener = null;
        }
    }

    /**
     * Delete the fileListener.
     */
    public void removeFileListener() {
        fileListener = null;
    }

    public interface FileListener {
        void onFilePickLoading();

        void onFilePicked(String name, File file);

        void onFilePickError(boolean cancelled);
    }

    public void postPass(String token, String pin, final PassListener passListener) {
        FormEncodingBuilder formBuilder = new FormEncodingBuilder();

        formBuilder.add("act", "do_login");

        formBuilder.add("id", token);
        formBuilder.add("pin", pin);

        Request.Builder request = new Request.Builder()
                .url(ChanUrls.getPassUrl())
                .post(formBuilder.build());

        makeOkHttpCall(request, new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                final PassResponse res = new PassResponse();
                res.isError = true;
                res.message = context.getString(R.string.setting_pass_error);
                runUI(new Runnable() {
                    public void run() {
                        passListener.onResponse(res);
                    }
                });
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (!response.isSuccessful()) {
                    onFailure(response.request(), null);
                    return;
                }
                String responseString = response.body().string();
                response.body().close();

                final PassResponse res = new PassResponse();
                if (responseString.contains("Your device is now authorized")) {
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
                        res.passId = passId;
                        res.message = "Success! Your device is now authorized.";
                    } else {
                        res.isError = true;
                        res.message = "Could not get pass id";
                    }
                } else {
                    res.isError = true;
                    if (responseString.contains("Your Token must be exactly 10 characters")) {
                        res.message = "Incorrect token";
                    } else if (responseString.contains("You have left one or more fields blank")) {
                        res.message = "You have left one or more fields blank";
                    } else if (responseString.contains("Incorrect Token or PIN")) {
                        res.message = "Incorrect Token or PIN";
                    } else {
                        res.unknownError = true;
                    }
                }

                runUI(new Runnable() {
                    public void run() {
                        passListener.onResponse(res);
                    }
                });
            }
        });
    }

    public interface PassListener {
        void onResponse(PassResponse response);
    }

    public static class PassResponse {
        public boolean isError = false;
        public boolean unknownError = false;
        public String responseData = "";
        public String message = "";
        public String passId;
    }

    public void postDelete(final SavedReply reply, boolean onlyImageDelete, final DeleteListener listener) {
        FormEncodingBuilder formBuilder = new FormEncodingBuilder();
        formBuilder.add(Integer.toString(reply.no), "delete");
        if (onlyImageDelete) {
            formBuilder.add("onlyimgdel", "on");
        }
        formBuilder.add("mode", "usrdel");
        formBuilder.add("pwd", reply.password);

        Request.Builder request = new Request.Builder()
                .url(ChanUrls.getDeleteUrl(reply.board))
                .post(formBuilder.build());

        makeOkHttpCall(request, new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                final DeleteResponse res = new DeleteResponse();
                res.isNetworkError = true;
                runUI(new Runnable() {
                    @Override
                    public void run() {
                        listener.onResponse(res);
                    }
                });
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (!response.isSuccessful()) {
                    onFailure(response.request(), null);
                    return;
                }
                String responseString = response.body().string();
                response.body().close();

                final DeleteResponse res = new DeleteResponse();
                res.responseData = responseString;

                if (responseString.contains("You must wait longer before deleting this post")) {
                    res.isUserError = true;
                    res.isTooSoonError = true;
                } else if (responseString.contains("Password incorrect")) {
                    res.isUserError = true;
                    res.isInvalidPassword = true;
                } else if (responseString.contains("You cannot delete a post this old")) {
                    res.isUserError = true;
                    res.isTooOldError = true;
                } else if (responseString.contains("Updating index")) {
                    res.isSuccessful = true;
                }

                runUI(new Runnable() {
                    @Override
                    public void run() {
                        listener.onResponse(res);
                    }
                });
            }
        });
    }

    public interface DeleteListener {
        void onResponse(DeleteResponse response);
    }

    public static class DeleteResponse {
        public boolean isNetworkError = false;
        public boolean isUserError = false;
        public boolean isInvalidPassword = false;
        public boolean isTooSoonError = false;
        public boolean isTooOldError = false;
        public boolean isSuccessful = false;
        public String responseData = "";
    }

    public void makeHttpCall(HttpCall httpCall, HttpCallback callback) {
        //noinspection unchecked
        httpCall.setCallback(callback);

        Request.Builder requestBuilder = new Request.Builder();

        httpCall.setup(requestBuilder);

        requestBuilder.header("User-Agent", Chan.getInstance().getUserAgent());
        Request request = requestBuilder.build();

        client.newCall(request).enqueue(httpCall);
    }

    public interface HttpCallback<T extends HttpCall> {
        void onHttpSuccess(T httpPost);

        void onHttpFail(T httpPost);
    }

    private void makeOkHttpCall(Request.Builder requestBuilder, Callback callback) {
        requestBuilder.header("User-Agent", Chan.getInstance().getUserAgent());
        Request request = requestBuilder.build();
        client.newCall(request).enqueue(callback);
    }

    private void runUI(Runnable runnable) {
        AndroidUtils.runOnUiThread(runnable);
    }
}
