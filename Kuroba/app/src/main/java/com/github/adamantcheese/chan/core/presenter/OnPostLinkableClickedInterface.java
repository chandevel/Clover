package com.github.adamantcheese.chan.core.presenter;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.ui.text.spans.post_linkables.PostLinkable;

public interface OnPostLinkableClickedInterface {
    void onPostLinkableClicked(Post post, PostLinkable<?> postLinkable);
}
