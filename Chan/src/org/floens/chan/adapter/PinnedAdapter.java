package org.floens.chan.adapter;

import java.util.HashMap;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.model.Pin;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PinnedAdapter extends ArrayAdapter<Pin> {
    private final HashMap<Pin, Integer> idMap = new HashMap<Pin, Integer>();
    private int idCounter;
    private View.OnTouchListener listener;
    
    public PinnedAdapter(Context context, int resId, List<Pin> list) {
        super(context, resId, list);
        
        int i = 0;
        for (i = 0; i < list.size(); ++i) {
            idMap.put(list.get(i), i);
        }
        
        idCounter = i + 1;
    }
    
    public void setTouchListener(View.OnTouchListener listener) {
        this.listener = listener;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        LinearLayout view = null;
        
        Pin item = getItem(position);
        if (item.type == Pin.Type.HEADER) {
            view = (LinearLayout) inflater.inflate(R.layout.drawer_item_header, null);
            
            ((TextView) view.findViewById(R.id.drawer_item_header)).setText(R.string.drawer_pinned);
        } else {
            view = (LinearLayout) inflater.inflate(R.layout.drawer_item, null);
            
            ((TextView) view.findViewById(R.id.drawer_item_text)).setText(item.loadable.title);
        }
        
        if (listener != null) {
            view.setOnTouchListener(listener);
        }
        
        return view;
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
        if (position < 0 || position >= getCount()) return -1;
        Pin item = getItem(position);
        return idMap.get(item);
    }
}





