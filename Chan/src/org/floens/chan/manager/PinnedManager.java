package org.floens.chan.manager;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.database.DatabaseManager;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Pin;

import android.content.Context;

public class PinnedManager {
    private static PinnedManager instance;

    private final List<PinListener> listeners = new ArrayList<PinListener>();
    private final List<Pin> pins;

    public PinnedManager(Context context) {
        instance = this;
        pins = DatabaseManager.getInstance().getPinned();
    }

    public static PinnedManager getInstance() {
        return instance;
    }

    public void addPinListener(PinListener l) {
        listeners.add(l);
    }

    public void removePinListener(PinListener l) {
        listeners.remove(l);
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

    public List<Pin> getPins() {
        return pins;
    }

    /**
     * Add a pin
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
        DatabaseManager.getInstance().addPin(pin);

        onPinsChanged();

        return true;
    }

    /**
     * Remove a pin
     * @param pin
     */
    public void remove(Pin pin) {
        pins.remove(pin);
        DatabaseManager.getInstance().removePin(pin);
        pin.destroy();

        onPinsChanged();
    }

    /**
     * Update the pin in the database
     * @param pin
     */
    public void update(Pin pin) {
        DatabaseManager.getInstance().updatePin(pin);

        onPinsChanged();
    }

    /**
     * Updates all the pins to the database.
     * This will run in a new thread because it can be an expensive operation.
     * (this will be an huge headache later on when we get concurrent problems)
     */
    public void updateAll() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatabaseManager.getInstance().updatePins(pins);
            }
        }).start();
    }

    public void onPinViewed(Pin pin) {
        pin.onViewed();

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





