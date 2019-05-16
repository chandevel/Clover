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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.Reply;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Chan4ReplyCall extends CommonReplyHttpCall {
    public Chan4ReplyCall(Site site, Reply reply) {
        super(site, reply);
    }

    @Override
    public void addParameters(
            MultipartBody.Builder formBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", replyResponse.password);

        if (reply.loadable.isThreadMode()) {
            formBuilder.addFormDataPart("resto", String.valueOf(reply.loadable.no));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!reply.loadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
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
            attachFile(formBuilder, progressListener);
        }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }
    }

    private void attachFile(
            MultipartBody.Builder formBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        RequestBody requestBody;

        if (progressListener == null) {
            requestBody = RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            );
        } else {
            requestBody = new ProgressRequestBody(RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ), progressListener);

        }

        formBuilder.addFormDataPart(
                "upfile",
                reply.fileName,
                requestBody);
    }
}
