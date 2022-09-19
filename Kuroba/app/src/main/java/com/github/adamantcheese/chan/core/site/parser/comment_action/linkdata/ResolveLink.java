package com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.*;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;

import java.util.Objects;

/**
 * Resolve a board and postId to a ThreadLink.
 * Used for ExternalSiteArchives.
 */
public class ResolveLink {
    public final ExternalSiteArchive site;
    public final String boardCode;
    public final int postId;

    public ResolveLink(@NonNull ExternalSiteArchive site, @NonNull String boardCode, int postId) {
        this.site = site;
        this.boardCode = boardCode;
        this.postId = postId;
    }

    public void resolve(
            @NonNull ResolveLink sourceLink, @NonNull NoFailResponseResult<ThreadLink> callback
    ) {
        NetUtils.makeJsonRequest(
                site.endpoints().resolvePost(boardCode, postId),
                new MainThreadResponseResult<>(callback),
                input -> sourceLink.site.resolvable().resolveToThreadLink(sourceLink, input),
                NetUtilsClasses.NO_CACHE,
                null,
                5000
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolveLink)) return false;
        ResolveLink that = (ResolveLink) o;
        return postId == that.postId && site.equals(that.site) && boardCode.equals(that.boardCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(site, boardCode, postId);
    }
}
