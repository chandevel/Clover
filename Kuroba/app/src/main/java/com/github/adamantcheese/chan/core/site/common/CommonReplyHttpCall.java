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
package com.github.adamantcheese.chan.core.site.common;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import org.jsoup.Jsoup;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.Response;

public abstract class CommonReplyHttpCall extends HttpCall {
    private static final String TAG = "CommonReplyHttpCall";
    private static final Random RANDOM = new Random();
    private static final Pattern THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)</span");
    private static final String PROBABLY_BANNED_TEXT = "banned";

    public final Reply reply;
    public final ReplyResponse replyResponse = new ReplyResponse();

    public CommonReplyHttpCall(Site site, Reply reply) {
        super(site);
        this.reply = reply;
    }

    @Override
    public void setup(
            Request.Builder requestBuilder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        replyResponse.password = Long.toHexString(RANDOM.nextLong());

        MultipartBody.Builder formBuilder = new MultipartBody.Builder();
        formBuilder.setType(MultipartBody.FORM);

        addParameters(formBuilder, progressListener);

        HttpUrl replyUrl = site.endpoints().reply(this.reply.loadable);
        requestBuilder.url(replyUrl);
        requestBuilder.addHeader("Referer", replyUrl.toString());
        requestBuilder.post(formBuilder.build());
    }

    @Override
    public void process(Response response, String result) {
        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            replyResponse.errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().text();
            replyResponse.probablyBanned = replyResponse.errorMessage.contains(PROBABLY_BANNED_TEXT);
        } else {
            Matcher threadNoMatcher = THREAD_NO_PATTERN.matcher(result);
            if (threadNoMatcher.find()) {
                try {
                    replyResponse.threadNo = Integer.parseInt(threadNoMatcher.group(1));
                    replyResponse.postNo = Integer.parseInt(threadNoMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }

                if (replyResponse.threadNo >= 0 && replyResponse.postNo >= 0) {
                    replyResponse.posted = true;
                }
            }
        }
    }

    public abstract void addParameters(
            MultipartBody.Builder builder,
            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    );
}
