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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.net.ImageLoadable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.layout.SearchLayout;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.StringUtils;

import javax.inject.Inject;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;
import static com.github.adamantcheese.chan.utils.StringUtils.getShortString;

public class DrawerPinAdapter
        extends RecyclerView.Adapter<DrawerPinAdapter.PinViewHolder>
        implements SearchLayout.SearchLayoutCallback {

    @Inject
    WatchManager watchManager;

    private String searchQuery = "";
    private final Callback callback;
    private Pin highlighted;

    private static final ColorMatrixColorFilter greyFilter;

    static {
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0);
        greyFilter = new ColorMatrixColorFilter(grey);
    }

    public DrawerPinAdapter(@NonNull Callback callback) {
        inject(this);
        this.callback = callback;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public PinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PinViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_pin, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PinViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        final Pin pin;
        synchronized (watchManager.getAllPins()) {
            pin = watchManager.getAllPins().get(position);
        }

        if (!StringUtils.containsIgnoreCase(pin.loadable.title, searchQuery)) {
            holder.itemView.setVisibility(View.GONE);
            ViewGroup.LayoutParams oldParams = holder.itemView.getLayoutParams();
            oldParams.height = 0;
            oldParams.width = 0;
            holder.itemView.setLayoutParams(oldParams);
            return;
        } else {
            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemView.getLayoutParams().width = MATCH_PARENT;
            holder.itemView.getLayoutParams().height = WRAP_CONTENT;
        }

        holder.title.setText(applySearchSpans(ThemeHelper.getTheme(), pin.loadable.title, searchQuery));

        holder.loadUrl(pin.loadable.thumbnailUrl, holder.image);
        holder.image.setColorFilter(pin.watching ? null : greyFilter);

        WatchManager.PinWatcher pinWatcher = watchManager.getPinWatcher(pin);
        if (pinWatcher != null) {
            CharSequence summary = pinWatcher.getSummary();
            if (summary == null) {
                summary = pin.loadable.board.getFormattedName();
            } else {
                summary = TextUtils.concat("/", pin.loadable.boardCode, "/ - ", summary);
            }

            if (pin.archived) {
                summary = PostHelper.prependIcon(context, summary, BitmapRepository.archivedIcon, sp(16));
            }
            if (pin.isSticky) {
                summary = PostHelper.prependIcon(context, summary, BitmapRepository.stickyIcon, sp(16));
            }

            holder.title.getLayoutParams().height = WRAP_CONTENT;
            holder.threadInfo.setVisibility(VISIBLE);
            holder.threadInfo.setText(summary);
        } else {
            holder.title.getLayoutParams().height = MATCH_PARENT;
            holder.threadInfo.setVisibility(GONE);
            holder.title.setText(String.format("/%s/ - %s", pin.loadable.boardCode, holder.title.getText()));
        }

        if (ChanSettings.watchEnabled.get()) {
            holder.watchCountText.setVisibility(View.VISIBLE);
            int newPostCount = pin.getNewPostCount();
            int newQuoteCount = pin.getNewQuoteCount();
            String text = getShortString(newPostCount) + (newQuoteCount > 0 ? "/" + getShortString(newQuoteCount) : "");
            holder.watchCountText.setText(text);

            if (pin.getNewQuoteCount() > 0) {
                holder.watchCountText.setTextColor(getColor(R.color.md_red_500));
            } else if (!pin.watching) {
                holder.watchCountText.setTextColor(getColor(R.color.md_grey_600));
            } else {
                holder.watchCountText.setTextColor(getColor(R.color.md_light_blue_400));
            }
        } else {
            holder.watchCountText.setVisibility(GONE);
        }

        int backColor = getAttrColor(context, R.attr.backcolor);
        int highlightColor = getAttrColor(context, R.attr.highlight_color);
        if (pin.shouldHighlight.get()) {
            holder.itemView.setBackgroundColor(ColorUtils.compositeColors(highlightColor, backColor));
        } else {
            holder.itemView.setBackgroundColor(backColor);
        }
    }

    @Override
    public void onViewRecycled(@NonNull PinViewHolder holder) {
        holder.cancelLoad(holder.image);
        holder.image.setColorFilter(null);
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
            return watchManager.getAllPins().get(position).loadable.no;
        }
    }

    public void setHighlightedPin(@Nullable Pin highlighted) {
        synchronized (watchManager.getAllPins()) {
            for (Pin p : watchManager.getAllPins()) {
                p.shouldHighlight.set(p == highlighted);
            }
        }
        notifyItemChanged(watchManager.getAllPins().indexOf(this.highlighted));
        notifyItemChanged(watchManager.getAllPins().indexOf(highlighted));
        this.highlighted = highlighted;
    }

    @Override
    public void onSearchEntered(String entered) {
        searchQuery = entered;
        notifyDataSetChanged();
    }

    @Override
    public void onClearPressedWhenEmpty() {
        searchQuery = "";
        notifyDataSetChanged();
    }

    public class PinViewHolder
            extends ViewHolder
            implements ImageLoadable {
        private final ImageView image;
        private final TextView title;
        private final TextView threadInfo;
        private final TextView watchCountText;
        private Call thumbnailCall;
        private HttpUrl lastHttpUrl;

        private PinViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.thumb);
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

        @Override
        public HttpUrl getLastHttpUrl() {
            return lastHttpUrl;
        }

        @Override
        public void setLastHttpUrl(HttpUrl url) {
            lastHttpUrl = url;
        }

        @Override
        public Call getImageCall() {
            return thumbnailCall;
        }

        @Override
        public void setImageCall(Call call) {
            thumbnailCall = call;
        }
    }

    public interface Callback {
        void onPinClicked(Pin pin);

        void onPinRemoved(Pin pin);
    }
}
