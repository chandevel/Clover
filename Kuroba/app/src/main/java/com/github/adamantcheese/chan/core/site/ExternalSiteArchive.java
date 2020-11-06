package com.github.adamantcheese.chan.core.site;

import android.content.Context;
import android.util.JsonReader;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ThreadLink;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

public abstract class ExternalSiteArchive
        implements Site {
    public Context context;
    public String domain;
    public String name;
    public List<String> boardCodes;
    public boolean searchEnabled;

    public ExternalSiteArchive(
            Context context, String domain, String name, List<String> boardCodes, boolean searchEnabled
    ) {
        this.context = context;
        this.domain = domain;
        this.name = name;
        this.boardCodes = boardCodes;
        this.searchEnabled = searchEnabled;
    }

    public Loadable getArchiveLoadable(String boardCode, int opNo, int postNo) {
        Loadable l = Loadable.forThread(board(boardCode), opNo, "", false);
        if (opNo != postNo) l.markedNo = postNo;
        return l;
    }

    @Override
    public void initialize(int id, JsonSettings userSettings) {}

    @Override
    public void postInitialize() {}

    @Override
    public int id() {
        return -1;
    }

    @Override
    public String name() {
        return name;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public SiteIcon icon() {
        return null;
    }

    @Override
    public BoardsType boardsType() {
        return BoardsType.STATIC;
    }

    @Override
    public abstract ArchiveSiteUrlHandler resolvable();

    @Override
    public boolean siteFeature(SiteFeature siteFeature) {
        return siteFeature == SiteFeature.IMAGE_FILE_HASH;
    }

    @Override
    public boolean boardFeature(BoardFeature boardFeature, Board board) {
        return false;
    }

    @Override
    public List<SiteSetting<?>> settings() {
        return Collections.emptyList();
    }

    @Override
    public abstract ArchiveEndpoints endpoints();

    @Override
    public CommonSite.CommonCallModifier callModifier() {
        return null;
    }

    @Override
    public abstract ChanReader chanReader();

    @Override
    public SiteActions actions() {
        return new SiteActions() {
            @Override
            public void boards(BoardsListener boardsListener) {}

            @Override
            public void pages(Board board, PagesListener pagesListener) {}

            @Override
            public void post(Loadable loadableWithDraft, PostListener postListener) {}

            @Override
            public boolean postRequiresAuthentication() { return false; }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromNone();
            }

            @Override
            public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {}

            @Override
            public void archive(Board board, ArchiveListener archiveListener) {}

            @Override
            public void login(LoginRequest loginRequest, LoginListener loginListener) {}

            @Override
            public void logout() {}

            @Override
            public boolean isLoggedIn() { return false; }

            @Override
            public LoginRequest getLoginDetails() { return new LoginRequest("", ""); }
        };
    }

    @Override
    public Board board(String code) {
        return Board.fromSiteNameCode(this, code, code);
    }

    @Override
    public Board createBoard(String name, String code) {
        return Board.fromSiteNameCode(this, code, code);
    }

    @NonNull
    @Override
    public ChunkDownloaderSiteProperties getChunkDownloaderSiteProperties() {
        return new ChunkDownloaderSiteProperties(Integer.MAX_VALUE, false, false);
    }

    public abstract static class ArchiveEndpoints
            implements SiteEndpoints {

        @Override
        public HttpUrl catalog(Board board) {
            return null;
        }

        @Override
        public abstract HttpUrl thread(Loadable loadable);

        @Override
        public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
            return null;
        }

        @Override
        public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
            return null;
        }

        @Override
        public HttpUrl icon(String icon, Map<String, String> arg) {
            return null;
        }

        @Override
        public HttpUrl boards() {
            return null;
        }

        @Override
        public HttpUrl pages(Board board) {
            return null;
        }

        @Override
        public HttpUrl archive(Board board) {
            return null;
        }

        @Override
        public HttpUrl reply(Loadable thread) {
            return null;
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

        @Override
        public HttpUrl banned() {
            return null;
        }

        public abstract HttpUrl resolvePost(String boardCode, int postNo);
    }

    public abstract class ArchiveSiteUrlHandler
            implements SiteUrlHandler {
        @Override
        public boolean matchesName(String value) {
            return name.equals(value);
        }

        @Override
        public boolean respondsTo(HttpUrl url) {
            return domain.equals(url.host());
        }

        @Override
        public boolean matchesMediaHost(@NonNull HttpUrl url) {
            return false;
        }

        @Override
        public abstract String desktopUrl(Loadable loadable, int postNo);

        @Override
        public Loadable resolveLoadable(Site site, HttpUrl url) {
            return Loadable.emptyLoadable(context);
        }

        public abstract ThreadLink resolveToThreadLink(ResolveLink sourceLink, JsonReader reader);
    }
}
