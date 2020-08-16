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
package org.floens.chan.core.site.common.tinyib;

import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.MultipartHttpCall;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.http.ReplyResponse;
import org.jsoup.Jsoup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public class TinyIBActions extends CommonSite.CommonActions {
    public TinyIBActions(CommonSite commonSite) {
        super(commonSite);
    }

    @Override
    public void setupPost(Reply reply, MultipartHttpCall call) {
        call.parameter("email", reply.options);

        if (reply.file != null) {
            call.fileParameter("file", reply.fileName, reply.file);
        }

        call.parameter("message", reply.comment);
        call.parameter("name", reply.name);

        if (reply.loadable.isThreadMode()) {
            call.parameter("parent", String.valueOf(reply.loadable.no));
        }

        if (!isEmpty(reply.password)) {
            call.parameter("password", reply.password);
        }

        if (!isEmpty(reply.subject)) {
            call.parameter("subject", reply.subject);
        }
    }

    @Override
    public void handlePost(ReplyResponse replyResponse, Response response, String result) {
        // extract the data we need from matching the result
        int threadNo = 0;
		int postNo = 0;
        Matcher res = resultPattern().matcher(result);
        if (res.find()) {
            threadNo = Integer.parseInt(res.group(1));
            postNo = Integer.parseInt(res.group(2));
	    }
        Matcher err = errorPattern().matcher(result);
        if (err.find()) {
            replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text();
        } else {
            replyResponse.threadNo = threadNo;
            replyResponse.postNo = postNo;
            replyResponse.posted = true;
        }
    }

    public Pattern resultPattern() {
        return Pattern.compile(".+url=res/(\\d+).html#(\\d+)\">");
    }
    //TODO: match the error pattern
    public Pattern errorPattern() {
        return Pattern.compile("<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>");
    }

    @Override
    public SiteAuthentication postAuthenticate() {
        return SiteAuthentication.fromNone();
    }
}