/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.model;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import org.floens.chan.utils.ThemeHelper;

/**
 * Anything that links to something in a post uses this entity.
 */
public class PostLinkable extends ClickableSpan {
    public static enum Type {
        QUOTE, LINK, SPOILER, THREAD
    }

    public final Post post;
    public final String key;
    public final Object value;
    public final Type type;

    private boolean clicked = false;

    public PostLinkable(Post post, String key, Object value, Type type) {
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
        clicked = true;
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        if (type == Type.QUOTE || type == Type.LINK || type == Type.THREAD) {
            if (type == Type.QUOTE) {
                if (value instanceof Integer && post.getLinkableListener() != null && (Integer) value == post.getLinkableListener().getHighlightQuotesWithNo()) {
                    ds.setColor(ThemeHelper.getInstance().getHighlightQuoteColor());
                } else {
                    ds.setColor(ThemeHelper.getInstance().getQuoteColor());
                }
            } else if (type == Type.LINK) {
                ds.setColor(ThemeHelper.getInstance().getLinkColor());
            } else {
                ds.setColor(ThemeHelper.getInstance().getQuoteColor());
            }

            ds.setUnderlineText(true);
        } else if (type == Type.SPOILER) {
            if (!clicked) {
                ds.setColor(ThemeHelper.getInstance().getSpoilerColor());
                ds.bgColor = ThemeHelper.getInstance().getSpoilerColor();
                ds.setUnderlineText(false);
            }
        }
    }

    public static class ThreadLink {
        public String board;
        public int threadId;
        public int postId;

        public ThreadLink(String board, int threadId, int postId) {
            this.board = board;
            this.threadId = threadId;
            this.postId = postId;
        }
    }
}
