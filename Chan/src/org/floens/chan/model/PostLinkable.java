package org.floens.chan.model;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

/**
 * Anything that links to something in a post uses this entity.
 */
public class PostLinkable extends ClickableSpan {
    public static enum Type {QUOTE, LINK};
    
    public final Post post;
    public final String key;
    public final String value;
    public final Type type;
    
    public PostLinkable(Post post, String key, String value, Type type) {
    	this.post = post;
        this.key = key;
        this.value = value;
        this.type = type;
    }

	@Override
	public void onClick(View widget) {
		if (post.getLinkableListener() != null) {
			post.getLinkableListener().onLinkableClick(this);
		}
	}
	
	@Override
	public void updateDrawState(TextPaint ds) {
		ds.setColor(type == Type.QUOTE ? Color.argb(255, 221, 0, 0) : Color.argb(255, 0, 0, 180));
        ds.setUnderlineText(true);
	}
}
