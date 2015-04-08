package org.floens.chan.ui.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;
import static org.floens.chan.utils.AndroidUtils.dp;

public class PinAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SwipeListener.Callback {
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
                final Pin pin = pins.get(position - 3);
                PinViewHolder pinHolder = (PinViewHolder) holder;
                pinHolder.textView.setText(pin.loadable.title);
                pinHolder.image.setUrl(pin.thumbnailUrl, dp(40), dp(40));
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
        return pins.size() + 3;
    }

    @Override
    public long getItemId(int position) {
        position -= 3;
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
        notifyItemInserted(pins.size() - 1 + 3);
    }

    public void onPinRemoved(Pin pin) {
        // TODO: this is a workaround for recyclerview crashing when the last item is removed, remove this when it is fixed
        if (pins.size() == 1) {
            pins.remove(pin);
            notifyDataSetChanged();
        } else {
            int location = pins.indexOf(pin);
            pins.remove(pin);
            notifyItemRemoved(location + 3);
        }
    }

    public void onPinChanged(Pin pin) {
        notifyItemChanged(pins.indexOf(pin) + 3);
    }

    @Override
    public SwipeListener.Swipeable getSwipeable(int position) {
        return getItemViewType(position) == TYPE_PIN ? SwipeListener.Swipeable.RIGHT : SwipeListener.Swipeable.NO;
    }

    @Override
    public void removeItem(int position) {
        ChanApplication.getWatchManager().removePin(pins.get(position - 3));
    }

    @Override
    public boolean isMoveable(int position) {
        return getItemViewType(position) == TYPE_PIN;
    }

    @Override
    public void moveItem(int from, int to) {
        Pin item = pins.remove(from - 3);
        pins.add(to - 3, item);
        notifyItemMoved(from, to);
    }

    @Override
    public void movingDone() {
    }

    public class PinViewHolder extends RecyclerView.ViewHolder {
        private ThumbnailView image;
        private TextView textView;

        public PinViewHolder(View pinCell) {
            super(pinCell);
            image = (ThumbnailView) pinCell.findViewById(R.id.thumb);
            image.setCircular(true);
            textView = (TextView) pinCell.findViewById(R.id.text);
            textView.setTypeface(ROBOTO_MEDIUM);

            pinCell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onPinClicked(pins.get(getAdapterPosition() - 3));
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
    }
}
