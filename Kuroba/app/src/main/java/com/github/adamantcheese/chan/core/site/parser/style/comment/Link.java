package com.github.adamantcheese.chan.core.site.parser.style.comment;

import com.github.adamantcheese.chan.core.model.PostLinkable;

public class Link {
    public PostLinkable.Type type;
    public CharSequence key;
    public Object value;

    public Link(PostLinkable.Type type, CharSequence key, Object value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }
}
