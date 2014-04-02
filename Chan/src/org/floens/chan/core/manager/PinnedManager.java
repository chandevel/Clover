package org.floens.chan.core.manager;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;

import android.content.Context;

public class PinnedManager {
    private final List<PinListener> listeners = new ArrayList<PinListener>();
    private final List<Pin> pins;

    public PinnedManager(Context context) {
        pins = ChanApplication.getDatabaseManager().getPinned();
    }

    public void addPinListener(PinListener l) {
        listeners.add(l);
    }

    public void removePinListener(PinListener l) {
        listeners.remove(l);
    }

    /**
     * Look for a pin that has an loadable that is equal to the supplied
     * loadable.
     *
     * @param other
     * @return The pin whose loadable is equal to the supplied loadable, or null
     *         if no pin was found.
     */
    public Pin findPinByLoadable(Loadable other) {
        for (Pin pin : pins) {
            if (pin.loadable.equals(other)) {
                return pin;
            }
        }

        return null;
    }

    public List<Pin> getPins() {
        return pins;
    }

    public List<Pin> getWatchingPins() {
        List<Pin> l = new ArrayList<Pin>();

        for (Pin p : pins) {
            if (p.watching)
                l.add(p);
        }

        return l;
    }

    /**
     * Add a pin
     *
     * @param pin
     * @return true if it was added, false if it wasn't (duplicated)
     */
    public boolean add(Pin pin) {
        // No duplicates
        for (Pin e : pins) {
            if (e.loadable.equals(pin.loadable)) {
                return false;
            }
        }

        pins.add(pin);
        ChanApplication.getDatabaseManager().addPin(pin);

        onPinsChanged();

        return true;
    }

    /**
     * Remove a pin
     *
     * @param pin
     */
    public void remove(Pin pin) {
        pins.remove(pin);
        ChanApplication.getDatabaseManager().removePin(pin);
        pin.destroyWatcher();

        onPinsChanged();
    }

    /**
     * Update the pin in the database
     *
     * @param pin
     */
    public void update(Pin pin) {
        ChanApplication.getDatabaseManager().updatePin(pin);

        onPinsChanged();
    }

    /**
     * Updates all the pins to the database. This will run in a new thread
     * because it can be an expensive operation. (this will be an huge headache
     * later on when we get concurrent problems)
     */
    public void updateAll() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ChanApplication.getDatabaseManager().updatePins(pins);
            }
        }).start();
    }

    public void onPinViewed(Pin pin) {
        if (pin.getPinWatcher() != null) {
            pin.getPinWatcher().onViewed();
        }

        onPinsChanged();
    }

    public void onPinsChanged() {
        for (PinListener l : listeners) {
            l.onPinsChanged();
        }
    }

    public static interface PinListener {
        public void onPinsChanged();
    }
}
