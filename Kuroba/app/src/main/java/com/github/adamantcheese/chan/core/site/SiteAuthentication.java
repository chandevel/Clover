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

import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.CAPTCHA1;
import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.CAPTCHA2;
import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.CAPTCHA2_NOJS;
import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.CUSTOM_JSON;
import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.GENERIC_WEBVIEW;
import static com.github.adamantcheese.chan.core.site.SiteAuthentication.Type.NONE;

@SuppressWarnings("ALL")
public class SiteAuthentication {
    public enum Type {
        NONE,
        CAPTCHA1,
        CAPTCHA2,
        CAPTCHA2_NOJS,
        CUSTOM_JSON,
        GENERIC_WEBVIEW
    }

    public static SiteAuthentication fromNone() {
        return new SiteAuthentication(NONE);
    }

    public static SiteAuthentication fromCaptcha1(String siteKey, String baseUrl) {
        SiteAuthentication a = new SiteAuthentication(CAPTCHA1);
        a.siteKey = siteKey;
        a.baseUrl = baseUrl;
        return a;
    }

    public static SiteAuthentication fromCaptcha2(String siteKey, String baseUrl) {
        SiteAuthentication a = new SiteAuthentication(CAPTCHA2);
        a.siteKey = siteKey;
        a.baseUrl = baseUrl;
        return a;
    }

    public static SiteAuthentication fromCaptcha2nojs(String siteKey, String baseUrl) {
        SiteAuthentication a = new SiteAuthentication(CAPTCHA2_NOJS);
        a.siteKey = siteKey;
        a.baseUrl = baseUrl;
        return a;
    }

    public static SiteAuthentication fromCustomJson(String baseUrl) {
        SiteAuthentication a = new SiteAuthentication(CUSTOM_JSON);
        a.baseUrl = baseUrl;
        return a;
    }

    public static SiteAuthentication fromUrl(String url, String retryText, String successText) {
        SiteAuthentication a = new SiteAuthentication(GENERIC_WEBVIEW);
        a.baseUrl = url;
        a.retryText = retryText;
        a.successText = successText;
        return a;
    }

    // All
    public final Type type;
    public String baseUrl;

    // captcha1 & captcha2
    public String siteKey;

    // generic webview
    public String retryText;
    public String successText;

    private SiteAuthentication(Type type) {
        this.type = type;
    }
}
