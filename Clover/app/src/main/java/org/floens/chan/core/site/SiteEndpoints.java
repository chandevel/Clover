package org.floens.chan.core.site;

import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;

/**
 * Endpoints for {@link Site}.
 */
public interface SiteEndpoints {
    String catalog(Board board);

    String thread(Board board, Loadable loadable);

    String imageUrl(Post post);

    String thumbnailUrl(Post post);

    String flag(Post post);

    String boards();

    String reply(Board board, Loadable thread);
}
