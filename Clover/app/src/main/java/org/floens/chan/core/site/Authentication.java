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

public class Authentication {
    public enum Type {
        NONE,
        CAPTCHA1,
        CAPTCHA2,
        GENERIC_WEBVIEW
    }

    public static Authentication fromNone() {
        return new Authentication(Type.NONE);
    }

    public static Authentication fromCaptcha1(String siteKey, String baseUrl) {
        Authentication a = new Authentication(Type.CAPTCHA1);
        a.siteKey = siteKey;
        a.baseUrl = baseUrl;
        return a;
    }

    public static Authentication fromCaptcha2(String siteKey, String baseUrl) {
        Authentication a = new Authentication(Type.CAPTCHA2);
        a.siteKey = siteKey;
        a.baseUrl = baseUrl;
        return a;
    }

    public static Authentication fromUrl(String url, String retryText, String successText) {
        Authentication a = new Authentication(Type.GENERIC_WEBVIEW);
        a.url = url;
        a.retryText = retryText;
        a.successText = successText;
        return a;
    }

    public final Type type;

    // captcha1 & captcha2
    public String siteKey;
    public String baseUrl;

    // generic webview
    public String url;
    public String retryText;
    public String successText;

    private Authentication(Type type) {
        this.type = type;
    }
}
