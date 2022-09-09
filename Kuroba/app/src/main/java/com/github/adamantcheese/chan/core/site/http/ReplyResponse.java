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
package com.github.adamantcheese.chan.core.site.http;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.SiteApi;

/**
 * Generic response for
 * {@link SiteApi#post(Loadable, SiteApi.PostListener)} that the
 * reply presenter uses.
 */
public class ReplyResponse {
    public Loadable originatingLoadable;

    /**
     * {@code true} if the post when through, {@code false} otherwise.
     */
    public boolean posted = false;

    /**
     * Error message used to show to the user if {@link #posted} is {@code false}.
     * <p>Optional
     */
    public String errorMessage;

    public int threadNo = 0;
    public int postNo = 0;
    public boolean requireAuthentication = false;

    public ReplyResponse(Loadable originatingLoadable) {
        this.originatingLoadable = originatingLoadable;
    }
}
