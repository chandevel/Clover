package com.github.adamantcheese.chan.core.net.interceptors

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.net.NetUtils
import com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.WebViewClientCompat
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor(private val context: Context) : Interceptor {

    private val executor = ContextCompat.getMainExecutor(context)

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (!AndroidUtils.supportsWebView()) {
            BackgroundUtils.runOnMainThread {
                showToast(context, R.string.fail_reason_webview_is_not_installed, Toast.LENGTH_LONG)
            }
            return chain.proceed(originalRequest)
        }

        WebSettings.getDefaultUserAgent(context)

        var response: Response? = null
        for (i in 1..5) {
            response = chain.proceed(originalRequest)

            // Check if Cloudflare anti-bot is on
            if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) {
                return response
            }

            try {
                response.close()
                NetUtils.loadWebviewCookies(originalRequest.url)
                val oldCookie = NetUtils.applicationClient.cookieJar.loadForRequest(originalRequest.url)
                        .firstOrNull { it.name == CF_CLEARANCE_NAME }
                NetUtils.clearSpecificCookies(originalRequest.url, COOKIE_NAMES)
                resolveWithWebView(originalRequest, oldCookie, i == 5)
            }
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            catch (e: CloudflareBypassException) {
                throw IOException("Could not do cloudflare bypass!")
            } catch (e: Exception) {
                throw IOException(e)
            }
        }
        return response ?: chain.proceed(originalRequest)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(request: Request, oldCookie: Cookie?, finalResolve: Boolean) {
        // We need to lock this thread until the WebView finds the challenge solution url, because
        // OkHttp doesn't support asynchronous interceptors.
        val latch = CountDownLatch(1)

        var webView: WebView? = null

        var challengeFound = false
        var cloudflareBypassed = false
        var isWebViewOutdated = false

        val origRequestUrl = request.url.toString()
        val headers = request.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }.toMutableMap()

        executor.execute {
            val webview = WebView(context)
            webView = webview
            webview.setDefaultSettings()

            webview.settings.userAgentString = NetUtils.USER_AGENT

            webview.webViewClient = object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(origRequestUrl: String): Boolean {
                        NetUtils.loadWebviewCookies(origRequestUrl.toHttpUrl())
                        return NetUtils.applicationClient.cookieJar.loadForRequest(origRequestUrl.toHttpUrl())
                                .firstOrNull { it.name == CF_CLEARANCE_NAME }
                                .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed(origRequestUrl)) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        // The first request didn't return the challenge, abort.
                        latch.countDown()
                    }
                }

                override fun onReceivedErrorCompat(
                        view: WebView,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String,
                        isMainFrame: Boolean,
                ) {
                    if (isMainFrame) {
                        if (errorCode in ERROR_CODES) {
                            // Found the Cloudflare challenge page.
                            challengeFound = true
                        } else {
                            // Unlock thread, the challenge wasn't found.
                            latch.countDown()
                        }
                    }
                }
            }

            webView?.loadUrl(origRequestUrl, headers)
        }

        // Wait a reasonable amount of time to retrieve the solution. The minimum should be
        // around 4 seconds but it can take more due to slow networks or server issues.
        latch.await(12, TimeUnit.SECONDS)

        executor.execute {
            if (!cloudflareBypassed) {
                isWebViewOutdated = webView?.isOutdated() == true
            }

            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }

        // Throw exception if we failed to bypass Cloudflare
        if (!cloudflareBypassed) {
            // Prompt user to update WebView if it seems too outdated
            if (isWebViewOutdated) {
                showToast(context, "Your webview is out of date, minimum 99, yours: " + (webView?.getWebViewMajorVersion()
                        ?: 0), Toast.LENGTH_LONG)
            }

            if (finalResolve) {
                throw CloudflareBypassException()
            }
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private const val CF_CLEARANCE_NAME = "cf_clearance"
        private val COOKIE_NAMES = listOf(CF_CLEARANCE_NAME)
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setDefaultSettings() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
    }
}

fun WebView.isOutdated(): Boolean {
    return getWebViewMajorVersion() < 99
}

private fun WebView.getWebViewMajorVersion(): Int {
    val uaRegexMatch = """.*Chrome/(\d+)\..*""".toRegex().matchEntire(getDefaultUserAgentString())
    return if (uaRegexMatch != null && uaRegexMatch.groupValues.size > 1) {
        uaRegexMatch.groupValues[1].toInt()
    } else {
        0
    }
}

private fun WebView.getDefaultUserAgentString(): String {
    val originalUA: String = settings.userAgentString

    // Next call to getUserAgentString() will get us the default
    settings.userAgentString = null
    val defaultUserAgentString = settings.userAgentString

    // Revert to original UA string
    settings.userAgentString = originalUA

    return defaultUserAgentString
}

private class CloudflareBypassException : Exception()
