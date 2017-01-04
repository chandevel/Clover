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
package org.floens.chan.core.site.sites.chan4;

import android.text.TextUtils;

import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.http.ReplyResponse;
import org.floens.chan.core.site.http.Reply;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Chan4ReplyHttpCall extends HttpCall {
    private static final String TAG = "Chan4ReplyHttpCall";
    private static final Random RANDOM = new Random();
    private static final Pattern THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)<\\/span");
    private static final String PROBABLY_BANNED_TEXT = "banned";

    public final Reply reply;
    public final ReplyResponse replyResponse = new ReplyResponse();

    public Chan4ReplyHttpCall(Reply reply) {
        this.reply = reply;
    }

    @Override
    public void setup(Request.Builder requestBuilder) {
        boolean thread = reply.loadable.isThreadMode();

        replyResponse.password = Long.toHexString(RANDOM.nextLong());

        MultipartBody.Builder formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);

        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", replyResponse.password);

        if (thread) {
            formBuilder.addFormDataPart("resto", String.valueOf(reply.loadable.no));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!thread && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("sub", reply.subject);
        }

        formBuilder.addFormDataPart("com", reply.comment);

        if (!reply.noVerification) {
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

        Site site = reply.loadable.getSite();
        requestBuilder.url(site.endpoints().reply(reply.loadable));
        requestBuilder.post(formBuilder.build());

        if (reply.noVerification) {
            requestBuilder.addHeader("Cookie", "pass_id=" + reply.passId);
        }
    }

    @Override
    public void process(Response response, String result) throws IOException {
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            replyResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
            replyResponse.probablyBanned = replyResponse.errorMessage.contains(PROBABLY_BANNED_TEXT);
        } else {
            Matcher threadNoMatcher = THREAD_NO_PATTERN.matcher(result);
            if (threadNoMatcher.find()) {
                try {
                    replyResponse.threadNo = Integer.parseInt(threadNoMatcher.group(1));
                    replyResponse.postNo = Integer.parseInt(threadNoMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }

                if (replyResponse.threadNo >= 0 && replyResponse.postNo >= 0) {
                    replyResponse.posted = true;
                }
            }
        }
    }
}
