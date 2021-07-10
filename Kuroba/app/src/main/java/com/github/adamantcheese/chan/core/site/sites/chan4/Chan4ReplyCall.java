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

import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Chan4ReplyCall
        extends CommonReplyHttpCall {
    public Chan4ReplyCall(@NonNull NetUtilsClasses.ResponseResult<ReplyResponse> callback, Loadable loadable) {
        super(callback, loadable);
    }

    @Override
    public void addParameters(
            MultipartBody.Builder formBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        Reply reply = originatingLoadable.draft;

        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", reply.password);

        if (originatingLoadable.isThreadMode()) {
            formBuilder.addFormDataPart("resto", String.valueOf(originatingLoadable.no));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!originatingLoadable.isThreadMode() && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("sub", reply.subject);
        }

        formBuilder.addFormDataPart(
                "com",
                reply.comment + (AndroidUtils.isAprilFoolsDay() && !PersistableChanState.noFunAllowed.get() ?
                        "\n\nSent from my " + Build.MANUFACTURER + " - " + Build.MODEL : "")
        );

        if (reply.token != null && reply.token.token != null) {
            if (reply.token.challenge != null) {
                if (Chan4.captchaType.get() != CommonDataStructs.CaptchaType.CUSTOM) {
                    formBuilder.addFormDataPart("recaptcha_challenge_field", reply.token.challenge);
                    formBuilder.addFormDataPart("recaptcha_response_field", reply.token.token);
                } else {
                    formBuilder.addFormDataPart("t-challenge", reply.token.challenge);
                    formBuilder.addFormDataPart("t-response", reply.token.token);
                }
            } else {
                formBuilder.addFormDataPart("g-recaptcha-response", reply.token.token);
            }
        }

        if (!reply.flag.isEmpty()) {
            formBuilder.addFormDataPart("flag", reply.flag);
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
            requestBody =
                    RequestBody.create(originatingLoadable.draft.file, MediaType.parse("application/octet-stream"));
        } else {
            requestBody = new ProgressRequestBody(
                    RequestBody.create(originatingLoadable.draft.file, MediaType.parse("application/octet-stream")),
                    progressListener
            );
        }

        formBuilder.addFormDataPart("upfile", originatingLoadable.draft.fileName, requestBody);
    }
}
