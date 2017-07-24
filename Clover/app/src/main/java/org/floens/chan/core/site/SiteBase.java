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


import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.model.json.site.SiteUserSettings;

public abstract class SiteBase implements Site {
    protected int id;
    protected SiteConfig config;
    protected SiteUserSettings userSettings;

    @Override
    public void initialize(int id, SiteConfig config, SiteUserSettings userSettings) {
        this.id = id;
        this.config = config;
        this.userSettings = userSettings;
    }

    @Override
    public int id() {
        return id;
    }
}
