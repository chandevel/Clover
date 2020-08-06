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
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrDrawable;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;
import static com.github.adamantcheese.chan.utils.StringUtils.getShortString;
import static java.util.concurrent.TimeUnit.MINUTES;

public class DrawerAdapter
        extends RecyclerView.Adapter<ViewHolder> {

    /*
     * All these values need to match your methods in onBindViewHolder and getItemViewType.
     */
    private static final int TYPE_LINK = 0;
    private static final int LINK_COUNT = 2;

    private static final int TYPE_HEADER = 1;
    private static final int HEADER_COUNT = 1;

    private static final int TYPE_PIN = 2;
    private static final int PIN_OFFSET = LINK_COUNT + HEADER_COUNT;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    WatchManager watchManager;

    private final Callback callback;
    private Pin highlighted;

    public DrawerAdapter(Callback callback) {
        inject(this);
        this.callback = callback;
        setHasStableIds(true);
    }

    public ItemTouchHelper.Callback getItemTouchHelperCallback() {
        return new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
                boolean pin = getItemViewType(viewHolder.getAdapterPosition()) == TYPE_PIN;
                int dragFlags = pin ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
                int swipeFlags = pin ? ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT : 0;

                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public boolean onMove(
                    @NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder, @NonNull ViewHolder target
            ) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();

                if (getItemViewType(to) == TYPE_PIN) {
                    Pin item = watchManager.getAllPins().remove(from - PIN_OFFSET);
                    watchManager.getAllPins().add(to - PIN_OFFSET, item);
                    watchManager.reorder();
                    notifyItemMoved(from, to);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onSwiped(@NonNull ViewHolder viewHolder, int direction) {
                callback.onPinRemoved(watchManager.getAllPins().get(viewHolder.getAdapterPosition() - PIN_OFFSET));
            }
        };
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case TYPE_LINK:
                return new LinkHolder(inflate(parent.getContext(), R.layout.cell_link, parent, false));
            case TYPE_HEADER:
                return new HeaderHolder(inflate(parent.getContext(), R.layout.cell_header, parent, false));
            case TYPE_PIN:
                return new PinViewHolder(inflate(parent.getContext(), R.layout.cell_pin, parent, false));
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case TYPE_LINK:
                LinkHolder linkHolder = (LinkHolder) holder;
                switch (position) {
                    case 0:
                        linkHolder.text.setText(R.string.drawer_settings);
                        linkHolder.image.setImageResource(R.drawable.ic_settings_themed_24dp);
                        updateNotificationIcon(linkHolder);
                        break;
                    case 1:
                        linkHolder.text.setText(R.string.drawer_history);
                        linkHolder.image.setImageResource(R.drawable.ic_history_themed_24dp);
                        break;
                }
                break;
            case TYPE_HEADER:
                break;
            case TYPE_PIN:
                final Pin pin = watchManager.getAllPins().get(position - PIN_OFFSET);
                updatePinViewHolder((PinViewHolder) holder, pin);
                break;
        }
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        switch (holder.getItemViewType()) {
            case TYPE_LINK:
                break;
            case TYPE_HEADER:
                HeaderHolder headerHolder = (HeaderHolder) holder;
                mainHandler.removeCallbacks(headerHolder.refreshRunnable);
                break;
            case TYPE_PIN:
                PinViewHolder pinHolder = (PinViewHolder) holder;
                pinHolder.image.setUrl(null);
                pinHolder.watchCountText.setText("");
                pinHolder.title.setText("");
                pinHolder.threadInfo.setText("");
                break;
        }
    }

    private void updateNotificationIcon(LinkHolder linkHolder) {
        SettingsNotificationManager settingsNotificationManager = instance(SettingsNotificationManager.class);
        SettingNotificationType notificationType = settingsNotificationManager.getNotificationByPriority();

        Logger.d(this, "updateNotificationIcon() called for type  " + notificationType);

        if (notificationType != null) {
            int color = getRes().getColor(notificationType.getNotificationIconTintColor());

            linkHolder.notificationIcon.setVisibility(VISIBLE);
            linkHolder.notificationIcon.setColorFilter(color);

            if (settingsNotificationManager.notificationsCount() > 1) {
                linkHolder.totalNotificationsCount.setVisibility(VISIBLE);
                linkHolder.totalNotificationsCount.setText(String.valueOf(settingsNotificationManager.notificationsCount()));
            } else {
                linkHolder.totalNotificationsCount.setVisibility(GONE);
            }
        } else {
            linkHolder.notificationIcon.setVisibility(GONE);
            linkHolder.totalNotificationsCount.setVisibility(GONE);
        }
    }

    @Override
    public int getItemCount() {
        return watchManager.getAllPins().size() + PIN_OFFSET;
    }

    @Override
    public long getItemId(int position) {
        position -= PIN_OFFSET;
        if (position >= 0 && position < watchManager.getAllPins().size()) {
            return watchManager.getAllPins().get(position).id + 10;
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

    public void onNotificationsChanged() {
        BackgroundUtils.ensureMainThread();
        for (int i = 0; i < LINK_COUNT; i++) {
            notifyItemChanged(i);
        }
    }

    public void onPinAdded(Pin pin) {
        notifyItemInserted(watchManager.getAllPins().indexOf(pin) + PIN_OFFSET);
    }

    public void onPinRemoved(int index) {
        notifyItemRemoved(index + PIN_OFFSET);
    }

    public void onPinChanged(RecyclerView recyclerView, Pin pin) {
        PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(
                watchManager.getAllPins().indexOf(pin) + PIN_OFFSET);
        if (holder != null) {
            updatePinViewHolder(holder, pin);
            holder.itemView.invalidate();
        }
    }

    public void setPinHighlighted(RecyclerView recyclerView, Pin highlighted) {
        this.highlighted = highlighted;
        for (int i = 0; i < watchManager.getAllPins().size(); i++) {
            PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(i + PIN_OFFSET);
            if (holder != null) {
                updatePinViewHolder(holder, watchManager.getAllPins().get(i));
                holder.itemView.invalidate();
            }
        }
    }

    private void updatePinViewHolder(PinViewHolder holder, Pin pin) {
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

        if (pin == this.highlighted) {
            holder.itemView.setBackground(new ColorDrawable(getAttrColor(holder.itemView.getContext(),
                    R.attr.highlight_color
            )));
        } else {
            holder.itemView.setBackground(getAttrDrawable(holder.itemView.getContext(),
                    android.R.attr.selectableItemBackground
            ));
        }
    }

    private void loadBookmarkImage(PinViewHolder holder, Pin pin) {
        if (holder.image.getBitmap() != null) return;
        holder.image.setUrl(pin.thumbnailUrl, dp(48), dp(48));

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
        if (savedThread == null || !savedThread.isFullyDownloaded) {
            return;
        }

        String filename = StringUtils.convertThumbnailUrlToFilenameOnDisk(pin.thumbnailUrl);
        if (TextUtils.isEmpty(filename)) {
            return;
        }

        holder.image.setUrlFromDisk(pin.loadable, filename, false, dp(48), dp(48));
    }

    private class PinViewHolder
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
                int pos = getAdjustedAdapterPosition();
                if (pos >= 0 && pos < watchManager.getAllPins().size()) {
                    watchManager.toggleWatch(watchManager.getAllPins().get(pos));
                }
            });

            itemView.setOnClickListener(v -> {
                int pos = getAdjustedAdapterPosition();
                if (pos >= 0 && pos < watchManager.getAllPins().size()) {
                    callback.onPinClicked(watchManager.getAllPins().get(pos));
                }
            });

            watchCountText.setOnClickListener(v -> {
                int pos = getAdjustedAdapterPosition();
                if (pos >= 0 && pos < watchManager.getAllPins().size()) {
                    watchManager.toggleWatch(watchManager.getAllPins().get(pos));
                }
            });
        }

        private int getAdjustedAdapterPosition() {
            return getAdapterPosition() - PIN_OFFSET;
        }
    }

    public class HeaderHolder
            extends ViewHolder {
        private ImageView refresh;

        private HeaderHolder(View itemView) {
            super(itemView);
            TextView text = itemView.findViewById(R.id.text);
            text.setTypeface(ThemeHelper.getTheme().mainFont);
            refresh = itemView.findViewById(R.id.refresh);
            refresh.setOnClickListener(v -> {
                watchManager.onWake();
                refresh.setVisibility(GONE);
                mainHandler.postDelayed(refreshRunnable, MINUTES.toMillis(5));
            });

            ImageView clear = itemView.findViewById(R.id.clear);
            clear.setOnClickListener(v -> callback.onHeaderClicked(HeaderAction.CLEAR));
            clear.setOnLongClickListener(v -> {
                callback.onHeaderClicked(HeaderAction.CLEAR_ALL);
                return true;
            });
        }

        private Runnable refreshRunnable = new Runnable() {
            @Override
            public void run() {
                refresh.setVisibility(VISIBLE);
            }
        };
    }

    public enum HeaderAction {
        CLEAR,
        CLEAR_ALL
    }

    private class LinkHolder
            extends ViewHolder {
        private ImageView image;
        private TextView text;
        private ImageView notificationIcon;
        private TextView totalNotificationsCount;

        private LinkHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            text.setTypeface(ThemeHelper.getTheme().mainFont);
            notificationIcon = itemView.findViewById(R.id.setting_notification_icon);
            totalNotificationsCount = itemView.findViewById(R.id.setting_notification_total_count);
            updatePaddings(notificationIcon, dp(4), dp(4), dp(4), dp(4));

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

    public interface Callback {
        void onPinClicked(Pin pin);

        void onHeaderClicked(HeaderAction headerAction);

        void onPinRemoved(Pin pin);

        void openSettings();

        void openHistory();
    }
}
