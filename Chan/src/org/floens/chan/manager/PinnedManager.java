package org.floens.chan.manager;

import java.util.ArrayList;
import java.util.Scanner;

import org.floens.chan.ChanApplication;
import org.floens.chan.adapter.PinnedAdapter;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Pin;

import android.content.Context;

public class PinnedManager {
    private final ArrayList<Pin> list = new ArrayList<Pin>();
    private final PinnedAdapter adapter;
    private final Context context;
    
    public PinnedManager(Context context) {
        this.context = context;
        
        adapter = new PinnedAdapter(context, 0, list);
        
        Pin header = new Pin();
        header.type = Pin.Type.HEADER;
        adapter.add(header);
        
        ArrayList<Pin> stored = getPinnedListFromPreferences("pinnedList");
        if (stored != null) {
            for (Pin post : stored) {
                adapter.add(post);
            }
        }
    }
    
    public PinnedAdapter getAdapter() {
        return adapter;
    }
    
    /**
     * Look for a pin that has an loadable that is equal to the supplied loadable.
     * @param other
     * @return The pin whose loadable is equal to the supplied loadable, or null if no pin was found.
     */
    public Pin findPinByLoadable(Loadable other) {
        ArrayList<Pin> pinList = getPinnedThreads();
        
        for (Pin pin : pinList) {
            if (pin.loadable.equals(other)) {
                return pin;
            }
        }
        
        return null;
    }
    
    public ArrayList<Pin> getPinnedThreads() {
        ArrayList<Pin> tempList = new ArrayList<Pin>();
        
        for (Pin pin : list) {
            if (pin.type == Pin.Type.THREAD) {
                tempList.add(pin);
            }
        }
        
        return tempList;
    }
    
    public void add(Pin pin) {
    	// No duplicates
	    for (Pin e : list) {
	        if (e.loadable.equals(pin.loadable)) {
	            return;
	        }
	    }
	    
	    adapter.add(pin);
	    
	    storePinnedListInPreferences("pinnedList", list);
	}

	public void remove(Pin pin) {
	    adapter.remove(pin);
	    
	    storePinnedListInPreferences("pinnedList", list);
	}

	public void update(Pin pin) {
    	adapter.notifyDataSetChanged();
    	
    	storePinnedListInPreferences("pinnedList", list);
    }
    
    private void storePinnedListInPreferences(String key, ArrayList<Pin> list) {
        String total = "";
        
        for (Pin post : list) {
            total += post.loadable.board + "\u1208" + post.loadable.no + "\u1208" + post.loadable.title + "\n";
        }
        
        ChanApplication.getPreferences().edit().putString(key, total).commit();
    }
    
    private ArrayList<Pin> getPinnedListFromPreferences(String key) {
        String total = ChanApplication.getPreferences().getString(key, null);
        if (total == null) return null;
        
        ArrayList<Pin> list = new ArrayList<Pin>();
        
        Scanner scanner = new Scanner(total);
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] splitted = line.split("\\u1208");
            
            if (splitted.length < 3) continue;
            
            Pin post = new Pin();
            post.loadable.board = splitted[0];
            
            try {
                post.loadable.no = Integer.parseInt(splitted[1]);
            } catch(NumberFormatException e) {
                e.printStackTrace();
                scanner.close();
                return null;
            }
            
            post.loadable.title = splitted[2];
            
            list.add(post);
        }
        
        scanner.close();
        
        return list;
    }
}





