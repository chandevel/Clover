package org.floens.chan.core.site;


import okhttp3.HttpUrl;

public interface Resolvable {
    boolean matchesName(String value);

    boolean respondsTo(HttpUrl url);

    Class<? extends Site> getSiteClass();
}
