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
package org.floens.chan.core.site.sites.chan8;

import android.text.TextUtils;

import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.common.CommonReplyHttpCall;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class Chan8ReplyHttpCall extends CommonReplyHttpCall {
    public Chan8ReplyHttpCall(Site site, Reply reply) {
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
}
