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

import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.DeleteResponse;
import org.floens.chan.core.site.http.HttpCall;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public class Chan4DeleteHttpCall extends HttpCall {
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)<\\/span");

    private final DeleteRequest deleteRequest;
    public final DeleteResponse deleteResponse = new DeleteResponse();

    public Chan4DeleteHttpCall(DeleteRequest deleteRequest) {
        this.deleteRequest = deleteRequest;
    }

    @Override
    public void setup(Request.Builder requestBuilder) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add(Integer.toString(deleteRequest.post.no), "delete");
        if (deleteRequest.imageOnly) {
            formBuilder.add("onlyimgdel", "on");
        }
        formBuilder.add("mode", "usrdel");
        formBuilder.add("pwd", deleteRequest.savedReply.password);

        requestBuilder.url(deleteRequest.site.endpoints().delete(deleteRequest.post));
        requestBuilder.post(formBuilder.build());
    }

    @Override
    public void process(Response response, String result) throws IOException {
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            deleteResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().ownText();
        } else {
            deleteResponse.deleted = true;
        }
    }
}
