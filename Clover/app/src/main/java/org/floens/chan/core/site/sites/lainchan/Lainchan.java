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
package org.floens.chan.core.site.sites.lainchan;

import android.support.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.MultipartHttpCall;
import org.floens.chan.core.site.common.vichan.VichanAntispam;
import org.floens.chan.core.site.common.vichan.VichanApi;
import org.floens.chan.core.site.common.vichan.VichanEndpoints;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.http.ReplyResponse;
import org.floens.chan.core.site.parser.CommentParser;
import org.floens.chan.core.site.parser.StyleRule;
import org.jsoup.Jsoup;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public class Lainchan extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Lainchan.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://lainchan.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"lainchan"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode).addPathSegment("res")
                        .addPathSegment(String.valueOf(loadable.no) + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("Lainchan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://lainchan.org/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "Programming", "λ"),
                Board.fromSiteNameCode(this, "Do It Yourself", "Δ"),
                Board.fromSiteNameCode(this, "Security", "sec"),
                Board.fromSiteNameCode(this, "Technology", "Ω"),
                Board.fromSiteNameCode(this, "Games and Interactive Media", "inter"),
                Board.fromSiteNameCode(this, "Literature", "lit"),
                Board.fromSiteNameCode(this, "Musical and Audible Media", "music"),
                Board.fromSiteNameCode(this, "Visual Media", "vis"),
                Board.fromSiteNameCode(this, "Humanity", "hum"),
                Board.fromSiteNameCode(this, "Drugs 3.0", "drug"),
                Board.fromSiteNameCode(this, "Consciousness and Dreams", "zzz"),
                Board.fromSiteNameCode(this, "layer", "layer"),
                Board.fromSiteNameCode(this, "Questions and Complaints", "q"),
                Board.fromSiteNameCode(this, "Random", "r")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://lainchan.org",
                "https://lainchan.org"));

        setActions(new CommonActions() {
            @Override
            public void setupPost(Reply reply, MultipartHttpCall call) {
                call.parameter("board", reply.loadable.board.code);

                if (reply.loadable.isThreadMode()) {
                    call.parameter("thread", String.valueOf(reply.loadable.no));
                } else {
//                    call.parameter("page", "1");
                }

                call.parameter("password", reply.password);
                call.parameter("name", reply.name);
                call.parameter("email", reply.options);

                if (!reply.loadable.isThreadMode() && !isEmpty(reply.subject)) {
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
                VichanAntispam antispam = new VichanAntispam(
                        HttpUrl.parse(resolvable().desktopUrl(reply.loadable, null)));
                antispam.addDefaultIgnoreFields();
                for (Map.Entry<String, String> e : antispam.get().entrySet()) {
                    call.parameter(e.getKey(), e.getValue());
                }
            }

            @Override
            public void handlePost(ReplyResponse replyResponse, Response response, String result) {
                Matcher auth = Pattern.compile(".*\"captcha\": ?true.*").matcher(result);
                Matcher err = Pattern.compile(".*<h1[^>]*>Error</h1>.*<h2[^>]*>(.*?)</h2>.*").matcher(result);
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
                            replyResponse.postNo = Integer.parseInt(url.encodedFragment());
                            replyResponse.posted = true;
                        }
                    } catch (NumberFormatException ignored) {
                        replyResponse.errorMessage = "Error posting: could not find posted thread.";
                    }
                }
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromNone();
            }
        });

        setApi(new VichanApi(this));

        CommentParser commentParser = new CommentParser();
        commentParser.addDefaultRules();
        commentParser.setQuotePattern(Pattern.compile(".*#(\\d+)"));
        commentParser.setFullQuotePattern(Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)"));
        commentParser.rule(StyleRule.tagRule("p").cssClass("quote").color(StyleRule.Color.INLINE_QUOTE).linkify());

        setParser(commentParser);
    }
}
