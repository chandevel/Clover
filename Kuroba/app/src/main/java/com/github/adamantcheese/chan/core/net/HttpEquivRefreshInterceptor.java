package com.github.adamantcheese.chan.core.net;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

import static java.nio.charset.StandardCharsets.UTF_8;

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
                BufferedSource source = initialResponse.body().source().peek();
                // we're looking for something like <meta http-equiv="refresh" content="0;URL='http://www.example.com/'"/>
                Document document = Jsoup.parse(source.readString(UTF_8));
                source.close();

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
                                    return initialResponse;
                                }
                            }
                            initialResponse.close();
                            Request r = new Request.Builder().url(redirectUrl).build();
                            // double the timeout, since we're basically sending a second request
                            return chain.withConnectTimeout(chain.connectTimeoutMillis() * 2, TimeUnit.MILLISECONDS)
                                    .withReadTimeout(chain.readTimeoutMillis() * 2, TimeUnit.MILLISECONDS)
                                    .withWriteTimeout(chain.writeTimeoutMillis(), TimeUnit.MILLISECONDS)
                                    .proceed(r);
                        } else {
                            return initialResponse;
                        }
                    }
                }
            }
        }
        return initialResponse;
    }
}
