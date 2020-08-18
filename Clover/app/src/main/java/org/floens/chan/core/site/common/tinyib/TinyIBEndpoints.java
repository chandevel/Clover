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

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.common.CommonSite;

import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;

public class TinyIBEndpoints extends CommonSite.CommonEndpoints {
    protected final CommonSite.SimpleHttpUrl root;
    protected final CommonSite.SimpleHttpUrl sys;
    protected final CommonSite.SimpleHttpUrl yt;

    public TinyIBEndpoints(CommonSite commonSite, String rootUrl, String sysUrl) {
        super(commonSite); 
        root = new CommonSite.SimpleHttpUrl(rootUrl);
        sys = new CommonSite.SimpleHttpUrl(sysUrl);
        yt = new CommonSite.SimpleHttpUrl("https://youtu.be/");
    }

    @Override
    public HttpUrl catalog(Board board) {
		return root.builder().s(board.code).s("catalog.json").url();
    }

    /*@Override
    public HttpUrl boards() {
        return root.builder().s("boards.json").url();
    }*/

    @Override
    public HttpUrl thread(Board board, Loadable loadable) {
        return root.builder().s(board.code).s("res").s(loadable.no + ".json").url();
    }

    @Override
    public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
        return sys.builder().s(post.board.code).s("thumb").s(arg.get("thumbnailpath")).url();
    }

    @Override
    public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
        if (arg.get("path").contains("<iframe")) {
            String youtubeId = arg.get("path");
            youtubeId = youtubeId.replaceAll("<iframe width=\"480\" height=\"270\" src=\"//www.youtube.com/embed/", "");
            youtubeId = youtubeId.replaceAll("\\?", "");
            youtubeId = youtubeId.replaceAll("feature=oembed\" frameborder=\"0\" allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture\" allowfullscreen></iframe>", "");
            return yt.builder().s(youtubeId).url();
        } else {
            return sys.builder().s(post.board.code).s("src").s(arg.get("path")).url();
        }
    }

    @Override
    public HttpUrl icon(Post.Builder post, String icon, Map<String, String> arg) {
        CommonSite.SimpleHttpUrl stat = sys.builder().s("static");

        if (icon.equals("country")) {
            stat.s("flags").s(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".png");
        }

        return stat.url();
    }


    @Override
    public HttpUrl reply(Loadable loadable) {
        return sys.builder().s(loadable.board.code).s("imgboard.php").url();
    }
}