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


import java.util.List;

import okhttp3.HttpUrl;

class SiteResolver {
    SiteResolverResult resolve(String url) {
        List<Resolvable> resolvables = Sites.RESOLVABLES;

        HttpUrl httpUrl = HttpUrl.parse(url);

        if (httpUrl == null) {
            httpUrl = HttpUrl.parse("https://" + url);
        }

        if (httpUrl != null) {
            if (httpUrl.host().indexOf('.') < 0) {
                httpUrl = null;
            }
        }

        if (httpUrl == null) {
            for (Resolvable resolvable : resolvables) {
                if (resolvable.resolve(url) == Resolvable.ResolveResult.NAME_MATCH) {
                    return new SiteResolverResult(SiteResolverResult.Match.BUILTIN, resolvable.getSiteClass(), null);
                }
            }

            return new SiteResolverResult(SiteResolverResult.Match.NONE, null, null);
        }

        if (!httpUrl.scheme().equals("https")) {
            httpUrl = httpUrl.newBuilder().scheme("https").build();
        }

        for (Resolvable resolvable : resolvables) {
            if (resolvable.resolve(httpUrl.toString()) == Resolvable.ResolveResult.FULL_MATCH) {
                return new SiteResolverResult(SiteResolverResult.Match.BUILTIN, resolvable.getSiteClass(), null);
            }
        }

        return new SiteResolverResult(SiteResolverResult.Match.EXTERNAL, null, httpUrl);
    }

    static class SiteResolverResult {
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
}
