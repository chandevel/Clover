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
import android.text.TextUtils;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Reply;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.ui.activity.ImagePickActivity;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
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
    private final Random random = new Random();
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
                res.message = context.getString(R.string.pass_error);
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

    public void postReply(Reply reply, ReplyListener replyListener) {
        if (reply.usePass) {
            postReplyInternal(reply, replyListener, null);
        } else {
            postReplyInternal(reply, replyListener, reply.captchaResponse);
        }
    }

    private void postReplyInternal(final Reply reply, final ReplyListener replyListener, String captchaHash) {
//        reply.password = Long.toHexString(random.nextLong());

        MultipartBuilder formBuilder = new MultipartBuilder();
        formBuilder.type(MultipartBuilder.FORM);

        formBuilder.addFormDataPart("mode", "regist");
//        formBuilder.addFormDataPart("pwd", reply.password);

        if (reply.resto >= 0) {
            formBuilder.addFormDataPart("resto", String.valueOf(reply.resto));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (reply.resto >= 0 && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("sub", reply.subject);
        }

        formBuilder.addFormDataPart("com", reply.comment);

        if (captchaHash != null) {
            formBuilder.addFormDataPart("g-recaptcha-response", captchaHash);
        }

        if (reply.file != null) {
            formBuilder.addFormDataPart("upfile", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }

        Request.Builder request = new Request.Builder()
                .url(ChanUrls.getReplyUrl(reply.board))
                .post(formBuilder.build());

        if (reply.usePass) {
            request.addHeader("Cookie", "pass_id=" + reply.passId);
        }

        makeOkHttpCall(request, new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                final ReplyResponse res = new ReplyResponse();
                res.isNetworkError = true;

                runUI(new Runnable() {
                    public void run() {
                        replyListener.onResponse(res);
                    }
                });
            }

            @Override
            public void onResponse(Response response) throws IOException {
                final ReplyResponse res = new ReplyResponse();
                if (response.isSuccessful()) {
                    onReplyPosted(response.body().string(), reply, res);
                    response.body().close();
                } else {
                    res.isNetworkError = true;
                }

                runUI(new Runnable() {
                    public void run() {
                        replyListener.onResponse(res);
                    }
                });
            }
        });
    }

    private ReplyResponse onReplyPosted(String responseString, Reply reply, ReplyResponse res) {
        res.responseData = responseString;

        if (res.responseData.contains("No file selected")) {
            res.isUserError = true;
            res.isFileError = true;
        } else if (res.responseData.contains("You forgot to solve the CAPTCHA") || res.responseData.contains("You seem to have mistyped the CAPTCHA")) {
            res.isUserError = true;
            res.isCaptchaError = true;
        } else if (res.responseData.toLowerCase(Locale.ENGLISH).contains("post successful")) {
            res.isSuccessful = true;

            Matcher matcher = POST_THREAD_NO_PATTERN.matcher(res.responseData);
            int threadNo = -1;
            int no = -1;
            if (matcher.find()) {
                try {
                    threadNo = Integer.parseInt(matcher.group(1));
                    no = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException err) {
                    err.printStackTrace();
                }
            }

            if (threadNo >= 0 && no >= 0) {
                SavedReply savedReply = new SavedReply();
                savedReply.board = reply.board;
                savedReply.no = no;
//                savedReply.password = reply.password;

                ChanApplication.getDatabaseManager().saveReply(savedReply);

                res.threadNo = threadNo;
                res.no = no;
            } else {
                Logger.w(TAG, "No thread & no in the response");
            }
        }
        return res;
    }

    public void makeHttpCall(HttpCall httpCall, HttpCallback callback) {
        //noinspection unchecked
        httpCall.setCallback(callback);

        Request.Builder requestBuilder = new Request.Builder();

        httpCall.setup(requestBuilder);

        requestBuilder.header("User-Agent", ChanApplication.getInstance().getUserAgent());
        Request request = requestBuilder.build();

        client.newCall(request).enqueue(httpCall);
    }

    public interface HttpCallback<T extends HttpCall> {
        void onHttpSuccess(T httpPost);

        void onHttpFail(T httpPost);
    }

    private void makeOkHttpCall(Request.Builder requestBuilder, Callback callback) {
        requestBuilder.header("User-Agent", ChanApplication.getInstance().getUserAgent());
        Request request = requestBuilder.build();
        client.newCall(request).enqueue(callback);
    }

    private void runUI(Runnable runnable) {
        AndroidUtils.runOnUiThread(runnable);
    }

    public interface ReplyListener {
        void onResponse(ReplyResponse response);
    }

    public static class ReplyResponse {
        /**
         * No response from server.
         */
        public boolean isNetworkError = false;

        /**
         * Some user error, like no file or captcha wrong.
         */
        public boolean isUserError = false;

        /**
         * The userError was an fileError
         */
        public boolean isFileError = false;

        /**
         * The userError was an captchaError
         */
        public boolean isCaptchaError = false;

        /**
         * Received 'post successful'
         */
        public boolean isSuccessful = false;

        /**
         * Raw html from the response. Used to set html in an WebView to the
         * client, when the error was not recognized by Clover.
         */
        public String responseData = "";

        /**
         * The no the post has
         */
        public int no = -1;

        /**
         * The thread no the post has
         */
        public int threadNo = -1;
    }
}
