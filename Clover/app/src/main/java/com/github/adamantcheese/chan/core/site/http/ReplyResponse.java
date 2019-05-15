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
package com.github.adamantcheese.chan.core.site.http;

import com.github.adamantcheese.chan.core.site.SiteActions;

/**
 * Generic response for
 * {@link com.github.adamantcheese.chan.core.site.SiteActions#post(Reply, SiteActions.PostListener)} that the
 * reply layout uses.
 */
public class ReplyResponse {
    /**
     * {@code true} if the post when through, {@code false} otherwise.
     */
    public boolean posted;

    /**
     * Error message used to show to the user if {@link #posted} is {@code false}.
     * <p>Optional
     */
    public String errorMessage;

    // TODO(multi-site)
    public int threadNo;
    public int postNo;
    public String password;
    public boolean probablyBanned;
    public boolean requireAuthentication;
}
