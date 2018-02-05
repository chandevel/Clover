package org.floens.chan.core.site;

import android.util.SparseArray;

import org.floens.chan.core.site.sites.chan4.Chan4;
import org.floens.chan.core.site.sites.vichan.ViChan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sites {
    public static final SparseArray<Class<? extends Site>> SITE_CLASSES = new SparseArray<>();

    static {
        // This id-siteclass mapping is used to look up the correct site class at deserialization.
        // This differs from the Site.id() id, that id is used for site instance linking, this is just to
        // find the correct class to use.
        SITE_CLASSES.put(0, Chan4.class);

        SITE_CLASSES.put(1, ViChan.class);
    }

    public static final List<Resolvable> RESOLVABLES = new ArrayList<>();

    static {
        RESOLVABLES.add(Chan4.RESOLVABLE);
        RESOLVABLES.add(ViChan.RESOLVABLE);
    }

    private static List<Site> ALL_SITES = Collections.unmodifiableList(new ArrayList<Site>());

    /**
     * Return all sites known in the system.
     * <p>This list is immutable. Changes to the known sites cause this function to return a new immutable list
     * with the site changes.
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
