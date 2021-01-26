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

import com.github.adamantcheese.chan.core.site.sites.Kun8;
import com.github.adamantcheese.chan.core.site.sites.Lainchan;
import com.github.adamantcheese.chan.core.site.sites.Sushichan;
import com.github.adamantcheese.chan.core.site.sites.Wired7;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.core.site.sites.chan420.Chan420;
import com.github.adamantcheese.chan.core.site.sites.dvach.Dvach;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of all sites and url handler we have.
 */
public class SiteRegistry {
    public static final List<SiteUrlHandler> URL_HANDLERS = new ArrayList<>();
    public static final BiMap<Integer, Class<? extends Site>> SITE_CLASSES = HashBiMap.create();

    static {
        URL_HANDLERS.add(Chan4.URL_HANDLER);
        //8chan was here but was removed
        URL_HANDLERS.add(Lainchan.URL_HANDLER);
        //arisuchan was here but was removed
        URL_HANDLERS.add(Sushichan.URL_HANDLER);
        URL_HANDLERS.add(Dvach.URL_HANDLER);
        URL_HANDLERS.add(Wired7.URL_HANDLER);
        //chan55 was here but was removed
        URL_HANDLERS.add(Kun8.URL_HANDLER);
        URL_HANDLERS.add(Chan420.URL_HANDLER);
    }

    static {
        // This id-siteclass mapping is used to look up the correct site class at deserialization.
        // This differs from the Site.id() id, that id is used for site instance linking, this is just to
        // find the correct class to use.
        SITE_CLASSES.put(0, Chan4.class);

        //8chan was here but was removed; don't use ID 1
        SITE_CLASSES.put(2, Lainchan.class);
        //arisuchan was here but was removed; don't use ID 3
        SITE_CLASSES.put(4, Sushichan.class);
        SITE_CLASSES.put(5, Dvach.class);
        SITE_CLASSES.put(6, Wired7.class);
        //chan55 was here but was removed; don't use ID 7
        SITE_CLASSES.put(8, Kun8.class);
        SITE_CLASSES.put(9, Chan420.class);
    }
}
