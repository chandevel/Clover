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

import android.support.annotation.NonNull;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import org.floens.chan.ui.cell.PostCell;
import org.floens.chan.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * A Clickable span that handles post clicks. These are created in ChanParser for post quotes, spoilers etc.<br>
 * PostCells bind callbacks with addCallback and call removeCallback when done.<br>
 * PostCell has a {@link PostCell.PostViewMovementMethod}, that searches spans at the location the TextView was tapped,
 * and handled if it was a PostLinkable.
 */
public class PostLinkable extends ClickableSpan {
    public enum Type {
        QUOTE, LINK, SPOILER, THREAD
    }

    public final Theme theme;
    public final Post post;
    public final String key;
    public final Object value;
    public final Type type;

    private List<Callback> callbacks = new ArrayList<>();
    private boolean spoilerVisible = false;

//    private static boolean testingCallbacks = false;
//    private static HashMap<PostLinkable, List<Callback>> callbacksTest = new HashMap<>();

    public PostLinkable(Theme theme, Post post, String key, Object value, Type type) {
        this.theme = theme;
        this.post = post;
        this.key = key;
        this.value = value;
        this.type = type;

        /*if (!testingCallbacks) {
            testingCallbacks = true;

            AndroidUtils.runOnUiThread(testCallbacksRunnable, 1000);
        }*/
    }

    /*private static Runnable testCallbacksRunnable = new Runnable() {
        @Override
        public void run() {
            AndroidUtils.runOnUiThread(testCallbacksRunnable, 1000);

            Logger.test("Callbacks:");
            for (Map.Entry<PostLinkable, List<Callback>> entry : callbacksTest.entrySet()) {
                if (entry.getValue().size() > 0) {
                    Logger.test(entry.getKey().key + " still has " + entry.getValue().size() + " bounded callbacks");
                }
            }
        }
    };*/

    public void addCallback(Callback callback) {
        callbacks.add(callback);

        /*if (!callbacksTest.containsKey(this)) {
            callbacksTest.put(this, new ArrayList<Callback>());
        }

        callbacksTest.get(this).add(callback);*/
    }

    public void removeCallback(Callback callback) {
        callbacks.remove(callback);

        /*callbacksTest.get(this).remove(callback);*/
    }

    public boolean hasCallback(Callback callback) {
        return callbacks.contains(callback);
    }

    @Override
    public void onClick(View widget) {
        Callback top = topCallback();
        if (top != null) {
            top.onLinkableClick(this);
        }
        spoilerVisible = !spoilerVisible;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        if (type == Type.QUOTE || type == Type.LINK || type == Type.THREAD) {
            if (type == Type.QUOTE) {
                Callback top = topCallback();
                if (value instanceof Integer && top != null && (Integer) value == top.getMarkedNo(this)) {
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
            if (!spoilerVisible) {
                ds.setColor(theme.spoilerColor);
                ds.bgColor = theme.spoilerColor;
                ds.setUnderlineText(false);
            }
        }
    }

    private Callback topCallback() {
        return callbacks.size() > 0 ? callbacks.get(callbacks.size() - 1) : null;
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

    public interface Callback {
        void onLinkableClick(PostLinkable postLinkable);

        int getMarkedNo(PostLinkable postLinkable);
    }
}
