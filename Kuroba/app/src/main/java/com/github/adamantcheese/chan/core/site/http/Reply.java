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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.orm.Loadable;

import java.io.File;

/**
 * The data needed to send a reply.
 */
public class Reply {
    /**
     * Optional. {@code null} when ReCaptcha v2 was used or a 4pass
     */
    public String captchaChallenge;

    /**
     * Optional. {@code null} when a 4pass was used.
     */
    public String captchaResponse;

    public Loadable loadable;

    public File file;
    public String fileName = "";
    public String name = "";
    public String options = "";
    public String flag = "";
    public String subject = "";
    public String comment = "";
    public boolean spoilerImage = false;
    public String password = "";

    @SuppressWarnings("ConstantConditions")
    public Reply(@NonNull Loadable loadable) {
        if (loadable == null) throw new IllegalArgumentException("Loadable cannot be null");
        this.loadable = loadable;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof Reply)) return false;
        Reply other = (Reply) obj;
        // for the sake of ReplyManager, two reply objects are equal if they reference the same loadable, regardless of contents
        return other.loadable.equals(loadable);
    }

    @Override
    public int hashCode() {
        return loadable.hashCode();
    }
}
