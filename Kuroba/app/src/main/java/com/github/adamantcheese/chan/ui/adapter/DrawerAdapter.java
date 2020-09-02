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

import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.StringUtils;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static androidx.recyclerview.widget.ItemTouchHelper.DOWN;
import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;
import static androidx.recyclerview.widget.ItemTouchHelper.UP;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrDrawable;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;
import static com.github.adamantcheese.chan.utils.StringUtils.getShortString;

public class DrawerAdapter
        extends RecyclerView.Adapter<DrawerAdapter.PinViewHolder> {

    @Inject
    WatchManager watchManager;

    private final Callback callback;
    private Pin highlighted;

    public DrawerAdapter(@NonNull Callback callback) {
        inject(this);
        this.callback = callback;
        setHasStableIds(true);
    }

    public ItemTouchHelper.Callback getItemTouchHelperCallback() {
        return new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
                return makeMovementFlags(UP | DOWN, RIGHT | LEFT);
            }

            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder, @NonNull ViewHolder target
            ) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();

                synchronized (watchManager.getAllPins()) {
                    Pin item = watchManager.getAllPins().remove(from);
                    watchManager.getAllPins().add(to, item);
                }
                watchManager.reorder();
                notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull ViewHolder viewHolder, int direction) {
                synchronized (watchManager.getAllPins()) {
                    callback.onPinRemoved(watchManager.getAllPins().get(viewHolder.getAdapterPosition()));
                }
            }
        };
    }

    @NonNull
    @Override
    public PinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PinViewHolder(inflate(parent.getContext(), R.layout.cell_pin, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PinViewHolder holder, int position) {
        final Pin pin;
        synchronized (watchManager.getAllPins()) {
            pin = watchManager.getAllPins().get(position);
        }
        CharSequence title = pin.loadable.title;
        if (pin.archived) {
            title = PostHelper.prependIcon(holder.itemView.getContext(), title, BitmapRepository.archivedIcon, sp(16));
        }
        if (pin.isSticky) {
            title = PostHelper.prependIcon(holder.itemView.getContext(), title, BitmapRepository.stickyIcon, sp(16));
        }
        holder.title.setText(title);

        loadBookmarkImage(holder, pin);
        holder.image.setGreyscale(!pin.watching);

        WatchManager.PinWatcher pinWatcher = watchManager.getPinWatcher(pin);
        if (pinWatcher != null) {
            SpannableStringBuilder summary = pinWatcher.getSummary();
            if (summary != null) {
                SpannableStringBuilder info = new SpannableStringBuilder("/" + pin.loadable.boardCode + "/ - ");
                info.append(summary);
                holder.threadInfo.setVisibility(VISIBLE);
                holder.threadInfo.setText(info);
            } else {
                holder.threadInfo.setText(BoardHelper.getName(pin.loadable.board));
            }
        } else {
            holder.threadInfo.setVisibility(GONE);
            holder.title.setText(String.format("/%s/ - %s", pin.loadable.boardCode, holder.title.getText()));
        }

        if (ChanSettings.watchEnabled.get()) {
            holder.watchCountText.setVisibility(View.VISIBLE);
            int newPostCount = pin.getNewPostCount();
            int newQuoteCount = pin.getNewQuoteCount();
            String text = getShortString(newPostCount) + (newQuoteCount > 0 ? "/" + getShortString(newQuoteCount) : "");
            holder.watchCountText.setText(text);

            SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);

            if (pin.getNewQuoteCount() > 0) {
                holder.watchCountText.setTextColor(getColor(R.color.md_red_500));
            } else if (savedThread != null && savedThread.isFullyDownloaded) {
                holder.watchCountText.setTextColor(getColor(R.color.md_green_500));
            } else if (savedThread != null && savedThread.isRunning()) {
                holder.watchCountText.setTextColor(getColor(R.color.md_orange_700));
            } else if (!pin.watching) {
                holder.watchCountText.setTextColor(getColor(R.color.md_grey_600));
            } else {
                holder.watchCountText.setTextColor(getColor(R.color.md_light_blue_400));
            }
        } else {
            holder.watchCountText.setVisibility(GONE);
        }

        if (pin == highlighted) {
            holder.itemView.setBackground(new ColorDrawable(getAttrColor(holder.itemView.getContext(),
                    R.attr.highlight_color
            )));
        } else {
            holder.itemView.setBackground(getAttrDrawable(holder.itemView.getContext(),
                    android.R.attr.selectableItemBackground
            ));
        }
    }

    @Override
    public void onViewRecycled(@NonNull PinViewHolder holder) {
        holder.image.setUrl(null);
        holder.watchCountText.setText("");
        holder.title.setText("");
        holder.threadInfo.setText("");
    }

    @Override
    public int getItemCount() {
        synchronized (watchManager.getAllPins()) {
            return watchManager.getAllPins().size();
        }
    }

    @Override
    public long getItemId(int position) {
        synchronized (watchManager.getAllPins()) {
            return watchManager.getAllPins().get(position).id + 10;
        }
    }

    public void setHighlightedPin(Pin highlighted) {
        Pin prevHighlight = this.highlighted;
        this.highlighted = highlighted;
        synchronized (watchManager.getAllPins()) {
            notifyItemChanged(watchManager.getAllPins().indexOf(prevHighlight));
            notifyItemChanged(watchManager.getAllPins().indexOf(highlighted));
        }
    }

    private void loadBookmarkImage(PinViewHolder holder, Pin pin) {
        if (holder.image.getBitmap() != null) return;
        holder.image.setUrl(pin.loadable.thumbnailUrl, dp(48), dp(48));

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
        if (savedThread == null || !savedThread.isFullyDownloaded) {
            return;
        }

        String filename = StringUtils.convertThumbnailUrlToFilenameOnDisk(pin.loadable.thumbnailUrl);
        if (TextUtils.isEmpty(filename)) {
            return;
        }

        holder.image.setUrlFromDisk(pin.loadable, filename, false, dp(48), dp(48));
    }

    public class PinViewHolder
            extends ViewHolder {
        private ThumbnailView image;
        private TextView title;
        private TextView threadInfo;
        private TextView watchCountText;

        private PinViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.thumb);
            image.setCircular(true);
            title = itemView.findViewById(R.id.title);
            title.setTypeface(ThemeHelper.getTheme().mainFont);
            threadInfo = itemView.findViewById(R.id.thread_info);
            watchCountText = itemView.findViewById(R.id.watch_count);
            watchCountText.setTypeface(ThemeHelper.getTheme().mainFont);
            watchCountText.setPaintFlags(Paint.ANTI_ALIAS_FLAG);

            image.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                synchronized (watchManager.getAllPins()) {
                    if (pos >= 0 && pos < watchManager.getAllPins().size()) {
                        watchManager.toggleWatch(watchManager.getAllPins().get(pos));
                    }
                }
            });

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                synchronized (watchManager.getAllPins()) {
                    if (pos >= 0 && pos < watchManager.getAllPins().size()) {
                        callback.onPinClicked(watchManager.getAllPins().get(pos));
                    }
                }
            });

            watchCountText.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                synchronized (watchManager.getAllPins()) {
                    if (pos >= 0 && pos < watchManager.getAllPins().size()) {
                        watchManager.toggleWatch(watchManager.getAllPins().get(pos));
                    }
                }
            });
        }
    }

    public interface Callback {
        void onPinClicked(Pin pin);

        void onPinRemoved(Pin pin);
    }
}
