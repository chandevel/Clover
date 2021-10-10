package com.github.adamantcheese.chan.core.site.parser.style.comment;

import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;

/**
 * Resolve a board and postId to a ThreadLink.
 * Used for ExternalSiteArchives.
 */
public class ResolveLink {
    public Board board;
    public int postId;

    public ResolveLink(Site site, String boardCode, int postId) {
        this.board = Board.fromSiteNameCode(site, "", boardCode);
        this.postId = postId;
    }

    public void resolve(@NonNull ResolveCallback callback, @NonNull ResolveParser parser) {
        NetUtils.makeJsonRequest(((ExternalSiteArchive.ArchiveEndpoints) board.site.endpoints()).resolvePost(board.code, postId),
                new NetUtilsClasses.MainThreadResponseResult<>(new NetUtilsClasses.ResponseResult<ThreadLink>() {
                    @Override
                    public void onFailure(Exception e) {
                        callback.onProcessed(null);
                    }

                    @Override
                    public void onSuccess(ThreadLink result) {
                        callback.onProcessed(result);
                    }
                }),
                parser,
                NetUtilsClasses.NO_CACHE,
                null,
                5000
        );
    }

    public interface ResolveCallback {
        void onProcessed(@Nullable ThreadLink result);
    }

    public static class ResolveParser
            implements NetUtilsClasses.Converter<ThreadLink, JsonReader> {
        private final ResolveLink sourceLink;

        public ResolveParser(ResolveLink source) {
            sourceLink = source;
        }

        @Override
        public ThreadLink convert(JsonReader reader) {
            return ((ExternalSiteArchive.ArchiveSiteUrlHandler) sourceLink.board.site.resolvable()).resolveToThreadLink(sourceLink,
                    reader
            );
        }
    }
}
