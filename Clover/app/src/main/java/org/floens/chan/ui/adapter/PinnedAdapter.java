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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Pin;

import java.util.ArrayList;
import java.util.List;

public class PinnedAdapter extends BaseAdapter {
    private final static int VIEW_TYPE_ITEM = 0;
    private final static int VIEW_TYPE_HEADER = 1;

    private Context context;
    private List<Pin> pins = new ArrayList<>();

    public PinnedAdapter(Context context) {
        this.context = context;
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
                final Pin item = getItem(position);

                if (convertView == null) {
                    convertView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.pin_item, null);
                }

                ((TextView) convertView.findViewById(R.id.drawer_item_text)).setText(item.loadable.title);

                FrameLayout frameLayout = (FrameLayout) convertView.findViewById(R.id.drawer_item_count_container);
                if (ChanPreferences.getWatchEnabled()) {
                    frameLayout.setVisibility(View.VISIBLE);

                    TextView itemCount = (TextView) convertView.findViewById(R.id.drawer_item_count);

                    if (item.isError) {
                        itemCount.setText("Err");
                    } else {
                        int count = item.getNewPostsCount();
                        String total = Integer.toString(count);
                        if (count > 999) {
                            total = "1k+";
                        }
                        itemCount.setText(total);
                    }

                    itemCount.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            item.toggleWatch();
                        }
                    });

                    if (!item.watching) {
                        frameLayout.setBackgroundResource(R.drawable.pin_icon_gray);
                    } else if (item.getNewQuoteCount() > 0) {
                        frameLayout.setBackgroundResource(R.drawable.pin_icon_red);
                    } else {
                        frameLayout.setBackgroundResource(R.drawable.pin_icon_blue);
                    }
                } else {
                    frameLayout.setVisibility(View.GONE);
                }

                return convertView;
            }
            case VIEW_TYPE_HEADER: {
                if (convertView == null) {
                    convertView = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.pin_item_header, null);
                    ((TextView) convertView.findViewById(R.id.drawer_item_header)).setText(R.string.drawer_pinned);
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
}
