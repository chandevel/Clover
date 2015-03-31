package org.floens.chan.ui.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class PinAdapter extends RecyclerView.Adapter<PinAdapter.PinViewHolder> {
    @Override
    public PinViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        TextView test = new TextView(parent.getContext());

        return new PinViewHolder(test);
    }

    @Override
    public void onBindViewHolder(PinViewHolder holder, int position) {
        ((TextView)holder.itemView).setText("Position = " + position);
    }

    @Override
    public int getItemCount() {
        return 1000;
    }

    public static class PinViewHolder extends RecyclerView.ViewHolder {
        private View itemView;

        public PinViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
        }
    }

}
