package org.floens.chan.ui.adapter;

import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrDrawable;

public class PinAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SwipeListener.Callback {
    private static final int PIN_OFFSET = 3;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PIN = 1;
    private static final int TYPE_LINK = 2;

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
                return new PinHeaderHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_header, parent, false));
            case TYPE_PIN:
                return new PinViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_pin, parent, false));
            case TYPE_LINK:
                return new LinkHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_link, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case TYPE_HEADER:
                ((PinHeaderHolder) holder).text.setText(R.string.drawer_pinned);
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
                        linkHolder.text.setText(R.string.drawer_board);
                        linkHolder.image.setImageResource(R.drawable.ic_view_list_24dp);
                        break;
                    case 1:
                        linkHolder.text.setText(R.string.drawer_catalog);
                        linkHolder.image.setImageResource(R.drawable.ic_view_module_24dp);
                        break;
                }
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
        notifyDataSetChanged();
    }

    public void onPinAdded(Pin pin) {
        pins.add(pin);
        notifyItemInserted(pins.size() - 1 + PIN_OFFSET);
    }

    public void onPinRemoved(Pin pin) {
        // TODO: this is a workaround for recyclerview crashing when the last item is removed, remove this when it is fixed
        if (pins.size() == 1) {
            pins.remove(pin);
            notifyDataSetChanged();
        } else {
            int location = pins.indexOf(pin);
            pins.remove(pin);
            notifyItemRemoved(location + PIN_OFFSET);
        }
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
        ChanApplication.getWatchManager().removePin(pins.get(position - PIN_OFFSET));
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
    }

    public void updatePinViewHolder(PinViewHolder holder, Pin pin) {
        holder.textView.setText(pin.loadable.title);
        holder.image.setUrl(pin.thumbnailUrl, dp(40), dp(40));

        if (ChanSettings.watchEnabled.get()) {
            String count;
            if (pin.isError) {
                count = "Err";
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                watchCountText.setBackground(getAttrDrawable(itemView.getContext(), android.R.attr.selectableItemBackgroundBorderless));
            } else {
                watchCountText.setBackgroundResource(R.drawable.gray_background_selector);
            }

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

    public class PinHeaderHolder extends RecyclerView.ViewHolder {
        private TextView text;

        public PinHeaderHolder(View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.text);
            text.setTypeface(ROBOTO_MEDIUM);
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

                }
            });
        }
    }

    public interface Callback {
        void onPinClicked(Pin pin);

        void onWatchCountClicked(Pin pin);

        boolean isHighlighted(Pin pin);
    }
}
