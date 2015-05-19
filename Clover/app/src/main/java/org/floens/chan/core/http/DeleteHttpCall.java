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

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.SavedReply;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeleteHttpCall extends HttpCall {
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)<\\/span");

    public boolean deleted;
    public String errorMessage;

    private final SavedReply reply;
    private final boolean onlyImageDelete;

    public DeleteHttpCall(final SavedReply reply, boolean onlyImageDelete) {
        this.reply = reply;
        this.onlyImageDelete = onlyImageDelete;
    }

    @Override
    public void setup(Request.Builder requestBuilder) {
        FormEncodingBuilder formBuilder = new FormEncodingBuilder();
        formBuilder.add(Integer.toString(reply.no), "delete");
        if (onlyImageDelete) {
            formBuilder.add("onlyimgdel", "on");
        }
        formBuilder.add("mode", "usrdel");
        formBuilder.add("pwd", reply.password);

        requestBuilder.url(ChanUrls.getDeleteUrl(reply.board));
        requestBuilder.post(formBuilder.build());
    }

    @Override
    public void process(Response response, String result) throws IOException {
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().ownText();
        } else {
            deleted = true;
        }
    }
}
