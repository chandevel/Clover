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
package com.github.adamantcheese.chan.core.site.sites.dvach;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DvachReplyCall
        extends CommonReplyHttpCall {
    private static final Pattern ERROR_MESSAGE = Pattern.compile("^\\{\"Error\":-\\d+,\"Reason\":\"(.*)\"");
    private static final Pattern POST_MESSAGE = Pattern.compile("^\\{\"Error\":null,\"Status\":\"OK\",\"Num\":(\\d+)");
    private static final Pattern THREAD_MESSAGE =
            Pattern.compile("^\\{\"Error\":null,\"Status\":\"Redirect\",\"Target\":(\\d+)");

    DvachReplyCall(@NonNull NetUtilsClasses.ResponseResult<ReplyResponse> callback, Loadable loadable) {
        super(callback, loadable);
    }

    @Override
    public void addParameters(
            MultipartBody.Builder formBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        Reply reply = originatingLoadable.draft;

        formBuilder.addFormDataPart("task", "post");
        formBuilder.addFormDataPart("board", originatingLoadable.boardCode);
        formBuilder.addFormDataPart("comment", reply.comment);
        formBuilder.addFormDataPart("thread", String.valueOf(originatingLoadable.no));

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!originatingLoadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("subject", reply.subject);
        }

        if (reply.token != null && reply.token.token != null) {
            formBuilder.addFormDataPart("captcha_type", "recaptcha");
            formBuilder.addFormDataPart("captcha_key", Dvach.CAPTCHA_KEY);

            if (reply.token.challenge != null) {
                formBuilder.addFormDataPart("recaptcha_challenge_field", reply.token.challenge);
                formBuilder.addFormDataPart("recaptcha_response_field", reply.token.token);
            } else {
                formBuilder.addFormDataPart("g-recaptcha-response", reply.token.token);
            }
        }

        if (reply.file != null) {
            attachFile(formBuilder, progressListener);
        }
    }

    private void attachFile(
            MultipartBody.Builder formBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        RequestBody requestBody;

        if (progressListener == null) {
            requestBody =
                    RequestBody.create(originatingLoadable.draft.file, MediaType.parse("application/octet-stream"));
        } else {
            requestBody = new ProgressRequestBody(
                    RequestBody.create(originatingLoadable.draft.file, MediaType.parse("application/octet-stream")),
                    progressListener
            );
        }

        formBuilder.addFormDataPart("image", originatingLoadable.draft.fileName, requestBody);
    }

    @Override
    public ReplyResponse convert(Response response)
            throws IOException {
        ReplyResponse replyResponse = new ReplyResponse(originatingLoadable);
        String responseString = response.body().string();
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(responseString);
        if (errorMessageMatcher.find()) {
            replyResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
        } else {
            replyResponse.posted = true;
            Matcher postMessageMatcher = POST_MESSAGE.matcher(responseString);
            if (postMessageMatcher.find()) {
                replyResponse.postNo = Integer.parseInt(postMessageMatcher.group(1));
            } else {
                Matcher threadMessageMatcher = THREAD_MESSAGE.matcher(responseString);
                if (threadMessageMatcher.find()) {
                    int threadNo = Integer.parseInt(threadMessageMatcher.group(1));
                    replyResponse.threadNo = threadNo;
                    replyResponse.postNo = threadNo;
                }
            }
        }
        return replyResponse;
    }
}
