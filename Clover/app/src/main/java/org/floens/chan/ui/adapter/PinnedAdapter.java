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

import java.util.ArrayList;
import java.util.HashMap;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Pin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PinnedAdapter extends ArrayAdapter<Pin> {
    private final HashMap<Pin, Integer> idMap;
    private int idCounter;

    public PinnedAdapter(Context context, int resId) {
        super(context, resId, new ArrayList<Pin>());

        idMap = new HashMap<Pin, Integer>();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        LinearLayout view = null;

        final Pin item = getItem(position);

        if (item.type == Pin.Type.HEADER) {
            view = (LinearLayout) inflater.inflate(R.layout.pin_item_header, null);

            ((TextView) view.findViewById(R.id.drawer_item_header)).setText(R.string.drawer_pinned);
        } else {
            view = (LinearLayout) inflater.inflate(R.layout.pin_item, null);

            ((TextView) view.findViewById(R.id.drawer_item_text)).setText(item.loadable.title);

            FrameLayout frameLayout = (FrameLayout) view.findViewById(R.id.drawer_item_count_container);
            if (ChanPreferences.getWatchEnabled()) {
                frameLayout.setVisibility(View.VISIBLE);

                TextView itemCount = (TextView) view.findViewById(R.id.drawer_item_count);

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
        }

        return view;
    }

    public void reload() {
        clear();

        Pin header = new Pin();
        header.type = Pin.Type.HEADER;
        add(header);

        addAll(ChanApplication.getPinnedManager().getPins());

        notifyDataSetChanged();
    }

    @Override
    public void remove(Pin item) {
        super.remove(item);
        idMap.remove(item);
        notifyDataSetChanged();
    }

    @Override
    public void add(Pin item) {
        idMap.put(item, ++idCounter);
        super.add(item);
        notifyDataSetChanged();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= getCount())
            return -1;

        Pin item = getItem(position);
        if (item == null) {
            return -1;
        } else {
            Integer i = idMap.get(item);
            if (i == null) {
                return -1;
            } else {
                return i;
            }
        }
    }
}
