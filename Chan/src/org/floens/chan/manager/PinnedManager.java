package org.floens.chan.manager;

import java.util.List;

import org.floens.chan.adapter.PinnedAdapter;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Pin;

import android.content.Context;

public class PinnedManager {
	private static PinnedManager instance;
	
	private final Context context;
    private final List<Pin> pins;
    
    public PinnedManager(Context context) {
    	instance = this;
    	
    	this.context = context;
        pins = DatabaseManager.getInstance().getPinned();
    }
    
    public static PinnedManager getInstance() {
    	return instance;
    }
    
    public PinnedAdapter getAdapter() {
        PinnedAdapter adapter = new PinnedAdapter(context, 0);
        
        Pin header = new Pin();
        header.type = Pin.Type.HEADER;
        adapter.add(header);
        
        adapter.addAll(pins);
        
        return adapter;
    }
    
    /**
     * Look for a pin that has an loadable that is equal to the supplied loadable.
     * @param other
     * @return The pin whose loadable is equal to the supplied loadable, or null if no pin was found.
     */
    public Pin findPinByLoadable(Loadable other) {
        for (Pin pin : pins) {
            if (pin.loadable.equals(other)) {
                return pin;
            }
        }
        
        return null;
    }
    
    public void add(Pin pin) {
    	// No duplicates
	    for (Pin e : pins) {
	        if (e.loadable.equals(pin.loadable)) {
	            return;
	        }
	    }
	    
	    pins.add(pin);
	    DatabaseManager.getInstance().addPin(pin);
	}

	public void remove(Pin pin) {
	    pins.remove(pin);
	    DatabaseManager.getInstance().removePin(pin);
	}

	public void update(Pin pin) {
    	DatabaseManager.getInstance().updatePin(pin);
    }
}





