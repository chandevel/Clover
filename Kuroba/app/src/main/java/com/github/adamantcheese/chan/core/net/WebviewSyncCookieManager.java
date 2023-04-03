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
    public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
        actualCookieJar.saveFromResponse(url, cookies);

        String sUrl = url.toString();
        for (Cookie cookie : actualCookieJar.loadForRequest(url)) {
            webviewCookieManager.setCookie(sUrl, cookie.toString());
        }
    }

    /**
     * Returns a list of cookies from both the okhttp cookie jar.
     * If you want to include webkit cookies, call loadWebviewCookiesIntoJar first.
     */
    @NonNull
    @Override
    public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
        return actualCookieJar.loadForRequest(url);
    }

    public void loadWebviewCookiesIntoJar(HttpUrl url) {
        actualCookieJar.saveFromResponse(url, getWebviewCookies(url));
    }

    public void clearCookiesForUrl(HttpUrl url, @Nullable List<String> optionalNames) {
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
        webviewCookieManager.removeAllCookies(null);
        actualCookieJar.clear();
    }

    private List<Cookie> getWebviewCookies(HttpUrl url) {
        String webviewCookies = webviewCookieManager.getCookie(url.toString());
        if (webviewCookies == null) return Collections.emptyList();
        //We can split on the ';' char as the cookie manager only returns cookies
        //that match the url and haven't expired, so the cookie attributes aren't included
        String[] cookieHeaders = webviewCookies.split(";");
        List<Cookie> ret = new ArrayList<>();
        for (String header : cookieHeaders) {
            Cookie parsed = Cookie.parse(url, header.trim());
            if (parsed != null) {
                ret.add(parsed);
            }
        }
        return ret;
    }
}
