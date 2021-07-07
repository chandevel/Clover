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

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
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
    public void setupPost(Loadable loadable, MultipartHttpCall<ReplyResponse> call) {
        Reply reply = loadable.draft;
        call.parameter("board", loadable.boardCode);

        if (loadable.isThreadMode()) {
            call.parameter("thread", String.valueOf(loadable.no));
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
    public void prepare(
            MultipartHttpCall<ReplyResponse> call,
            Loadable loadable,
            NetUtilsClasses.ResponseResult<Void> callback
    ) {
        VichanAntispam antispam = new VichanAntispam(HttpUrl.parse(loadable.desktopUrl()));
        antispam.addDefaultIgnoreFields();
        antispam.get(new ResponseResult<Map<String, String>>() {
            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }

            @Override
            public void onSuccess(Map<String, String> result) {
                for (Map.Entry<String, String> e : result.entrySet()) {
                    call.parameter(e.getKey(), e.getValue());
                }
                callback.onSuccess(null);
            }
        });
    }

    @Override
    public ReplyResponse handlePost(Loadable loadable, Response response) {
        ReplyResponse replyResponse = new ReplyResponse(loadable);
        String responseString = "";
        try {
            responseString = response.body().string();
        } catch (Exception ignored) {}
        Matcher auth = Pattern.compile("\"captcha\": ?true").matcher(responseString);
        Matcher err = errorPattern().matcher(responseString);
        if (auth.find()) {
            replyResponse.requireAuthentication = true;
            replyResponse.errorMessage = responseString;
        } else if (err.find()) {
            replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text();
        } else {
            HttpUrl url = response.request().url();
            StringBuilder urlPath = new StringBuilder();
            //noinspection KotlinInternalInJava
            HttpUrl.Companion.toPathString$okhttp(url.pathSegments(), urlPath);
            Matcher m = Pattern.compile("/\\w+/\\w+/(\\d+).html").matcher(urlPath);
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
        return replyResponse;
    }

    @Override
    public void setupDelete(DeleteRequest deleteRequest, MultipartHttpCall<DeleteResponse> call) {
        call.parameter("board", deleteRequest.post.board.code);
        call.parameter("delete", "Delete");
        call.parameter("delete_" + deleteRequest.post.no, "on");
        call.parameter("password", deleteRequest.savedReply.password);

        if (deleteRequest.imageOnly) {
            call.parameter("file", "on");
        }
    }

    @Override
    public DeleteResponse handleDelete(Response httpResponse)
            throws IOException {
        DeleteResponse response = new DeleteResponse();
        Matcher err = errorPattern().matcher(httpResponse.body().string());
        if (err.find()) {
            response.errorMessage = Jsoup.parse(err.group(1)).body().text();
        } else {
            response.deleted = true;
        }
        return response;
    }

    public Pattern errorPattern() {
        return Pattern.compile("<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>");
    }

    @Override
    public SiteAuthentication postAuthenticate(Loadable loadableWithDraft) {
        return SiteAuthentication.fromNone();
    }

    @Override
    public void pages(Board board, ResponseResult<ChanPages> pagesListener) {
        // Vichan keeps the pages and the catalog as one JSON unit, so parse those here
        NetUtils.makeJsonRequest(site.endpoints().catalog(board),
                new ResponseResult<ChanPages>() {
                    @Override
                    public void onFailure(Exception e) {
                        Logger.e(site, "Failed to get pages for board " + board.code, e);
                        pagesListener.onSuccess(new ChanPages());
                    }

                    @Override
                    public void onSuccess(ChanPages result) {
                        pagesListener.onSuccess(result);
                    }
                },
                response -> ((VichanApi) site.chanReader()).readCatalogWithPages(response,
                        new ChanReaderProcessingQueue(new ArrayList<>(), Loadable.forCatalog(board))
                ),
                NetUtilsClasses.NO_CACHE
        );
    }
}
