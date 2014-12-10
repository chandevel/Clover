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
package org.floens.chan.core.manager;

import android.content.Context;
import android.content.Intent;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Pass;
import org.floens.chan.core.model.Reply;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.ui.activity.ImagePickActivity;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.boye.httpclientandroidlib.Consts;
import ch.boye.httpclientandroidlib.Header;
import ch.boye.httpclientandroidlib.HeaderElement;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.config.RequestConfig;
import ch.boye.httpclientandroidlib.client.methods.CloseableHttpResponse;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.ContentType;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClientBuilder;
import ch.boye.httpclientandroidlib.util.EntityUtils;

/**
 * To send an reply to 4chan.
 */
public class ReplyManager {
    private static final String TAG = "ReplyManager";

    private static final Pattern challengePattern = Pattern.compile("challenge.?:.?'([\\w-]+)'");
    private static final Pattern responsePattern = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final int POST_TIMEOUT = 10000;

    private static final ContentType TEXT_UTF_8 = ContentType.create(
            "text/plain", Consts.UTF_8);

    private final Context context;
    private Reply draft;
    private FileListener fileListener;
    private final Random random = new Random();

    public ReplyManager(Context context) {
        this.context = context;
        draft = new Reply();
    }

    /**
     * Clear the draft
     */
    public void removeReplyDraft() {
        draft = new Reply();
    }

    /**
     * Set an reply draft.
     *
     * @param value the draft to save.
     */
    public void setReplyDraft(Reply value) {
        draft = value;
    }

    /**
     * Gets the saved reply draft.
     *
     * @return the saved draft or an empty draft.
     */
    public Reply getReplyDraft() {
        return draft;
    }

    /**
     * Add an quote to the comment field. Looks like >>123456789\n
     *
     * @param no the raw no to quote to.
     */
    public void quote(int no) {
        String textToInsert = ">>" + no + "\n";
        draft.comment = new StringBuilder(draft.comment).insert(draft.cursorPosition, textToInsert).toString();
        draft.cursorPosition += textToInsert.length();
    }

