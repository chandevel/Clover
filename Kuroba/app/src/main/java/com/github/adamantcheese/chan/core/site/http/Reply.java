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

import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.io.File;

import static kotlin.random.Random.Default;

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

    public File file;
    public String fileName;
    public String name;
    public String options;
    public String flag;
    public String subject;
    public String comment;
    public boolean spoilerImage;
    public String password;

    public Reply() {
        reset(false);
    }

    public void reset(boolean keepNameAndFlag) {
        file = null;
        fileName = "";
        name = keepNameAndFlag ? name : ChanSettings.postDefaultName.get();
        options = "";
        flag = keepNameAndFlag ? flag : "";
        subject = "";
        comment = "";
        spoilerImage = false;
        password = Long.toHexString(Default.nextLong());
    }
}
