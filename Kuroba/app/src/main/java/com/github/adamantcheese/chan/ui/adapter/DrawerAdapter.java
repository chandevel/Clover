/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.PinHelper;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.AnimationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    //PIN_OFFSET is the number of items before the pins
    //(in this case, settings, history, and the bookmarked threads title)
    private static final int PIN_OFFSET = 3;

    //NOTE: TYPE_PIN is public so that in DrawerController we can set it's recycled count to 0 in the pool
    //We avoid recycling them as it was causing issues with the TextViews internal to those holders which update their text style
    private static final int TYPE_HEADER = 0;
    public static final int TYPE_PIN = 1;
    private static final int TYPE_LINK = 2;
    private static final int TYPE_BOARD_INPUT = 3;
    private static final int TYPE_DIVIDER = 4;

    @Inject
    WatchManager watchManager;

    private Context context;
    private Drawable downloadIconOutline;
    private Drawable downloadIconFilled;

    private final Callback callback;
    private List<Pin> pins = new ArrayList<>();
    private Pin highlighted;

    public DrawerAdapter(Callback callback, Context context) {
        inject(this);
        this.callback = callback;
        this.context = context;
        setHasStableIds(true);

        Theme currentTheme = ThemeHelper.getTheme();

        downloadIconOutline = context.getDrawable(R.drawable.ic_download0)
                .mutate();
        downloadIconOutline.setTint(currentTheme.textPrimary);

        downloadIconFilled = context.getDrawable(R.drawable.ic_download5)
                .mutate();
        downloadIconFilled.setTint(0xFF808080);
    }

    public void setPinHighlighted(Pin highlighted) {
        this.highlighted = highlighted;
    }

    public ItemTouchHelper.Callback getItemTouchHelperCallback() {
        return new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                boolean pin = getItemViewType(viewHolder.getAdapterPosition()) == TYPE_PIN;
                int dragFlags = pin ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
                int swipeFlags = pin ? ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT : 0;

                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();

                if (getItemViewType(to) == TYPE_PIN) {
                    Pin item = pins.remove(from - PIN_OFFSET);
                    pins.add(to - PIN_OFFSET, item);
                    watchManager.reorder(pins);
                    notifyItemMoved(from, to);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                callback.onPinRemoved(pins.get(viewHolder.getAdapterPosition() - PIN_OFFSET));
            }
        };
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_header, parent, false));
            case TYPE_PIN:
                return new PinViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_pin, parent, false));
            case TYPE_LINK:
                return new LinkHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_link, parent, false));
            case TYPE_BOARD_INPUT:
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_browse_input, parent, false)) {
                };
            case TYPE_DIVIDER:
                return new DividerHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_divider, parent, false));
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case TYPE_HEADER:
                HeaderHolder headerHolder = (HeaderHolder) holder;
                headerHolder.text.setText(R.string.drawer_pinned);
                ThemeHelper.getTheme().clearDrawable.apply(headerHolder.clear);

                break;
            case TYPE_PIN:
                final Pin pin = pins.get(position - PIN_OFFSET);
                PinViewHolder pinHolder = (PinViewHolder) holder;
                updatePinViewHolder(pinHolder, pin);

                break;
            case TYPE_LINK:
                LinkHolder linkHolder = (LinkHolder) holder;
                switch (position) {
                    case 0:
                        linkHolder.text.setText(R.string.drawer_settings);
                        ThemeHelper.getTheme().settingsDrawable.apply(linkHolder.image);
                        break;
                    case 1:
                        linkHolder.text.setText(R.string.drawer_history);
                        ThemeHelper.getTheme().historyDrawable.apply(linkHolder.image);
                        break;
                }
                break;
            case TYPE_DIVIDER:
                ((DividerHolder) holder).withBackground(position != 0);
                break;
            default:
                break;
        }
    }

    @Override
    public int getItemCount() {
        return pins.size() + PIN_OFFSET;
    }

    @Override
    public long getItemId(int position) {
        position -= PIN_OFFSET;
        if (position >= 0 && position < pins.size()) {
            return pins.get(position).id + 10;
        } else {
            return position;
        }
    }

    @Override
    public int getItemViewType(int position) {
        switch (position) {
            case 0:
            case 1:
                return TYPE_LINK;
            case 2:
                return TYPE_HEADER;
            default:
                return TYPE_PIN;
        }
    }

    public void onPinsChanged(List<Pin> pins) {
        this.pins.clear();
        this.pins.addAll(pins);
        Collections.sort(pins);
        notifyDataSetChanged();
    }

    public void onPinAdded(Pin pin) {
        pins.add(pin);
        Collections.sort(pins);
        notifyItemInserted(pins.indexOf(pin) + PIN_OFFSET);
    }

    public void onPinRemoved(RecyclerView recyclerView, Pin pin) {
        int index = pins.indexOf(pin) + PIN_OFFSET;
        PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(index);
        if (holder != null) {
            pins.remove(pin);
            Collections.sort(pins);
            notifyItemRemoved(index);
        }
    }

    public void onPinChanged(RecyclerView recyclerView, Pin pin) {
        PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(pins.indexOf(pin) + PIN_OFFSET);
        if (holder != null) {
            updatePinViewHolder(holder, pin);
            notifyItemChanged(pins.indexOf(pin) + PIN_OFFSET);
        }
    }

    public void updateHighlighted(RecyclerView recyclerView) {
        for (int i = 0; i < pins.size(); i++) {
            PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(i + PIN_OFFSET);
            if (holder != null) {
                updatePinViewHolder(holder, pins.get(i));
                notifyItemChanged(i + PIN_OFFSET);
            }
        }
    }

    private void updatePinViewHolder(PinViewHolder holder, Pin pin) {
        TextView watchCount = holder.watchCountText;
        LinearLayout.LayoutParams newParams = new LinearLayout.LayoutParams(
                watchCount.getLayoutParams().width,
                watchCount.getLayoutParams().height,
                ChanSettings.shortPinInfo.get() ? 1.5f : 2.5f);

        watchCount.setLayoutParams(newParams);

        CharSequence text = pin.loadable.title;
        if (pin.archived) {
            Bitmap archivedIcon = BitmapFactory.decodeResource(AndroidUtils.getRes(), R.drawable.archived_icon);
            text = PostHelper.prependIcon(text, archivedIcon, sp(16));
        }

        TextView bookmarkLabel = holder.textView;
        bookmarkLabel.setText(text);
        holder.image.setUrl(pin.thumbnailUrl, dp(40), dp(40));

        if (ChanSettings.watchEnabled.get()) {
            if (PinType.hasWatchNewPostsFlag(pin.pinType)) {
                String newCount = PinHelper.getShortUnreadCount(pin.getNewPostCount());
                String totalCount = PinHelper.getShortUnreadCount(watchManager.getPinWatcher(pin).getReplyCount());
                watchCount.setVisibility(View.VISIBLE);
                watchCount.setText(ChanSettings.shortPinInfo.get() ? newCount : totalCount + " / " + newCount);

                if (!pin.watching) {
                    watchCount.setTextColor(0xff898989); // TODO material colors
                } else if (pin.getNewQuoteCount() > 0) {
                    watchCount.setTextColor(0xffFF4444);
                } else {
                    watchCount.setTextColor(0xff33B5E5);
                }

                if ((watchManager.getPinWatcher(pin).getReplyCount() >= pin.loadable.board.bumpLimit && pin.loadable.board.bumpLimit > 0) ||
                        (watchManager.getPinWatcher(pin).getImageCount() >= pin.loadable.board.imageLimit && pin.loadable.board.imageLimit > 0)) {
                    watchCount.setTypeface(watchCount.getTypeface(), Typeface.ITALIC);
                } else {
                    watchCount.setTypeface(watchCount.getTypeface(), Typeface.NORMAL);
                }

                // The 16dp padding now belongs to the counter, for a bigger touch area
                bookmarkLabel.setPadding(bookmarkLabel.getPaddingLeft(), bookmarkLabel.getPaddingTop(),
                        0, bookmarkLabel.getPaddingBottom());
                watchCount.setPadding(dp(16), watchCount.getPaddingTop(),
                        watchCount.getPaddingRight(), watchCount.getPaddingBottom());
            } else {
                // FIXME: apparently we don't need to set the visibility to GONE here. Needs research.
                watchCount.setVisibility(View.GONE);
            }
        } else {
            // The 16dp padding now belongs to the textview, for better ellipsize
            watchCount.setVisibility(View.GONE);
            holder.threadDownloadIcon.setVisibility(View.GONE);

            bookmarkLabel.setPadding(bookmarkLabel.getPaddingLeft(), bookmarkLabel.getPaddingTop(),
                    dp(16), bookmarkLabel.getPaddingBottom());
        }

        setPinDownloadIcon(holder, pin);

        boolean highlighted = pin == this.highlighted;
        if (highlighted && !holder.highlighted) {
            holder.itemView.setBackgroundColor(0x22000000);
            holder.highlighted = true;
        } else if (!highlighted && holder.highlighted) {
            holder.itemView.setBackground(AndroidUtils.getAttrDrawable(holder.itemView.getContext(), android.R.attr.selectableItemBackground));
            holder.highlighted = false;
        }
    }

    private void setPinDownloadIcon(PinViewHolder holder, Pin pin) {
        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
        if (savedThread == null) {
            holder.threadDownloadIcon.setVisibility(View.GONE);
            return;
        }

        holder.threadDownloadIcon.setVisibility(View.VISIBLE);

        if (savedThread.isFullyDownloaded) {
            holder.threadDownloadIcon.setImageDrawable(downloadIconFilled);
            return;
        }

        if (savedThread.isStopped) {
            holder.threadDownloadIcon.setImageDrawable(downloadIconOutline);
            return;
        }

        AnimationDrawable downloadAnimation = AnimationUtils.createAnimatedDownloadIconWithThemeTextPrimaryColor(
                context,
                ThemeHelper.getTheme());
        holder.threadDownloadIcon.setImageDrawable(downloadAnimation);

        if (!downloadAnimation.isRunning()) {
            downloadAnimation.start();
        }
    }

    private class PinViewHolder extends RecyclerView.ViewHolder {
        private boolean highlighted;
        private ThumbnailView image;
        private TextView textView;
        private TextView watchCountText;
        private AppCompatImageView threadDownloadIcon;

        private PinViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.thumb);
            image.setCircular(true);
            textView = itemView.findViewById(R.id.text);
            textView.setTypeface(ThemeHelper.getTheme().mainFont);
            watchCountText = itemView.findViewById(R.id.watch_count);
            watchCountText.setTypeface(ThemeHelper.getTheme().mainFont);
            threadDownloadIcon = itemView.findViewById(R.id.thread_download_icon);

            setRoundItemBackground(watchCountText);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition() - PIN_OFFSET;
                if (pos >= 0 && pos < pins.size()) {
                    callback.onPinClicked(pins.get(pos));
                }
            });

            watchCountText.setOnClickListener(v -> {
                int pos = getAdapterPosition() - PIN_OFFSET;
                if (pos >= 0 && pos < pins.size()) {
                    callback.onWatchCountClicked(pins.get(pos));
                }
            });
        }
    }

    public class HeaderHolder extends RecyclerView.ViewHolder {
        private TextView text;
        private ImageView clear;

        private HeaderHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
            text.setTypeface(ThemeHelper.getTheme().mainFont);
            clear = itemView.findViewById(R.id.clear);
            setRoundItemBackground(clear);
            clear.setOnClickListener(v -> callback.onHeaderClicked(HeaderAction.CLEAR));
            clear.setOnLongClickListener(v -> {
                callback.onHeaderClicked(HeaderAction.CLEAR_ALL);
                return true;
            });
        }
    }

    public enum HeaderAction {
        CLEAR, CLEAR_ALL
    }

    private class LinkHolder extends RecyclerView.ViewHolder {
        private ImageView image;
        private TextView text;

        private LinkHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            text.setTypeface(ThemeHelper.getTheme().mainFont);

            itemView.setOnClickListener(v -> {
                switch (getAdapterPosition()) {
                    case 0:
                        callback.openSettings();
                        break;
                    case 1:
                        callback.openHistory();
                        break;
                }
            });
        }
    }

    private class DividerHolder extends RecyclerView.ViewHolder {
        private boolean withBackground = false;
        private View divider;

        private DividerHolder(View itemView) {
            super(itemView);
            divider = itemView.findViewById(R.id.divider);
        }

        private void withBackground(boolean withBackground) {
            if (withBackground != this.withBackground) {
                this.withBackground = withBackground;
                if (withBackground) {
                    divider.setBackgroundColor(getAttrColor(itemView.getContext(), R.attr.divider_color));
                } else {
                    divider.setBackgroundColor(0);
                }
            }
        }
    }

    public interface Callback {
        void onPinClicked(Pin pin);

        void onWatchCountClicked(Pin pin);

        void onHeaderClicked(HeaderAction headerAction);

        void onPinRemoved(Pin pin);

        void openSettings();

        void openHistory();
    }
}
