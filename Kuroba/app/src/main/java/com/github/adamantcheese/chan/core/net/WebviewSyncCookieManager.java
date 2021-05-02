package com.github.adamantcheese.chan.core.net;

import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class WebviewSyncCookieManager
        implements CookieJar {
    private final CookieJar actualCookieJar;
    private final CookieManager webviewCookieManager;

    public WebviewSyncCookieManager(@NonNull CookieJar actualCookieJar) {
        this.actualCookieJar = actualCookieJar;
        this.webviewCookieManager = CookieManager.getInstance();
    }

    /**
     * Saves cookies from both the request, and also sends them to webkit's cookie manager, overwriting any existing cookies.
     */
    @Override
    public void saveFromResponse(@NonNull HttpUrl url, List<Cookie> cookies) {
        String sUrl = url.toString();
        for (Cookie cookie : cookies) {
            webviewCookieManager.setCookie(sUrl, cookie.toString());
        }
        actualCookieJar.saveFromResponse(url, cookies);
    }

    /**
     * Returns a list of cookies from both the webkit cookie manager and the okhttp cookie jar.
     * Note that webview cookies come after okhttp cookies; okhttp cookies overrule webview cookies.
     */
    @NonNull
    @Override
    public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
        String urlString = url.toString();
        String cookiesString = webviewCookieManager.getCookie(urlString);

        // Set to keep track of cookies; these are filtered on name alone
        Set<CustomHashCookie> toReturn = new HashSet<>();
        for (Cookie existing : actualCookieJar.loadForRequest(url)) {
            toReturn.add(new CustomHashCookie(existing));
        }

        if (cookiesString != null) {
            //We can split on the ';' char as the cookie manager only returns cookies
            //that match the url and haven't expired, so the cookie attributes aren't included
            String[] cookieHeaders = cookiesString.split(";");

            for (String header : cookieHeaders) {
                Cookie parsed = Cookie.parse(url, header.trim());
                if (parsed != null) {
                    toReturn.add(new CustomHashCookie(parsed));
                }
            }
        }

        List<Cookie> ret = new ArrayList<>();
        for (CustomHashCookie c : toReturn) {
            ret.add(c.cookie);
        }

        actualCookieJar.saveFromResponse(url, ret);

        return ret;
    }

    private static class CustomHashCookie {
        public Cookie cookie;

        public CustomHashCookie(Cookie c) {
            this.cookie = c;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomHashCookie that = (CustomHashCookie) o;
            return cookie.name().equals(that.cookie.name());
        }

        @Override
        public int hashCode() {
            return cookie.name().hashCode();
        }
    }
}
