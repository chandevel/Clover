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
package org.floens.chan.core.site.sites.dvach;

import android.text.TextUtils;
import android.util.Log;

import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.common.CommonReplyHttpCall;
import org.floens.chan.core.site.http.LoginResponse;
import org.floens.chan.core.site.http.Reply;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DvachReplyCall extends CommonReplyHttpCall {
    private static final Pattern ERROR_MESSAGE = Pattern.compile("^\\{\"Error\":-\\d+,\"Reason\":\"(.*)\"");
    private static final Pattern POST_MESSAGE = Pattern.compile("^\\{\"Error\":null,\"Status\":\"OK\",\"Num\":(\\d+)");
    private static final Pattern THREAD_MESSAGE = Pattern.compile("^\\{\"Error\":null,\"Status\":\"Redirect\",\"Target\":(\\d+)");
    private static final String PROBABLY_BANNED_TEXT = "banned";
    private String captchaType;
    public final LoginResponse loginResponse = new LoginResponse();


    DvachReplyCall(Site site, Reply reply, String captchaType) {
        super(site, reply);
        this.captchaType = captchaType;
    }

    @Override
    public void addParameters(MultipartBody.Builder formBuilder) {
        formBuilder.addFormDataPart("task", "post");
        formBuilder.addFormDataPart("board", reply.loadable.boardCode);
        formBuilder.addFormDataPart("comment", reply.comment);
        formBuilder.addFormDataPart("thread", String.valueOf(reply.loadable.no));

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!reply.loadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("subject", reply.subject);
        }


        if (this.captchaType == "v1") {
            formBuilder.addFormDataPart("captcha_type", "2chaptcha");
            formBuilder.addFormDataPart("2chaptcha_id", reply.captchaChallenge);
            formBuilder.addFormDataPart("2chaptcha_value", reply.captchaResponse);
        } else {
            if (reply.captchaResponse != null) {
                formBuilder.addFormDataPart("captcha_type", "recaptcha");
                formBuilder.addFormDataPart("captcha_key", Dvach.CAPTCHA_KEY);

                if (reply.captchaChallenge != null) {
                    formBuilder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge);
                    formBuilder.addFormDataPart("recaptcha_response_field", reply.captchaResponse);
                } else {
                    formBuilder.addFormDataPart("g-recaptcha-response", reply.captchaResponse);
                }
            }
        }

        if (reply.file != null) {
            formBuilder.addFormDataPart("image", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }
    }

    @Override
    public void process(Response response, String result) throws IOException {
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            replyResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
            replyResponse.probablyBanned = replyResponse.errorMessage.contains(PROBABLY_BANNED_TEXT);
        } else {
            replyResponse.posted = true;
            Matcher postMessageMatcher = POST_MESSAGE.matcher(result);
            if (postMessageMatcher.find()) {
                replyResponse.postNo = Integer.parseInt(postMessageMatcher.group(1));
            } else {
                Matcher threadMessageMatcher = THREAD_MESSAGE.matcher(result);
                if (threadMessageMatcher.find()) {
                    int threadNo = Integer.parseInt(threadMessageMatcher.group(1));
                    replyResponse.threadNo = threadNo;
                    replyResponse.postNo = threadNo;
                }
            }
        }

        if (response.message().contains("OK")) {
            List<String> cookies = response.headers("Set-Cookie");
            String usercode = null;
            for (String cookie : cookies) {
                try {
                    List<HttpCookie> parsedList = HttpCookie.parse(cookie);
                    for (HttpCookie parsed : parsedList) {
                        if (parsed.getName().equals("usercode_auth")) {
                            usercode = parsed.getValue();
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (usercode != null) {
                loginResponse.token = usercode;
                loginResponse.success = true;
                Log.i("Clover", "usercode_auth=:" + usercode);
            }
        }

    }
}

