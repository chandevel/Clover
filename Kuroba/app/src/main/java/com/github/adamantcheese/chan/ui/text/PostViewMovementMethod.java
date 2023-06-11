package com.github.adamantcheese.chan.ui.text;

import static com.github.adamantcheese.chan.utils.StringUtils.RenderOrder.RENDER_ABOVE_ELSE;
import static com.github.adamantcheese.chan.utils.StringUtils.makeSpanOptions;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.text.spans.post_linkables.PostLinkable;
import com.github.adamantcheese.chan.ui.text.spans.post_linkables.SpoilerLinkable;

import java.util.*;

/**
 * A MovementMethod that searches for PostLinkables.<br>
 * See {@link PostLinkable} for more information.
 */
public class PostViewMovementMethod
        extends LinkMovementMethod {
    private final Post post;
    private final PostCellInterface.PostCellCallback callback;

    // This span highlights the selected linkable, but also forces the text to update its draw state.
    private static final BackgroundColorSpan BACKGROUND_SPAN = new BackgroundColorSpan(0x6633B5E5);

    public PostViewMovementMethod(Post post, PostCellInterface.PostCellCallback callback) {
        this.post = post;
        this.callback = callback;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
        int action = event.getActionMasked();

        if (action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_CANCEL) {
            return super.onTouchEvent(widget, buffer, event);
        }

        int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

        Layout layout = widget.getLayout();
        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);

        List<PostLinkable> links = new ArrayList<>(Arrays.asList(buffer.getSpans(off, off, PostLinkable.class)));

        if (links.size() > 0) {
            PostLinkable linkable1 = links.get(0);
            PostLinkable linkable2 = links.size() > 1 ? links.get(1) : null;
            if (action == MotionEvent.ACTION_UP) {
                if (linkable2 == null && linkable1 != null) {
                    //regular, non-spoilered link
                    callback.onPostLinkableClicked(post, linkable1);
                } else if (linkable2 != null && linkable1 != null) {
                    //spoilered link, figure out which span is the spoiler
                    if (linkable1 instanceof SpoilerLinkable) {
                        if (((SpoilerLinkable) linkable1).isSpoilerVisible()) {
                            //linkable2 is the link and we're unspoilered
                            callback.onPostLinkableClicked(post, linkable2);
                        } else {
                            //linkable2 is the link and we're spoilered; don't do the click event on the link yet
                            links.remove(linkable2);
                        }
                    } else if (linkable2 instanceof SpoilerLinkable) {
                        if (((SpoilerLinkable) linkable2).isSpoilerVisible()) {
                            //linkable 1 is the link and we're unspoilered
                            callback.onPostLinkableClicked(post, linkable1);
                        } else {
                            //linkable1 is the link and we're spoilered; don't do the click event on the link yet
                            links.remove(linkable1);
                        }
                    } else {
                        //weird case where a double stack of linkables, but isn't spoilered (some 4chan stickied posts)
                        callback.onPostLinkableClicked(post, linkable1);
                    }
                }

                //do onclick on all spoiler postlinkables afterwards, so that we don't update the spoiler state early
                for (ClickableSpan s : links) {
                    s.onClick(widget);
                }

                cleanHighlightSpannable(buffer);
            } else if (action == MotionEvent.ACTION_DOWN) {
                buffer.setSpan(
                        BACKGROUND_SPAN,
                        buffer.getSpanStart(linkable1),
                        buffer.getSpanEnd(linkable1),
                        makeSpanOptions(RENDER_ABOVE_ELSE)
                );
            } else {
                cleanHighlightSpannable(buffer);
            }

            return true;
        } else {
            cleanHighlightSpannable(buffer);
        }

        return super.onTouchEvent(widget, buffer, event);
    }

    public void cleanHighlightSpannable(Spannable buffer) {
        buffer.removeSpan(BACKGROUND_SPAN);
    }
}
