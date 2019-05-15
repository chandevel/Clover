/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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
package com.github.adamantcheese.chan.core.model;

import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.theme.Theme;

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes, spoilers etc.<br>
 * PostCell has a {@link PostCell.PostViewMovementMethod}, that searches spans at the location the TextView was tapped,
 * and handled if it was a PostLinkable.
 */
public class PostLinkable extends ClickableSpan {
    public enum Type {
        QUOTE, LINK, SPOILER, THREAD, BOARD, SEARCH
    }

    public final Theme theme;
    public final CharSequence key;
    public final Object value;
    public final Type type;

    private boolean spoilerVisible = ChanSettings.revealTextSpoilers.get();
    private int markedNo = -1;

    public PostLinkable(Theme theme, CharSequence key, Object value, Type type) {
        this.theme = theme;
        this.key = key;
        this.value = value;
        this.type = type;
    }

    @Override
    public void onClick(View widget) {
        spoilerVisible = !spoilerVisible;
    }

    public void setMarkedNo(int markedNo) {
        this.markedNo = markedNo;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        if (type == Type.QUOTE || type == Type.LINK || type == Type.THREAD || type == Type.BOARD || type == Type.SEARCH) {
            if (type == Type.QUOTE) {
                if (value instanceof Integer && ((int) value) == markedNo) {
                    ds.setColor(theme.highlightQuoteColor);
                } else {
                    ds.setColor(theme.quoteColor);
                }
            } else if (type == Type.LINK) {
                ds.setColor(theme.linkColor);
            } else {
                ds.setColor(theme.quoteColor);
            }

            ds.setUnderlineText(true);
        } else if (type == Type.SPOILER) {
            ds.bgColor = theme.spoilerColor;
            ds.setUnderlineText(false);
            if (!spoilerVisible) {
                ds.setColor(theme.spoilerColor);
            } else {
                ds.setColor(theme.textColorRevealSpoiler);
            }
        }
    }

    public boolean getSpoilerState() {
        return spoilerVisible;
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

    public static class SearchLink {
        public String board;
        public String search;

        public SearchLink(String board, String search) {
            this.board = board;
            this.search = search;
        }
    }
}
