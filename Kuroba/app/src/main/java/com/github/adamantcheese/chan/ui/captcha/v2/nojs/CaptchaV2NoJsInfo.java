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
package com.github.adamantcheese.chan.ui.captcha.v2.nojs;

import static com.github.adamantcheese.chan.ui.captcha.v2.nojs.CaptchaV2NoJsInfo.CaptchaType.UNKNOWN;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public class CaptchaV2NoJsInfo {
    CaptchaType captchaType = UNKNOWN;
    @Nullable
    String cParameter = null;
    @NonNull
    List<Bitmap> challengeImages = Collections.emptyList();
    @Nullable
    CharSequence captchaTitle = null;

    public CaptchaV2NoJsInfo() { }

    public boolean isValid() {
        if (captchaType == UNKNOWN) return false;
        if (cParameter == null) return false;
        return !challengeImages.isEmpty();
    }

    @NonNull
    @Override
    public String toString() {
        return "CaptchaV2NoJsInfo{" + "captchaType=" + captchaType + ", cParameter='" + cParameter + '\''
                + ", challengeImages=" + challengeImages + ", captchaTitle=" + captchaTitle + '}';
    }

    public enum CaptchaType {
        UNKNOWN(0, 0), // ?x?
        CANONICAL(3, 3), // 3x3
        NO_CANONICAL(2, 4); // 2x4

        int columnCount;
        int rowCount;

        CaptchaType(int columnCount, int rowCount) {
            this.columnCount = columnCount;
            this.rowCount = rowCount;
        }

        public static CaptchaType fromCheckboxesCount(int count) {
            switch (count) {
                case 8:
                    return NO_CANONICAL;
                case 9:
                    return CANONICAL;
                default:
                    return UNKNOWN;
            }
        }
    }
}
