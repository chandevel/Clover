package org.floens.chan.core.site;

import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;

import java.util.Map;

/**
 * Endpoints for {@link Site}.
 */
public interface SiteEndpoints {
    String catalog(Board board);

    String thread(Board board, Loadable loadable);

    String imageUrl(Post.Builder post, Map<String, String> arg);

    String thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg);

    String flag(Post.Builder post, String countryCode, Map<String, String> arg);

    String boards();

    String reply(Board board, Loadable thread);
}
