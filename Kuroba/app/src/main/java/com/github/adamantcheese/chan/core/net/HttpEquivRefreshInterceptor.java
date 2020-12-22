package com.github.adamantcheese.chan.core.net;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This interceptor follows up http-equiv redirects when applicable.
 */
public class HttpEquivRefreshInterceptor
        implements Interceptor {
    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain)
            throws IOException {
        Response initialResponse = chain.proceed(chain.request());
        if (initialResponse.isSuccessful() && initialResponse.body() != null) {
            String contentType = initialResponse.header("Content-Type");
            if (contentType != null && contentType.contains("text/html")) {
                // we're looking for something like <meta http-equiv="refresh" content="0;URL='http://www.example.com/'"/>
                Document document = Jsoup.parse(initialResponse.body().string());

                Elements metaTags = document.head().getElementsByTag("meta");

                for (Element metaTag : metaTags) {
                    String content = metaTag.attr("content");
                    String httpEquiv = metaTag.attr("http-equiv");

                    if (httpEquiv.equalsIgnoreCase("refresh")) {
                        String[] splitContent = content.split(";");
                        if (splitContent.length == 2) {
                            String url = splitContent[1].trim().substring(4);
                            HttpUrl redirectUrl;
                            try {
                                // url is directly specified
                                redirectUrl = HttpUrl.get(url);
                            } catch (Exception e) {
                                try {
                                    // url is surround by quotes maybe?
                                    redirectUrl = HttpUrl.get(url.substring(1, url.length() - 1));
                                } catch (Exception e2) {
                                    // the initial response has been consumed, so redo it
                                    return chain.proceed(chain.request());
                                }
                            }
                            Request r = chain.request()
                                    .newBuilder()
                                    .url(redirectUrl)
                                    .header("Host", redirectUrl.host())
                                    .header("Referer", redirectUrl.toString())
                                    .build();
                            return chain.proceed(r);
                        } else {
                            // the initial response has been consumed, so redo it
                            return chain.proceed(chain.request());
                        }
                    }
                }
            }
        }
        return initialResponse;
    }
}
