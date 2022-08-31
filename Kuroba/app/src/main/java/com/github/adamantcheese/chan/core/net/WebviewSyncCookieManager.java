package com.github.adamantcheese.chan.core.net;

import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.franmontiel.persistentcookiejar.PersistentCookieJar;

import java.util.*;

import okhttp3.*;

public class WebviewSyncCookieManager
        implements CookieJar {
    private final PersistentCookieJar actualCookieJar;
    private final CookieManager webviewCookieManager;

    public WebviewSyncCookieManager(@NonNull PersistentCookieJar actualCookieJar) {
        this.actualCookieJar = actualCookieJar;
        webviewCookieManager = CookieManager.getInstance();
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
        Set<CustomHashCookie> toReturn = new HashSet<>();

        List<Cookie> okhttpCookies = actualCookieJar.loadForRequest(url);
        for (Cookie existing : okhttpCookies) {
            toReturn.add(new CustomHashCookie(existing));
        }

        String cookiesString = webviewCookieManager.getCookie(url.toString());
        List<Cookie> webviewCookies = cookieStringToList(url, cookiesString);
        for (Cookie existing : webviewCookies) {
            toReturn.add(new CustomHashCookie(existing));
        }

        List<Cookie> ret = new ArrayList<>();
        for (CustomHashCookie c : toReturn) {
            ret.add(c.cookie);
        }

        actualCookieJar.saveFromResponse(url, ret);

        return ret;
    }

    public void clearCookiesForUrl(HttpUrl url, @Nullable List<String> optionalNames) {
        String cookiesString = webviewCookieManager.getCookie(url.toString());
        List<Cookie> webviewCookies = cookieStringToList(url, cookiesString);
        for (Cookie existing : webviewCookies) {
            if (optionalNames == null || optionalNames.contains(existing.name())) {
                webviewCookieManager.setCookie(url.toString(), existing.newBuilder().expiresAt(0).build().toString());
            }
        }

        List<Cookie> okhttpCookies = actualCookieJar.loadForRequest(url);
        List<Cookie> updatedCookies = new ArrayList<>();
        for (Cookie cookie : okhttpCookies) {
            if (optionalNames == null || optionalNames.contains(cookie.name())) {
                updatedCookies.add(cookie.newBuilder().expiresAt(0).build());
            }
        }
        actualCookieJar.saveFromResponse(url, updatedCookies);
    }

    public void clearAllCookies() {
        webviewCookieManager.removeAllCookies(value -> {});
        actualCookieJar.clear();
    }

    private List<Cookie> cookieStringToList(HttpUrl baseUrl, String cookieString) {
        if (cookieString == null) return Collections.emptyList();
        //We can split on the ';' char as the cookie manager only returns cookies
        //that match the url and haven't expired, so the cookie attributes aren't included
        String[] cookieHeaders = cookieString.split(";");
        List<Cookie> ret = new ArrayList<>();
        for (String header : cookieHeaders) {
            Cookie parsed = Cookie.parse(baseUrl, header.trim());
            if (parsed != null) {
                ret.add(parsed);
            }
        }
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
            return cookie.name().equals(that.cookie.name()) && cookie.value().equals(that.cookie.value());
        }

        @Override
        public int hashCode() {
            return Objects.hash(cookie.name(), cookie.value());
        }
    }
}
