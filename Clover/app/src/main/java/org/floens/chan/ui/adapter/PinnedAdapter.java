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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.view.CustomNetworkImageView;

import java.util.ArrayList;
import java.util.List;

public class PinnedAdapter extends BaseAdapter {
    private final static int VIEW_TYPE_ITEM = 0;
    private final static int VIEW_TYPE_HEADER = 1;

    private Context context;
    private ListView listView;
    private List<Pin> pins = new ArrayList<>();
    private boolean postInvalidated = false;

    public PinnedAdapter(Context context, ListView listView) {
        this.context = context;
        this.listView = listView;
    }

    @Override
    public int getCount() {
        return pins.size() + 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(final int position) {
        return position == 0 ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @Override
    public Pin getItem(final int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_ITEM:
                int itemPosition = position - 1;
                if (itemPosition >= 0 && itemPosition < pins.size()) {
                    return pins.get(itemPosition);
                } else {
                    return null;
                }
            case VIEW_TYPE_HEADER:
                return null;
            default:
                return null;
        }
    }

    @Override
    public long getItemId(int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_ITEM:
                int itemPosition = position - 1;
                if (itemPosition >= 0 && itemPosition < pins.size()) {
                    return pins.get(itemPosition).id;
                } else {
                    return -1;
                }
            case VIEW_TYPE_HEADER:
                return -1;
            default:
                return -1;
        }
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_ITEM: {
                final Pin pin = getItem(position);

                if (convertView == null) {
                    convertView = LayoutInflater.from(context).inflate(R.layout.pin_item, null);
                }

                CustomNetworkImageView imageView = (CustomNetworkImageView) convertView.findViewById(R.id.pin_image);
                if (pin.thumbnailUrl != null) {
                    imageView.setVisibility(View.VISIBLE);
                    imageView.setFadeIn(0);
                    imageView.setImageUrl(pin.thumbnailUrl, ChanApplication.getVolleyImageLoader());
                } else {
                    imageView.setVisibility(View.GONE);
                }

                ((TextView) convertView.findViewById(R.id.pin_text)).setText(pin.loadable.title);

                FrameLayout timeContainer = (FrameLayout) convertView.findViewById(R.id.pin_time_container);
                FrameLayout countContainer = (FrameLayout) convertView.findViewById(R.id.pin_count_container);
                if (ChanPreferences.getWatchEnabled()) {
                    countContainer.setVisibility(View.VISIBLE);

                    TextView timeView = (TextView) convertView.findViewById(R.id.pin_time);
                    if (pin.watching && pin.getPinWatcher() != null && ChanPreferences.getWatchCountdownVisibleEnabled()) {
                        timeContainer.setVisibility(View.VISIBLE);
                        long timeRaw = pin.getPinWatcher().getTimeUntilNextLoad();
                        long time = 0;
                        if (timeRaw > 0) {
                            time = timeRaw / 1000L;
                            time = Math.min(9999, time);
                        }

                        timeView.setText(Long.toString(time));

                        postInvalidate();
                    } else {
                        timeContainer.setVisibility(View.GONE);
                    }

                    TextView countView = (TextView) convertView.findViewById(R.id.pin_count);
                    ProgressBar loadView = (ProgressBar) convertView.findViewById(R.id.pin_load);

                    if (pin.isError) {
                        countView.setText("Err");
                    } else {
                        int count = pin.getNewPostCount();
                        String total = Integer.toString(count);
                        if (count > 999) {
                            total = "1k+";
                        }
                        countView.setText(total);
                    }

                    if (pin.getPinWatcher() != null && pin.getPinWatcher().isLoading()) {
                        countView.setVisibility(View.GONE);
                        loadView.setVisibility(View.VISIBLE);
                    } else {
                        loadView.setVisibility(View.GONE);
                        countView.setVisibility(View.VISIBLE);
                    }

                    countContainer.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pin.toggleWatch();
                        }
                    });

                    if (!pin.watching) {
                        countContainer.setBackgroundResource(R.drawable.pin_icon_gray);
                    } else if (pin.getNewQuoteCount() > 0) {
                        countContainer.setBackgroundResource(R.drawable.pin_icon_red);
                    } else {
                        countContainer.setBackgroundResource(R.drawable.pin_icon_blue);
                    }
                } else {
                    timeContainer.setVisibility(View.GONE);
                    countContainer.setVisibility(View.GONE);
                }

                return convertView;
            }
            case VIEW_TYPE_HEADER: {
                if (convertView == null) {
                    convertView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.pin_item_header, null);
                    ((TextView) convertView.findViewById(R.id.pin_header)).setText(R.string.drawer_pinned);
                }

                return convertView;
            }
            default:
                return null;
        }
    }

    public void reload() {
        pins.clear();
        pins.addAll(ChanApplication.getWatchManager().getPins());

        notifyDataSetChanged();
    }

    private void postInvalidate() {
        if (!postInvalidated) {
            postInvalidated = true;
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    postInvalidated = false;
                    listView.invalidateViews();
                }
            }, 1000);
        }
    }
}
