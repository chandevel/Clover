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
package com.github.adamantcheese.chan.ui.captcha.v2;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CaptchaInfo {
    CaptchaType captchaType;
    @NonNull
    List<Integer> checkboxes;
    @Nullable
    String cParameter;
    @NonNull
    List<Bitmap> challengeImages;
    @Nullable
    CaptchaTitle captchaTitle;

    public CaptchaInfo() {
        captchaType =  CaptchaType.Unknown;
        checkboxes = new ArrayList<>();
        cParameter = null;
        challengeImages = null;
        captchaTitle = null;
    }

    public void setCaptchaType(CaptchaType captchaType) {
        this.captchaType = captchaType;
    }

    public void setCheckboxes(@NonNull List<Integer> checkboxes) {
        this.checkboxes = checkboxes;
    }

    public void setcParameter(@Nullable String cParameter) {
        this.cParameter = cParameter;
    }

    public void setChallengeImages(@NonNull List<Bitmap> challengeImages) {
        this.challengeImages = challengeImages;
    }

    public void setCaptchaTitle(@Nullable CaptchaTitle captchaTitle) {
        this.captchaTitle = captchaTitle;
    }

    public CaptchaType getCaptchaType() {
        return captchaType;
    }

    @NonNull
    public List<Bitmap> getChallengeImages() {
        return challengeImages;
    }

    @NonNull
    public List<Integer> getCheckboxes() {
        return checkboxes;
    }

    @Nullable
    public String getcParameter() {
        return cParameter;
    }

    @Nullable
    public CaptchaTitle getCaptchaTitle() {
        return captchaTitle;
    }

    public enum CaptchaType {
        Unknown,
        // 3x3
        Canonical,
        // 2x4
        NoCanonical;

        public static CaptchaType fromCheckboxesCount(int count) {
            if (count == 8) {
                return NoCanonical;
            } else if (count == 9) {
                return Canonical;
            }

            return Unknown;
        }
    }

    public static class CaptchaTitle {
        private String title;
        private int boldStart;
        private int boldEnd;

        public CaptchaTitle(String title, int boldStart, int boldEnd) {
            this.title = title;
            this.boldStart = boldStart;
            this.boldEnd = boldEnd;
        }

        public boolean isEmpty() {
            return title.isEmpty() && boldStart == -1 && boldEnd == -1;
        }

        public boolean hasBold() {
            return boldStart != -1 && boldEnd != -1;
        }

        public String getTitle() {
            return title;
        }

        public int getBoldStart() {
            return boldStart;
        }

        public int getBoldEnd() {
            return boldEnd;
        }
    }
}
