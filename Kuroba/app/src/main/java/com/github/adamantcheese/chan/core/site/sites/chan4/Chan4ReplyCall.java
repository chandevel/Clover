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

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.http.Reply;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Chan4ReplyCall
        extends CommonReplyHttpCall {
    public Chan4ReplyCall(Loadable loadable) {
        super(loadable);
    }

    @Override
    public void addParameters(
            MultipartBody.Builder formBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        Reply reply = replyResponse.originatingLoadable.draft;

        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", reply.password);

        if (replyResponse.originatingLoadable.isThreadMode()) {
            formBuilder.addFormDataPart("resto", String.valueOf(replyResponse.originatingLoadable.no));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!replyResponse.originatingLoadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
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

        if (getSite() instanceof Chan4 && replyResponse.originatingLoadable.boardCode.equals("pol")) {
            if (!reply.flag.isEmpty()) {
                formBuilder.addFormDataPart("flag", reply.flag);
            } else {
                // if for some reason the flag type is empty, set it to the default "whatever the site thinks"
                formBuilder.addFormDataPart("flag",
                        Chan4.flagType.get().isEmpty() ? Chan4.flagType.getDefault() : Chan4.flagType.get()
                );
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
            MultipartBody.Builder formBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        RequestBody requestBody;

        if (progressListener == null) {
            requestBody = RequestBody.create(replyResponse.originatingLoadable.draft.file,
                    MediaType.parse("application/octet-stream")
            );
        } else {
            requestBody = new ProgressRequestBody(
                    RequestBody.create(replyResponse.originatingLoadable.draft.file,
                            MediaType.parse("application/octet-stream")
                    ),
                    progressListener
            );
        }

        formBuilder.addFormDataPart("upfile", replyResponse.originatingLoadable.draft.fileName, requestBody);
    }
}
