package org.floens.chan.core.site;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sites {
    private static List<Site> ALL_SITES = Collections.unmodifiableList(new ArrayList<Site>());

    /**
     * Return all sites known in the system.
     * <p>This list is immutable. Changes to the known sites cause this function to return a new immutable list
     * with the site changes.
     *
     * @return list of sites known in the system.
     */
    public static List<Site> allSites() {
        return ALL_SITES;
    }

    public static Site forId(int id) {
        // TODO: better datastructure
        for (Site site : ALL_SITES) {
            if (site.id() == id) {
                return site;
            }
        }

        return null;
    }

    static void initialize(List<Site> sites) {
        Sites.ALL_SITES = Collections.unmodifiableList(new ArrayList<>(sites));
    }
}
