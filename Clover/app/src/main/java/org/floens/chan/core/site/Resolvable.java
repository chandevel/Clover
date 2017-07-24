package org.floens.chan.core.site;


public interface Resolvable {
    enum ResolveResult {
        NO,
        NAME_MATCH,
        FULL_MATCH
    }

    ResolveResult resolve(String value);

    Class<? extends Site> getSiteClass();
}
