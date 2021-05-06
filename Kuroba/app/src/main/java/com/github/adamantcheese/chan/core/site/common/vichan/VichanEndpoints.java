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

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.site.common.CommonSite;

import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;

public class VichanEndpoints
        extends CommonSite.CommonEndpoints {
    protected final CommonSite.SimpleHttpUrl root;
    protected final CommonSite.SimpleHttpUrl sys;

    public VichanEndpoints(CommonSite commonSite, String rootUrl, String sysUrl) {
        super(commonSite);
        root = new CommonSite.SimpleHttpUrl(rootUrl);
        sys = new CommonSite.SimpleHttpUrl(sysUrl);
    }

    @Override
    public HttpUrl catalog(Board board) {
        return root.builder().s(board.code).s("catalog.json").url();
    }

    @Override
    public HttpUrl thread(Loadable loadable) {
        return root.builder().s(loadable.boardCode).s("res").s(loadable.no + ".json").url();
    }

    @Override
    public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
        return root.builder()
                .s(post.board.code)
                .s("thumb")
                .s(arg.get("tim") + (arg.get("ext").equals("webm") ? ".webm" : ".png"))
                .url();
    }

    @Override
    public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
        return root.builder().s(post.board.code).s("src").s(arg.get("tim") + "." + arg.get("ext")).url();
    }

    @Override
    public Pair<HttpUrl, PassthroughBitmapResult> icon(ICON_TYPE icon, Map<String, String> arg) {
        CommonSite.SimpleHttpUrl stat = root.builder().s("static");

        if (icon == ICON_TYPE.COUNTRY_FLAG) {
            stat.s("flags").s(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".png");
        }

        return new Pair<>(stat.url(), new PassthroughBitmapResult());
    }

    @Override
    public HttpUrl pages(Board board) {
        return root.builder().s(board.code).s("threads.json").url();
    }

    @Override
    public HttpUrl reply(Loadable loadable) {
        return sys.builder().s("post.php").url();
    }

    @Override
    public HttpUrl delete(Post post) {
        return sys.builder().s("post.php").url();
    }
}
