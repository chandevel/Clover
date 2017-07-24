package org.floens.chan.core.site;

import android.util.SparseArray;

import org.floens.chan.core.site.sites.chan4.Chan4;

import java.util.ArrayList;
import java.util.List;

public class Sites {
    public static final SparseArray<Class<? extends Site>> SITE_CLASSES = new SparseArray<>();

    static {
        // This id-siteclass mapping is used to look up the correct site class at deserialization.
        // This differs from the Site.id() id, that id is used for site instance linking, this is just to
        // find the correct class to use.
        SITE_CLASSES.put(0, Chan4.class);
    }

    public static final List<Resolvable> RESOLVABLES = new ArrayList<>();

    static {
        RESOLVABLES.add(Chan4.RESOLVABLE);
    }

    public static final List<Site> ALL_SITES = new ArrayList<>();

    @Deprecated
    private static Site defaultSite;

    public static Site forId(int id) {
        // TODO: better datastructure
        for (Site site : ALL_SITES) {
            if (site.id() == id) {
                return site;
            }
        }

        return null;
    }

    @Deprecated
    public static Site defaultSite() {
        return defaultSite;
    }

    static void initialize(List<Site> sites) {
        Sites.defaultSite = sites.isEmpty() ? null : sites.get(0);
        Sites.ALL_SITES.clear();
        Sites.ALL_SITES.addAll(sites);
    }
}
