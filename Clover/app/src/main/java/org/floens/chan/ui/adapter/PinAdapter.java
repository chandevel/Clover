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
package org.floens.chan.ui.adapter;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.PostHelper;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;
import static org.floens.chan.utils.AndroidUtils.sp;

public class PinAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SwipeListener.Callback {
    private static final int PIN_OFFSET = 4;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PIN = 1;
    private static final int TYPE_LINK = 2;
    private static final int TYPE_DIVIDER = 3;

    private final Callback callback;
    private List<Pin> pins = new ArrayList<>();

    public PinAdapter(Callback callback) {
        this.callback = callback;
        setHasStableIds(true);
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
            case TYPE_DIVIDER:
                return new DividerHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_divider, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case TYPE_HEADER:
                HeaderHolder headerHolder = (HeaderHolder) holder;
                headerHolder.text.setText(R.string.drawer_pinned);
                theme().settingsDrawable.apply(headerHolder.image);

                break;
            case TYPE_PIN:
                final Pin pin = pins.get(position - PIN_OFFSET);
                PinViewHolder pinHolder = (PinViewHolder) holder;
                updatePinViewHolder(pinHolder, pin);

                break;
            case TYPE_LINK:
                LinkHolder linkHolder = (LinkHolder) holder;
                switch (position) {
                    case 1:
                        linkHolder.text.setText(R.string.settings_screen);
                        theme().settingsDrawable.apply(linkHolder.image);
                        break;
                }
                break;
            case TYPE_DIVIDER:
                ((DividerHolder) holder).withBackground(position != 0);
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
            case 1:
                return TYPE_LINK;
            case 0:
            case 2:
                return TYPE_DIVIDER;
            case 3:
                return TYPE_HEADER;
            default:
                return TYPE_PIN;
        }
    }

    public void onPinsChanged(List<Pin> pins) {
        this.pins.clear();
        this.pins.addAll(pins);
        notifyDataSetChanged();
    }

    public void onPinAdded(Pin pin) {
        pins.add(pin);
        notifyDataSetChanged();
    }

    public void onPinRemoved(Pin pin) {
        pins.remove(pin);
        notifyDataSetChanged();
    }

    public void onPinChanged(RecyclerView recyclerView, Pin pin) {
        PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(pins.indexOf(pin) + PIN_OFFSET);
        if (holder != null) {
            updatePinViewHolder(holder, pin);
        }
    }

    public void updateHighlighted(RecyclerView recyclerView) {
        for (int i = 0; i < pins.size(); i++) {
            PinViewHolder holder = (PinViewHolder) recyclerView.findViewHolderForAdapterPosition(i + PIN_OFFSET);
            if (holder != null) {
                updatePinViewHolder(holder, pins.get(i));
            }
        }
    }

    @Override
    public SwipeListener.Swipeable getSwipeable(int position) {
        return getItemViewType(position) == TYPE_PIN ? SwipeListener.Swipeable.RIGHT : SwipeListener.Swipeable.NO;
    }

    @Override
    public void removeItem(int position) {
        Chan.getWatchManager().removePin(pins.get(position - PIN_OFFSET));
    }

    @Override
    public boolean isMoveable(int position) {
        return getItemViewType(position) == TYPE_PIN;
    }

    @Override
    public void moveItem(int from, int to) {
        Pin item = pins.remove(from - PIN_OFFSET);
        pins.add(to - PIN_OFFSET, item);
        notifyItemMoved(from, to);
    }

    @Override
    public void movingDone() {
        for (int i = 0; i < pins.size(); i++) {
            Pin pin = pins.get(i);
            pin.order = i;
        }
    }

    public void updatePinViewHolder(PinViewHolder holder, Pin pin) {
        CharSequence text = pin.loadable.title;
        if (pin.archived) {
            text = TextUtils.concat(PostHelper.addIcon(PostHelper.archivedIcon, sp(14 + 2)), text);
        }

        holder.textView.setText(text);
        holder.image.setUrl(pin.thumbnailUrl, dp(40), dp(40));

        if (ChanSettings.watchEnabled.get()) {
            String count;
            if (pin.isError) {
                count = "E";
            } else {
                int c = pin.getNewPostCount();
                if (c > 999) {
                    count = "1k+";
                } else {
                    count = Integer.toString(c);
                }
            }
            holder.watchCountText.setVisibility(View.VISIBLE);
            holder.watchCountText.setText(count);

            if (!pin.watching) {
                holder.watchCountText.setTextColor(0xff898989); // TODO material colors
            } else if (pin.getNewQuoteCount() > 0) {
                holder.watchCountText.setTextColor(0xffFF4444);
            } else {
                holder.watchCountText.setTextColor(0xff33B5E5);
            }

            // The 16dp padding now belongs to the counter, for a bigger touch area
            holder.textView.setPadding(holder.textView.getPaddingLeft(), holder.textView.getPaddingTop(),
                    0, holder.textView.getPaddingBottom());
            holder.watchCountText.setPadding(dp(16), holder.watchCountText.getPaddingTop(),
                    holder.watchCountText.getPaddingRight(), holder.watchCountText.getPaddingBottom());
        } else {
            // The 16dp padding now belongs to the textview, for better ellipsize
            holder.watchCountText.setVisibility(View.GONE);
            holder.textView.setPadding(holder.textView.getPaddingLeft(), holder.textView.getPaddingTop(),
                    dp(16), holder.textView.getPaddingBottom());
        }

        boolean highlighted = callback.isHighlighted(pin);
        if (highlighted && !holder.highlighted) {
            holder.itemView.setBackgroundColor(0x22000000);
            holder.highlighted = true;
        } else if (!highlighted && holder.highlighted) {
            //noinspection deprecation
            holder.itemView.setBackgroundDrawable(AndroidUtils.getAttrDrawable(holder.itemView.getContext(), android.R.attr.selectableItemBackground));
            holder.highlighted = false;
        }
    }

    public class PinViewHolder extends RecyclerView.ViewHolder {
        private boolean highlighted;
        private ThumbnailView image;
        private TextView textView;
        private TextView watchCountText;

        public PinViewHolder(View itemView) {
            super(itemView);
            image = (ThumbnailView) itemView.findViewById(R.id.thumb);
            image.setCircular(true);
            textView = (TextView) itemView.findViewById(R.id.text);
            textView.setTypeface(ROBOTO_MEDIUM);
            watchCountText = (TextView) itemView.findViewById(R.id.watch_count);
            watchCountText.setTypeface(ROBOTO_MEDIUM);

            setRoundItemBackground(watchCountText);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onPinClicked(pins.get(getAdapterPosition() - PIN_OFFSET));
                }
            });

            watchCountText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onWatchCountClicked(pins.get(getAdapterPosition() - PIN_OFFSET));
                }
            });
        }
    }

    public class HeaderHolder extends RecyclerView.ViewHolder {
        private TextView text;
        private ImageView image;

        public HeaderHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.text);
            text.setTypeface(ROBOTO_MEDIUM);
            image = (ImageView) itemView.findViewById(R.id.image);
            setRoundItemBackground(image);
            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onHeaderClicked(HeaderHolder.this);
                }
            });
        }
    }

    public class LinkHolder extends RecyclerView.ViewHolder {
        private ImageView image;
        private TextView text;

        public LinkHolder(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
            text.setTypeface(ROBOTO_MEDIUM);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.openSettings();
                }
            });
        }
    }

    public class DividerHolder extends RecyclerView.ViewHolder {
        private boolean withBackground = false;
        private View divider;

        public DividerHolder(View itemView) {
            super(itemView);
            divider = itemView.findViewById(R.id.divider);
        }

        public void withBackground(boolean withBackground) {
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

        void onHeaderClicked(HeaderHolder holder);

        boolean isHighlighted(Pin pin);

        void openSettings();
    }
}
