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
package org.floens.chan.core.site.sites.vichan;


import android.support.annotation.Nullable;
import android.webkit.WebView;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Authentication;
import org.floens.chan.core.site.Resolvable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteBase;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.SiteRequestModifier;
import org.floens.chan.core.site.common.ChanReader;
import org.floens.chan.core.site.common.CommonReplyHttpCall;
import org.floens.chan.core.site.common.FutabaChanParser;
import org.floens.chan.core.site.common.FutabaChanReader;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.http.HttpCallManager;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.utils.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Request;

import static org.floens.chan.Chan.injector;

public class ViChan extends SiteBase {
    private static final String TAG = "ViChan";

    public static final Resolvable RESOLVABLE = new Resolvable() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return ViChan.class;
        }

        @Override
        public boolean matchesName(String value) {
            return value.equals("8chan") || value.equals("8ch");
        }

        @Override
        public boolean respondsTo(HttpUrl url) {
            return url.host().equals("8ch.net");
        }
    };

    private final SiteEndpoints endpoints = new SiteEndpoints() {
        private final HttpUrl root = new HttpUrl.Builder()
                .scheme("https")
                .host("8ch.net")
                .build();

        private final HttpUrl media = new HttpUrl.Builder()
                .scheme("https")
                .host("media.8ch.net")
                .build();

        private final HttpUrl sys = new HttpUrl.Builder()
                .scheme("https")
                .host("sys.8ch.net")
                .build();

        @Override
        public HttpUrl catalog(Board board) {
            return root.newBuilder()
                    .addPathSegment(board.code)
                    .addPathSegment("catalog.json")
                    .build();
        }

        @Override
        public HttpUrl thread(Board board, Loadable loadable) {
            return root.newBuilder()
                    .addPathSegment(board.code)
                    .addPathSegment("res")
                    .addPathSegment(loadable.no + ".json")
                    .build();
        }

        @Override
        public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
            return root.newBuilder()
                    .addPathSegment("file_store")
                    .addPathSegment(arg.get("tim") + "." + arg.get("ext"))
                    .build();
        }

        @Override
        public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
            return root.newBuilder()
                    .addPathSegment("file_store")
                    .addPathSegment("thumb")
                    .addPathSegment(arg.get("tim") + "." + arg.get("ext"))
                    .build();
        }

        @Override
        public HttpUrl icon(Post.Builder post, String icon, Map<String, String> arg) {
            HttpUrl.Builder stat = root.newBuilder().addPathSegment("static");

            switch (icon) {
                case "country":
                    stat.addPathSegment("flags");
                    stat.addPathSegment(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".png");
                    break;
            }

            return stat.build();
        }

        @Override
        public HttpUrl boards() {
            return null;
        }

        @Override
        public HttpUrl reply(Loadable loadable) {
            return sys.newBuilder()
                    .addPathSegment("post.php")
                    .build();
        }

        @Override
        public HttpUrl delete(Post post) {
            return null;
        }

        @Override
        public HttpUrl report(Post post) {
            return null;
        }

        @Override
        public HttpUrl login() {
            return null;
        }
    };

    private SiteRequestModifier siteRequestModifier = new SiteRequestModifier() {
        @Override
        public void modifyHttpCall(HttpCall httpCall, Request.Builder requestBuilder) {
        }

        @SuppressWarnings("deprecation")
        @Override
        public void modifyWebView(WebView webView) {
        }
    };

    @Override
    public String name() {
        return "8chan";
    }

    @Override
    public SiteIcon icon() {
        return SiteIcon.fromAssets("icons/8chan.png");
    }

    @Override
    public Resolvable resolvable() {
        return RESOLVABLE;
    }

    @Override
    public Loadable resolveLoadable(HttpUrl url) {
        List<String> parts = url.pathSegments();

        if (!parts.isEmpty()) {
            String boardCode = parts.get(0);
            Board board = board(boardCode);
            if (board != null) {
                if (parts.size() < 3) {
                    // Board mode
                    return loadableProvider.get(Loadable.forCatalog(board));
                } else if (parts.size() >= 3) {
                    // Thread mode
                    int no = -1;
                    try {
                        no = Integer.parseInt(parts.get(2).replace(".html", ""));
                    } catch (NumberFormatException ignored) {
                    }

                    int post = -1;
                    String fragment = url.fragment();
                    if (fragment != null) {
                        try {
                            post = Integer.parseInt(fragment);
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    if (no >= 0) {
                        Loadable loadable = loadableProvider.get(
                                Loadable.forThread(this, board, no));
                        if (post >= 0) {
                            loadable.markedNo = post;
                        }

                        return loadable;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean feature(Feature feature) {
        switch (feature) {
            case POSTING:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean boardFeature(BoardFeature boardFeature, Board board) {
        return false;
    }

    @Override
    public SiteEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public SiteRequestModifier requestModifier() {
        return siteRequestModifier;
    }

    @Override
    public BoardsType boardsType() {
        return BoardsType.INFINITE;
    }

    @Override
    public String desktopUrl(Loadable loadable, @Nullable Post post) {
        return "https://8ch.net/";
    }

    @Override
    public void boards(BoardsListener boardsListener) {
    }

    @Override
    public ChanReader chanReader() {
        FutabaChanParser parser = new FutabaChanParser(new ViChanParserHandler());
        return new FutabaChanReader(parser);
    }

    @Override
    public void post(Reply reply, final PostListener postListener) {
        // TODO
        HttpCallManager httpCallManager = injector().instance(HttpCallManager.class);
        httpCallManager.makeHttpCall(new ViChanReplyHttpCall(this, reply),
                new HttpCall.HttpCallback<CommonReplyHttpCall>() {
                    @Override
                    public void onHttpSuccess(CommonReplyHttpCall httpPost) {
                        postListener.onPostComplete(httpPost, httpPost.replyResponse);
                    }

                    @Override
                    public void onHttpFail(CommonReplyHttpCall httpPost, Exception e) {
                        Logger.e(TAG, "post error", e);

                        postListener.onPostError(httpPost);
                    }
                });
    }

    @Override
    public Authentication postAuthenticate() {
        return Authentication.fromUrl("https://8ch.net/dnsbls_bypass.php",
                "You failed the CAPTCHA",
                "You may now go back and make your post");
    }
}
