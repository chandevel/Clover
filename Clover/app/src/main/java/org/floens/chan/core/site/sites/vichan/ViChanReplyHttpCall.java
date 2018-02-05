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
package org.floens.chan.core.site.sites.vichan;

import android.text.TextUtils;

import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.common.CommonReplyHttpCall;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.utils.Logger;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ViChanReplyHttpCall extends CommonReplyHttpCall {
    private static final String TAG = "ViChanReplyHttpCall";

    private static final Pattern REQUIRE_AUTHENTICATION = Pattern.compile(".*\"captcha\": ?true.*");
    private static final Pattern ERROR_MESSAGE =
            Pattern.compile(".*<h1>Error</h1>.*<h2[^>]*>(.*?)<\\/h2>.*");

    public ViChanReplyHttpCall(Site site, Reply reply) {
        super(site, reply);
    }

    @Override
    public void addParameters(MultipartBody.Builder formBuilder) {
//        formBuilder.addFormDataPart("pwd", replyResponse.password);

        formBuilder.addFormDataPart("board", reply.loadable.board.code);

        if (reply.loadable.isThreadMode()) {
            formBuilder.addFormDataPart("post", "New Reply");

            formBuilder.addFormDataPart("thread", String.valueOf(reply.loadable.no));
        } else {
            formBuilder.addFormDataPart("post", "New Thread");

            formBuilder.addFormDataPart("page", "1");
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!reply.loadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("subject", reply.subject);
        }

        formBuilder.addFormDataPart("body", reply.comment);

        if (reply.file != null) {
            formBuilder.addFormDataPart("file", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }
    }

    @Override
    public void process(Response response, String result) throws IOException {
        Matcher authenticationMatcher = REQUIRE_AUTHENTICATION.matcher(result);
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (authenticationMatcher.find()) {
            replyResponse.requireAuthentication = true;
            replyResponse.errorMessage = result;
        } else if (errorMessageMatcher.find()) {
            replyResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
        } else {
            Logger.d(TAG, "url: " + response.request().url().toString());
            Logger.d(TAG, "body: " + response);

            // TODO(multisite): 8ch redirects us, but the result is a 404, and we need that
            // redirect url to figure out what we posted.
            HttpUrl url = response.request().url();
            List<String> segments = url.pathSegments();

            String board = null;
            int threadId = 0, postId = 0;
            try {
                if (segments.size() == 3) {
                    board = segments.get(0);
                    threadId = Integer.parseInt(
                            segments.get(2).replace(".html", ""));
                    postId = Integer.parseInt(url.encodedFragment());
                }
            } catch (NumberFormatException ignored) {
            }

            if (postId == 0) {
                postId = threadId;
            }

            if (board != null && threadId != 0) {
                replyResponse.threadNo = threadId;
                replyResponse.postNo = postId;
                replyResponse.posted = true;
            } else {
                replyResponse.errorMessage = "Error posting: could not find posted thread.";
            }
        }
    }
}
