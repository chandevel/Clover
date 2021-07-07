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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.orm.Loadable;

import okhttp3.HttpUrl;

public interface SiteUrlHandler {
    boolean matchesName(String value);

    boolean respondsTo(HttpUrl url);

    boolean matchesMediaHost(@NonNull HttpUrl url);

    String desktopUrl(Loadable loadable, int postNo);

    Loadable resolveLoadable(Site site, HttpUrl url);

    static boolean containsMediaHostUrl(HttpUrl desiredSiteUrl, String[] mediaHosts) {
        String host = desiredSiteUrl.host();

        for (String mediaHost : mediaHosts) {
            if (host.equals(mediaHost)) {
                return true;
            }

            if (host.equals("www." + mediaHost)) {
                return true;
            }
        }

        return false;
    }
}
