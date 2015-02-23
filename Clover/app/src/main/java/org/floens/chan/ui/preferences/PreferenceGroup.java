package org.floens.chan.ui.preferences;

import java.util.ArrayList;
import java.util.List;

public class PreferenceGroup {
    public final String name;

    public PreferenceGroup(String name) {
        this.name = name;
    }

    public List<PreferenceItem> preferenceItems = new ArrayList<>();
}
