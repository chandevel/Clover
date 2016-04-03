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

import android.text.TextUtils;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Reply;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplyHttpCall extends HttpCall {
    private static final String TAG = "ReplyHttpCall";
    private static final Random RANDOM = new Random();
    private static final Pattern THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)<\\/span");

    public boolean posted;
    public String errorMessage;
    public String text;
    public String password;
    public int threadNo = -1;
    public int postNo = -1;

    private final Reply reply;

    public ReplyHttpCall(Reply reply) {
        this.reply = reply;
    }

    @Override
    public void setup(Request.Builder requestBuilder) {
        boolean thread = reply.resto >= 0;

        password = Long.toHexString(RANDOM.nextLong());

        MultipartBuilder formBuilder = new MultipartBuilder();
        formBuilder.type(MultipartBuilder.FORM);

        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", password);

        if (thread) {
            formBuilder.addFormDataPart("resto", String.valueOf(reply.resto));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!thread && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("sub", reply.subject);
        }

        formBuilder.addFormDataPart("com", reply.comment);

        if (reply.captchaResponse != null) {
            if (reply.captchaChallenge != null) {
                formBuilder.addFormDataPart("recaptcha_challenge_field", reply.captchaChallenge);
                formBuilder.addFormDataPart("recaptcha_response_field", reply.captchaResponse);
            } else {
                formBuilder.addFormDataPart("g-recaptcha-response", reply.captchaResponse);
            }
        }

        if (reply.file != null) {
            formBuilder.addFormDataPart("upfile", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }

        requestBuilder.url(ChanUrls.getReplyUrl(reply.board));
        requestBuilder.post(formBuilder.build());

        if (reply.usePass) {
            requestBuilder.addHeader("Cookie", "pass_id=" + reply.passId);
        }
    }

    @Override
    public void process(Response response, String result) throws IOException {
        text = result;

        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
        } else {
            Matcher threadNoMatcher = THREAD_NO_PATTERN.matcher(result);
            if (threadNoMatcher.find()) {
                try {
                    threadNo = Integer.parseInt(threadNoMatcher.group(1));
                    postNo = Integer.parseInt(threadNoMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }

                if (threadNo >= 0 && postNo >= 0) {
                    posted = true;
                }
            }
        }
    }
}
