package org.floens.chan.core.site;

import org.floens.chan.core.site.sites.chan4.Chan4;

import java.util.Arrays;
import java.util.List;

public class Sites {
    public static final Chan4 CHAN4 = new Chan4();

    public static final List<? extends Site> ALL_SITES = Arrays.asList(
            CHAN4
    );

    private static final Site[] BY_ID;

    static {
        int highestId = 0;
        for (Site site : ALL_SITES) {
            if (site.id() > highestId) {
                highestId = site.id();
            }
        }
        BY_ID = new Site[highestId + 1];
        for (Site site : ALL_SITES) {
            BY_ID[site.id()] = site;
        }
    }

    public static Site forId(int id) {
        return BY_ID[id];
    }

    public static Site defaultSite() {
        return CHAN4;
    }
}
