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

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

/**
 * Anything that links to something in a post uses this entity.
 */
public class PostLinkable extends ClickableSpan {
    public static enum Type {
        QUOTE, LINK, SPOILER
    };

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
        if (type == Type.QUOTE || type == Type.LINK) {
            ds.setColor(type == Type.QUOTE ? Color.argb(255, 221, 0, 0) : Color.argb(255, 0, 0, 180));
            ds.setUnderlineText(true);
        } else if (type == Type.SPOILER) {
            ds.setColor(0x00000000);
            ds.bgColor = 0xff000000;
            ds.setUnderlineText(false);
        }
    }
}
