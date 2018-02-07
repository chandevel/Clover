/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.site;


import android.support.annotation.Nullable;

import org.floens.chan.core.database.LoadableProvider;
import org.floens.chan.core.model.orm.Loadable;

import java.util.List;

import javax.inject.Inject;

import okhttp3.HttpUrl;

public class SiteResolver {
    private LoadableProvider loadableProvider;

    @Inject
    public SiteResolver(LoadableProvider loadableProvider) {
        this.loadableProvider = loadableProvider;
    }

    public Site findSiteForUrl(String url) {
        HttpUrl httpUrl = sanitizeUrl(url);

        if (httpUrl == null) {
            for (Site site : Sites.allSites()) {
                SiteUrlHandler siteUrlHandler = site.resolvable();

                if (siteUrlHandler.matchesName(url)) {
                    return site;
                }
            }

            return null;
        }

        if (!httpUrl.scheme().equals("https")) {
            httpUrl = httpUrl.newBuilder().scheme("https").build();
        }

        for (Site site : Sites.allSites()) {
            SiteUrlHandler siteUrlHandler = site.resolvable();
            if (siteUrlHandler.respondsTo(httpUrl)) {
                return site;
            }
        }

        return null;
    }

    public SiteResolverResult resolveSiteForUrl(String url) {
        List<SiteUrlHandler> siteUrlHandlers = Sites.URL_HANDLERS;

        HttpUrl httpUrl = sanitizeUrl(url);

        if (httpUrl == null) {
            for (SiteUrlHandler siteUrlHandler : siteUrlHandlers) {
                if (siteUrlHandler.matchesName(url)) {
                    return new SiteResolverResult(SiteResolverResult.Match.BUILTIN,
                            siteUrlHandler.getSiteClass(), null);
                }
            }

            return new SiteResolverResult(SiteResolverResult.Match.NONE,
                    null, null);
        }

        if (!httpUrl.scheme().equals("https")) {
            httpUrl = httpUrl.newBuilder().scheme("https").build();
        }

        for (SiteUrlHandler siteUrlHandler : siteUrlHandlers) {
            if (siteUrlHandler.respondsTo(httpUrl)) {
                return new SiteResolverResult(SiteResolverResult.Match.BUILTIN,
                        siteUrlHandler.getSiteClass(), null);
            }
        }

        return new SiteResolverResult(SiteResolverResult.Match.EXTERNAL, null, httpUrl);
    }

    public LoadableResult resolveLoadableForUrl(String url) {
        final HttpUrl httpUrl = sanitizeUrl(url);

        if (httpUrl == null) {
            return null;
        }

        for (Site site : Sites.allSites()) {
            if (site.resolvable().respondsTo(httpUrl)) {
                Loadable resolved = loadableProvider.get(
                        site.resolvable().resolveLoadable(site, httpUrl));

                if (resolved != null) {
                    return new LoadableResult(resolved);
                }
            }
        }

        return null;
    }

    @Nullable
    private HttpUrl sanitizeUrl(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);

        if (httpUrl == null) {
            httpUrl = HttpUrl.parse("https://" + url);
        }

        if (httpUrl != null) {
            if (httpUrl.host().indexOf('.') < 0) {
                httpUrl = null;
            }
        }
        return httpUrl;
    }

    public static class SiteResolverResult {
        enum Match {
            NONE,
            BUILTIN,
            EXTERNAL
        }

        Match match;
        Class<? extends Site> builtinResult;
        HttpUrl externalResult;

        public SiteResolverResult(Match match, Class<? extends Site> builtinResult, HttpUrl externalResult) {
            this.match = match;
            this.builtinResult = builtinResult;
            this.externalResult = externalResult;
        }
    }

    public static class LoadableResult {
        public final Loadable loadable;

        public LoadableResult(Loadable loadable) {
            this.loadable = loadable;
        }
    }
}
