package org.floens.chan.ui.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.cell.PinCell;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class PinAdapter extends RecyclerView.Adapter<PinAdapter.PinViewHolder> implements SwipeListener.Callback {
    private final Callback callback;
    private List<Pin> pins = new ArrayList<>();

    public PinAdapter(Callback callback) {
        this.callback = callback;
    }

    @Override
    public PinViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        PinCell pinCell = (PinCell) LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_pin, parent, false);
        return new PinViewHolder(pinCell);
    }

    @Override
    public void onBindViewHolder(PinViewHolder holder, int position) {
        final Pin pin = pins.get(position);

        holder.textView.setText(pin.loadable.title);
        holder.image.setUrl(pin.thumbnailUrl, dp(40), dp(40));
    }

    @Override
    public int getItemCount() {
        return pins.size();
    }

    public void onPinsChanged(List<Pin> pins) {
        this.pins.clear();
        this.pins.addAll(pins);
        notifyDataSetChanged();
    }

    public void onPinAdded(Pin pin) {
        pins.add(pin);
        notifyItemInserted(pins.size() - 1);
    }

    public void onPinRemoved(Pin pin) {
        // TODO: this is a workaround for recyclerview crashing when the last item is removed, remove this when it is fixed
        if (pins.size() == 1) {
            pins.remove(pin);
            notifyDataSetChanged();
        } else {
            int location = pins.indexOf(pin);
            pins.remove(pin);
            notifyItemRemoved(location);
        }
    }

    public void onPinChanged(Pin pin) {
        notifyItemChanged(pins.indexOf(pin));
    }

    @Override
    public SwipeListener.Swipeable getSwipeable(int position) {
        return SwipeListener.Swipeable.RIGHT;
    }

    @Override
    public void removeItem(int position) {
        ChanApplication.getWatchManager().removePin(pins.get(position));
    }

    @Override
    public boolean isMoveable(int position) {
        return true;
    }

    @Override
    public void moveItem(int from, int to) {
        Pin item = pins.remove(from);
        pins.add(to, item);
        notifyItemMoved(from, to);
    }

    @Override
    public void movingDone() {
    }

    public class PinViewHolder extends RecyclerView.ViewHolder {
        private PinCell pinCell;
        private ThumbnailView image;
        private TextView textView;

        public PinViewHolder(PinCell pinCell) {
            super(pinCell);
            this.pinCell = pinCell;
            image = (ThumbnailView) pinCell.findViewById(R.id.thumb);
            image.setCircular(true);
            textView = (TextView) pinCell.findViewById(R.id.text);

            pinCell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onPinClicked(pins.get(getAdapterPosition()));
                }
            });
        }
    }

    public interface Callback {
        void onPinClicked(Pin pin);
    }
}
