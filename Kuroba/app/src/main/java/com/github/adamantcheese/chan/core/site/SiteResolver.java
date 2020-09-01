/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.site;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.SiteRepository;

import okhttp3.HttpUrl;

public class SiteResolver {
    private final SiteRepository siteRepository;

    public SiteResolver(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    /**
     * Can find a site by it's url (e.g. 4chan.org, 4channel.org) or it's media url (e.g. i.4cdn.org)
     */
    @Nullable
    public Site findSiteForUrl(String url) {
        HttpUrl httpUrl = sanitizeUrl(url);
        SiteRepository.Sites sites = siteRepository.all();

        if (httpUrl == null) {
            for (Site site : sites.getAll()) {
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

        for (Site site : sites.getAll()) {
            SiteUrlHandler siteUrlHandler = site.resolvable();
            if (siteUrlHandler.respondsTo(httpUrl)) {
                return site;
            }
            if (siteUrlHandler.matchesMediaHost(httpUrl)) {
                return site;
            }
        }

        return null;
    }

    public Loadable resolveLoadableForUrl(String url) {
        final HttpUrl httpUrl = sanitizeUrl(url);

        if (httpUrl == null) {
            return null;
        }

        for (Site site : siteRepository.all().getAll()) {
            if (site.resolvable().respondsTo(httpUrl)) {
                Loadable resolvedLoadable = site.resolvable().resolveLoadable(site, httpUrl);
                if (resolvedLoadable != null) {
                    return resolvedLoadable;
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
}
