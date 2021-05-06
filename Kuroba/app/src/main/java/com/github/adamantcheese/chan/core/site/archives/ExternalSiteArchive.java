package com.github.adamantcheese.chan.core.site.archives;

import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.site.DummySite;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteUrlHandler;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ThreadLink;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.utils.JavaUtils.NoDeleteArrayList;

import java.util.List;
import java.util.Map;

import kotlin.random.Random;
import okhttp3.HttpUrl;

/**
 * Base class for any external archive site implementation, like FoolFuuka or Ayase.
 */
public abstract class ExternalSiteArchive
        extends DummySite {
    private final int id;
    public final String domain;
    public final String name;
    public final NoDeleteArrayList<String> boardCodes;
    public final boolean searchEnabled;

    public ExternalSiteArchive(
            String domain, String name, List<String> boardCodes, boolean searchEnabled
    ) {
        this.domain = domain;
        this.name = name;
        this.boardCodes = new NoDeleteArrayList<>(boardCodes);
        this.searchEnabled = searchEnabled;

        id = Random.Default.nextInt(Integer.MIN_VALUE / 2, -1);
    }

    public Loadable getArchiveLoadable(Loadable op, int postNo) {
        Loadable l = Loadable.forThread(board(op.boardCode), op.no, op.title, false);
        if (op.no != postNo) l.markedNo = postNo;
        if (!(op.site instanceof ExternalSiteArchive) && op.no == postNo) {
            // copy the scroll location
            l.listViewIndex = op.listViewIndex;
            l.listViewTop = op.listViewTop;
        }
        return l;
    }

    @Override
    public int id() {
        return id;
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
    public abstract ArchiveSiteUrlHandler resolvable();

    @Override
    public boolean siteFeature(SiteFeature siteFeature) {
        return siteFeature == SiteFeature.IMAGE_FILE_HASH;
    }

    @Override
    public abstract ArchiveEndpoints endpoints();

    @Override
    public abstract ExternalArchiveChanReader chanReader();

    @Override
    public Board board(String code) {
        return Board.fromSiteNameCode(this, code, code);
    }

    @Override
    public Board createBoard(String name, String code) {
        return Board.fromSiteNameCode(this, code, code);
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
        public Pair<HttpUrl, PassthroughBitmapResult> icon(ICON_TYPE icon, Map<String, String> arg) {
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
            return Loadable.emptyLoadable();
        }

        /**
         * Given a source ResolveLink, turn it into a regular ThreadLink that can be used by the application without any additional API calls.
         * Originally added for FoolFuuka, because of how that archiver works.
         *
         * @param sourceLink The source of the link that needs resolution
         * @param reader     The JSON of the API query
         * @return A ThreadLink that the ResolveLink is processed into
         */
        public abstract ThreadLink resolveToThreadLink(ResolveLink sourceLink, JsonReader reader);
    }

    public abstract static class ExternalArchiveChanReader
            implements ChanReader {
        @Override
        public abstract PostParser getParser();

        @Override
        public abstract void loadThread(JsonReader reader, ChanReaderProcessingQueue queue)
                throws Exception;

        @Override
        public void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue) {
            // external archives don't support catalogs
        }

        @Override
        public abstract void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue)
                throws Exception;
    }
}