    public void quoteInline(int no, String text) {
        String textToInsert = ">>" + no + "\n";
        String[] lines = text.split("\n+");
        for (String line : lines) {
            textToInsert += ">" + line + "\n";
        }

        draft.comment = new StringBuilder(draft.comment).insert(draft.cursorPosition, textToInsert).toString();
        draft.cursorPosition += textToInsert.length();
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

    /**
     * Called from ImagePickActivity, sends onFileLoading to the fileListener.
     */
    public void _onPickedFileLoading() {
        if (fileListener != null) {
            fileListener.onFileLoading();
        }
    }

    /**
     * Called from ImagePickActivity. Sends the file to the listening
     * fileListener, and deletes the fileListener.
     */
    public void _onPickedFile(String name, File file) {
        if (fileListener != null) {
            fileListener.onFile(name, file);
        }
        fileListener = null;
    }

    /**
     * Delete the fileListener.
     */
    public void removeFileListener() {
        fileListener = null;
    }

    public static abstract class FileListener {
        public abstract void onFile(String name, File file);

        public abstract void onFileLoading();
    }

    /**
     * Get the CAPTCHA challenge hash from an JSON response.
     *
     * @param total The total response from the server
     * @return The pattern, or null when none was found.
     */
    public static String getChallenge(String total) {
        Matcher matcher = challengePattern.matcher(total);

        if (matcher.find() && matcher.groupCount() == 1) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public void sendPass(Pass pass, final PassListener listener) {
        Logger.i(TAG, "Sending pass login request");

        HttpPost httpPost = new HttpPost(ChanUrls.getPassUrl());

        MultipartEntityBuilder entity = MultipartEntityBuilder.create();

        entity.addTextBody("act", "do_login");

        entity.addTextBody("id", pass.token);
        entity.addTextBody("pin", pass.pin);

        //            entity.addPart("pwd", new StringBody(reply.password));

        httpPost.setEntity(entity.build());

        sendHttpPost(httpPost, new HttpPostSendListener() {
            @Override
            public void onResponse(String responseString, HttpClient client, HttpResponse response) {
                PassResponse e = new PassResponse();

                if (responseString == null || response == null) {
                    e.isError = true;
                    e.message = context.getString(R.string.pass_error);
                } else {
                    e.responseData = responseString;

                    if (responseString.contains("Your device is now authorized")) {
                        e.message = "Success! Your device is now authorized.";

                        String passId = null;

                        Header[] cookieHeaders = response.getHeaders("Set-Cookie");
                        if (cookieHeaders != null) {
                            for (Header cookieHeader : cookieHeaders) {
                                HeaderElement[] elements = cookieHeader.getElements();
                                if (elements != null) {
                                    for (HeaderElement el : elements) {
                                        if (el != null) {
                                            if (el.getName().equals("pass_id")) {
                                                passId = el.getValue();
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (passId != null) {
                            e.passId = passId;
                        } else {
                            e.isError = true;
                            e.message = "Could not get pass id";
                        }
                    } else {
                        e.isError = true;
                        if (responseString.contains("Your Token must be exactly 10 characters")) {
                            e.message = "Incorrect token";
                        } else if (responseString.contains("You have left one or more fields blank")) {
                            e.message = "You have left one or more fields blank";
                        } else if (responseString.contains("Incorrect Token or PIN")) {
                            e.message = "Incorrect Token or PIN";
                        } else {
                            e.unknownError = true;
                        }
                    }
                }

                listener.onResponse(e);
            }
        });
    }

    public static interface PassListener {
        public void onResponse(PassResponse response);
    }

    public static class PassResponse {
        public boolean isError = false;
        public boolean unknownError = false;
        public String responseData = "";
        public String message = "";
        public String passId;
    }

    public void sendDelete(final SavedReply reply, boolean onlyImageDelete, final DeleteListener listener) {
        Logger.i(TAG, "Sending delete request: " + reply.board + ", " + reply.no);

        HttpPost httpPost = new HttpPost(ChanUrls.getDeleteUrl(reply.board));

        MultipartEntityBuilder entity = MultipartEntityBuilder.create();


        entity.addTextBody(Integer.toString(reply.no), "delete");

        if (onlyImageDelete) {
            entity.addTextBody("onlyimgdel", "on");
        }

        // res not necessary

        entity.addTextBody("mode", "usrdel");
        entity.addTextBody("pwd", reply.password);


        httpPost.setEntity(entity.build());

        sendHttpPost(httpPost, new HttpPostSendListener() {
            @Override
            public void onResponse(String responseString, HttpClient client, HttpResponse response) {
                DeleteResponse e = new DeleteResponse();

                if (responseString == null) {
                    e.isNetworkError = true;
                } else {
                    e.responseData = responseString;

                    if (responseString.contains("You must wait longer before deleting this post")) {
                        e.isUserError = true;
                        e.isTooSoonError = true;
                    } else if (responseString.contains("Password incorrect")) {
                        e.isUserError = true;
                        e.isInvalidPassword = true;
                    } else if (responseString.contains("You cannot delete a post this old")) {
                        e.isUserError = true;
                        e.isTooOldError = true;
                    } else if (responseString.contains("Updating index")) {
                        e.isSuccessful = true;
                    }
                }

                listener.onResponse(e);
            }
        });
    }

    public static interface DeleteListener {
        public void onResponse(DeleteResponse response);
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

    private void getCaptchaHash(final CaptchaHashListener listener, String challenge, String response) {
        HttpPost httpPost = new HttpPost(ChanUrls.getCaptchaFallback());

        MultipartEntityBuilder entity = MultipartEntityBuilder.create();

        entity.addTextBody("c", challenge, TEXT_UTF_8);
        entity.addTextBody("response", response, TEXT_UTF_8);

        httpPost.setEntity(entity.build());

        sendHttpPost(httpPost, new HttpPostSendListener() {
            @Override
            public void onResponse(String responseString, HttpClient client, HttpResponse response) {
                if (responseString != null) {
                    Document document = Jsoup.parseBodyFragment(responseString);
                    Elements verificationToken = document.select("div.fbc-verification-token textarea");
                    String hash = verificationToken.text();
                    if (hash.length() > 0) {
                        listener.onHash(hash);
                        return;
                    }
                }
                listener.onHash(null);
            }
        });
    }

    private interface CaptchaHashListener {
        public void onHash(String hash);
    }

    /**
     * Send an reply off to the server.
     *
     * @param reply    The reply object with all data needed, like captcha and the
     *                 file.
     * @param listener The listener, after server response.
     */
    public void sendReply(final Reply reply, final ReplyListener listener) {
        Logger.i(TAG, "Sending reply request: " + reply.board + ", " + reply.resto);

        CaptchaHashListener captchaHashListener = new CaptchaHashListener() {
            @Override
            public void onHash(String captchaHash) {
                if (captchaHash == null) {
                    // Could not find a hash in the response html
                    ReplyResponse e = new ReplyResponse();
                    e.isUserError = true;
                    e.isCaptchaError = true;
                    listener.onResponse(e);
                    return;
                }

                HttpPost httpPost = new HttpPost(ChanUrls.getReplyUrl(reply.board));

                MultipartEntityBuilder entity = MultipartEntityBuilder.create();

                reply.password = Long.toHexString(random.nextLong());

                entity.addTextBody("name", reply.name, TEXT_UTF_8);
                entity.addTextBody("email", reply.email, TEXT_UTF_8);

                entity.addTextBody("sub", reply.subject, TEXT_UTF_8);
                entity.addTextBody("com", reply.comment, TEXT_UTF_8);

                if (reply.resto >= 0) {
                    entity.addTextBody("resto", Integer.toString(reply.resto));
                }

                if (reply.spoilerImage) {
                    entity.addTextBody("spoiler", "on");
                }

                entity.addTextBody("g-recaptcha-response", captchaHash, TEXT_UTF_8);

                entity.addTextBody("mode", "regist");
                entity.addTextBody("pwd", reply.password);

                if (reply.usePass) {
                    httpPost.addHeader("Cookie", "pass_id=" + reply.passId);
                }

                if (reply.file != null) {
                    entity.addBinaryBody("upfile", reply.file, ContentType.APPLICATION_OCTET_STREAM, reply.fileName);
                }

                httpPost.setEntity(entity.build());

                sendHttpPost(httpPost, new HttpPostSendListener() {
                    @Override
                    public void onResponse(String responseString, HttpClient client, HttpResponse response) {
                        ReplyResponse e = new ReplyResponse();

                        if (responseString == null) {
                            e.isNetworkError = true;
                        } else {
                            e.responseData = responseString;

                            if (responseString.contains("No file selected")) {
                                e.isUserError = true;
                                e.isFileError = true;
                            } else if (responseString.contains("You forgot to solve the CAPTCHA")
                                    || responseString.contains("You seem to have mistyped the CAPTCHA")) {
                                e.isUserError = true;
                                e.isCaptchaError = true;
                            } else if (responseString.toLowerCase(Locale.ENGLISH).contains("post successful")) {
                                e.isSuccessful = true;
                            }
                        }

                        if (e.isSuccessful) {
                            Matcher matcher = responsePattern.matcher(e.responseData);

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
                                savedReply.password = reply.password;

                                ChanApplication.getDatabaseManager().saveReply(savedReply);

                                e.threadNo = threadNo;
                                e.no = no;
                            } else {
                                Logger.w(TAG, "No thread & no in the response");
                            }
                        }

                        listener.onResponse(e);
                    }
                });
            }
        };

        getCaptchaHash(captchaHashListener, reply.captchaChallenge, reply.captchaResponse);
    }

    public static interface ReplyListener {
        public void onResponse(ReplyResponse response);
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

    /**
     * Async task to send an reply to the server. Uses HttpClient. Since Android
     * 4.4 there is an updated version of HttpClient, 4.2, given with Android.
     * However, that version causes problems with file uploading. Version 4.3 of
     * HttpClient has been given with a library, that has another namespace:
     * ch.boye.httpclientandroidlib This lib also has some fixes/improvements of
     * HttpClient for Android.
     */
    private void sendHttpPost(final HttpPost post, final HttpPostSendListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RequestConfig.Builder requestBuilder = RequestConfig.custom();
                requestBuilder = requestBuilder.setConnectTimeout(POST_TIMEOUT);
                requestBuilder = requestBuilder.setConnectionRequestTimeout(POST_TIMEOUT);

                HttpClientBuilder httpBuilder = HttpClientBuilder.create();
                httpBuilder.setDefaultRequestConfig(requestBuilder.build());
                final CloseableHttpClient client = httpBuilder.build();
                try {
                    final CloseableHttpResponse response = client.execute(post);
                    final String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResponse(responseString, client, response);
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    Utils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onResponse(null, client, null);
                        }
                    });
                } finally {
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private static interface HttpPostSendListener {
        public void onResponse(String responseString, HttpClient client, HttpResponse response);
    }
}
