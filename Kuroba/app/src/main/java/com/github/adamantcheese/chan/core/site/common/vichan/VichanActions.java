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
package com.github.adamantcheese.chan.core.site.common.vichan;

import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;

import org.jsoup.Jsoup;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public class VichanActions
        extends CommonSite.CommonActions {
    public VichanActions(CommonSite commonSite) {
        super(commonSite);
    }

    @Override
    public void setupPost(Reply reply, MultipartHttpCall call) {
        call.parameter("board", reply.loadable.board.code);

        if (reply.loadable.isThreadMode()) {
            call.parameter("thread", String.valueOf(reply.loadable.no));
        }

        // Added with VichanAntispam.
        // call.parameter("post", "Post");

        call.parameter("password", reply.password);
        call.parameter("name", reply.name);
        call.parameter("email", reply.options);

        if (!isEmpty(reply.subject)) {
            call.parameter("subject", reply.subject);
        }

        call.parameter("body", reply.comment);

        if (reply.file != null) {
            call.fileParameter("file", reply.fileName, reply.file);
        }

        if (reply.spoilerImage) {
            call.parameter("spoiler", "on");
        }
    }

    @Override
    public boolean requirePrepare() {
        return true;
    }

    @Override
    public void prepare(MultipartHttpCall call, Reply reply, ReplyResponse replyResponse) {
        VichanAntispam antispam = new VichanAntispam(HttpUrl.parse(reply.loadable.desktopUrl()));
        antispam.addDefaultIgnoreFields();
        for (Map.Entry<String, String> e : antispam.get().entrySet()) {
            call.parameter(e.getKey(), e.getValue());
        }
    }

    @Override
    public void handlePost(ReplyResponse replyResponse, Response response, String result) {
        Matcher auth = Pattern.compile("\"captcha\": ?true").matcher(result);
        Matcher err = errorPattern().matcher(result);
        if (auth.find()) {
            replyResponse.requireAuthentication = true;
            replyResponse.errorMessage = result;
        } else if (err.find()) {
            replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text();
        } else {
            HttpUrl url = response.request().url();
            Matcher m = Pattern.compile("/\\w+/\\w+/(\\d+).html").matcher(url.encodedPath());
            try {
                if (m.find()) {
                    replyResponse.threadNo = Integer.parseInt(m.group(1));
                    String fragment = url.encodedFragment();
                    if (fragment != null) {
                        replyResponse.postNo = Integer.parseInt(fragment);
                    } else {
                        replyResponse.postNo = replyResponse.threadNo;
                    }
                    replyResponse.posted = true;
                }
            } catch (NumberFormatException ignored) {
                replyResponse.errorMessage = "Error posting: could not find posted thread.";
            }
        }
    }

    @Override
    public void setupDelete(DeleteRequest deleteRequest, MultipartHttpCall call) {
        call.parameter("board", deleteRequest.post.board.code);
        call.parameter("delete", "Delete");
        call.parameter("delete_" + deleteRequest.post.no, "on");
        call.parameter("password", deleteRequest.savedReply.password);

        if (deleteRequest.imageOnly) {
            call.parameter("file", "on");
        }
    }

    @Override
    public void handleDelete(DeleteResponse response, Response httpResponse, String responseBody) {
        Matcher err = errorPattern().matcher(responseBody);
        if (err.find()) {
            response.errorMessage = Jsoup.parse(err.group(1)).body().text();
        } else {
            response.deleted = true;
        }
    }

    public Pattern errorPattern() {
        return Pattern.compile("<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>");
    }

    @Override
    public SiteAuthentication postAuthenticate() {
        return SiteAuthentication.fromNone();
    }
}
