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
package com.github.adamantcheese.chan.core.site.common.taimaba;

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

public class TaimabaActions extends CommonSite.CommonActions {
    private static final Pattern errorPattern = Pattern.compile("<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>");

    public TaimabaActions(CommonSite commonSite) {
        super(commonSite);
    }

    @Override
    public void setupPost(Reply reply, MultipartHttpCall call) {
        call.parameter("fart", Integer.toString((int) Math.random() * 15000 + 5000));

        call.parameter("board", reply.loadable.board.code);
        call.parameter("task", "post");

        if (reply.loadable.isThreadMode()) {
            call.parameter("parent", String.valueOf(reply.loadable.no));
        }

        call.parameter("password", reply.password);
        call.parameter("field1", reply.name);

        if (!isEmpty(reply.subject)) {
            call.parameter("field3", reply.subject);
        }

        call.parameter("field4", reply.comment);

        if (reply.file != null) {
            call.fileParameter("file", reply.fileName, reply.file);
        }

        if (reply.options == "sage") {
            call.parameter("sage", "on");
        }
    }

    public Pattern errorPattern() {
        return errorPattern;
    }

    @Override
    public SiteAuthentication postAuthenticate() {
        return SiteAuthentication.fromNone();
    }
}
