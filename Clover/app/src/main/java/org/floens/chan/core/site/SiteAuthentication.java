package org.floens.chan.core.site;


public interface SiteAuthentication {
    enum AuthenticationRequestType {
        POSTING
    }

    boolean requireAuthentication(AuthenticationRequestType type);
}
