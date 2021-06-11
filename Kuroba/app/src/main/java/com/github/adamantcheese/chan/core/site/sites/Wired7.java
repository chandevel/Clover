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
package com.github.adamantcheese.chan.core.site.sites;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;

import org.jsoup.Jsoup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public class Wired7
        extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://wired-7.org/";

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse(ROOT);
        }

        @Override
        public String[] getMediaHosts() {
            return new String[]{ROOT};
        }

        @Override
        public String[] getNames() {
            return new String[]{"Wired-7, wired7, Wired7"};
        }

        @Override
        public String desktopUrl(Loadable loadable, int postNo) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode)
                        .addPathSegment("res")
                        .addPathSegment(loadable.no + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    public Wired7() {
        setName("Wired-7");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://wired-7.org/favicon.ico")));
    }

    @Override
    public void setup() {
        setBoards(
                Board.fromSiteNameCode(this, "Random", "b"),
                Board.fromSiteNameCode(this, "Hentai", "h"),
                Board.fromSiteNameCode(this, "Humanidad", "hum"),
                Board.fromSiteNameCode(this, "Internacional/Random", "i"),
                Board.fromSiteNameCode(this, "Política", "pol"),
                Board.fromSiteNameCode(this, "Wired-7 Metaboard", "meta"),
                Board.fromSiteNameCode(this, "Anime", "a"),
                Board.fromSiteNameCode(this, "Cultura Japonesa", "jp"),
                Board.fromSiteNameCode(this, "Música y Audio", "mu"),
                Board.fromSiteNameCode(this, "Tecnología", "tech"),
                Board.fromSiteNameCode(this, "Videojuegos y Gaming", "v"),
                Board.fromSiteNameCode(this, "Medios Visuales", "vis"),
                Board.fromSiteNameCode(this, "Paranormal", "x"),
                Board.fromSiteNameCode(this, "Lain", "lain")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://wired-7.org", "https://wired-7.org"));
        setActions(new Wired7Actions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }

    private static class Wired7Actions
            extends VichanActions {
        Wired7Actions(CommonSite commonSite) {
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
            call.parameter("post", "Post");

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
                Matcher m = Pattern.compile("/\\w+/\\w+/(\\d+)(.html)?").matcher(urlPath);
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
    }
}
