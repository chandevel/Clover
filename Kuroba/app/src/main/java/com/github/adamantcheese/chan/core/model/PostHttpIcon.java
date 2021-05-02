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
package com.github.adamantcheese.chan.core.model;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;

import okhttp3.HttpUrl;

public class PostHttpIcon {
    public final SiteEndpoints.ICON_TYPE type;
    public final HttpUrl url;
    public final PassthroughBitmapResult bitmapResult;
    public final String code;
    public final String description;

    public PostHttpIcon(
            SiteEndpoints.ICON_TYPE type,
            HttpUrl url,
            NetUtilsClasses.PassthroughBitmapResult result,
            String code,
            String description
    ) {
        this.type = type;
        this.url = url;
        this.bitmapResult = result;
        this.code = code;
        this.description = description;
    }
}
