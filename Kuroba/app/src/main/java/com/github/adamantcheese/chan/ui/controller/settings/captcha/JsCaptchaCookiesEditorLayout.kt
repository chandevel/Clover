package com.github.adamantcheese.chan.ui.controller.settings.captcha

import android.content.Context
import android.widget.Button
import android.widget.FrameLayout
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import javax.inject.Inject

class JsCaptchaCookiesEditorLayout(context: Context) : FrameLayout(context) {

    @Inject
    lateinit var gson: Gson

    private var callbacks: JsCaptchaCookiesEditorControllerCallbacks? = null

    private val hsidCookieEditText: TextInputEditText
    private val ssidCookieEditText: TextInputEditText
    private val sidCookieEditText: TextInputEditText
    private val nidCookieEditText: TextInputEditText
    private val saveAndApplyButton: Button
    private val resetButton: Button

    init {
        inject(this)

        inflate(context, R.layout.js_captcha_cookies_editor, this).apply {
            hsidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_hsid_cookie)
            ssidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_ssid_cookie)
            sidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_sid_cookie)
            nidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_nid_cookie)
            saveAndApplyButton = findViewById(R.id.js_captcha_cookies_editor_save_and_apply)
            resetButton = findViewById(R.id.js_captcha_cookies_editor_reset)

            var prevCookiesJar = JsCaptchaCookiesJar.empty()
            try {
                prevCookiesJar = gson.fromJson(
                        ChanSettings.jsCaptchaCookies.get(),
                        JsCaptchaCookiesJar::class.java
                )
            } catch (ignored: Exception) {
                showToast(context, R.string.cookies_editor_failed_parse);
            }

            hsidCookieEditText.setText(prevCookiesJar.hsidCookie)
            ssidCookieEditText.setText(prevCookiesJar.ssidCookie)
            sidCookieEditText.setText(prevCookiesJar.sidCookie)
            nidCookieEditText.setText(prevCookiesJar.nidCookie)

            saveAndApplyButton.setOnClickListener {
                onSaveAndApplyClicked()
            }
            resetButton.setOnClickListener {
                hsidCookieEditText.setText("")
                ssidCookieEditText.setText("")
                sidCookieEditText.setText("")
                nidCookieEditText.setText("")

                ChanSettings.jsCaptchaCookies.set(EMPTY_JSON)
            }
        }
    }

    fun onReady(callbacks: JsCaptchaCookiesEditorControllerCallbacks) {
        this.callbacks = callbacks
    }

    fun destroy() {
        this.callbacks = null
    }

    private fun onSaveAndApplyClicked() {
        val hsidCookie = hsidCookieEditText.text?.toString() ?: ""
        val ssidCookie = ssidCookieEditText.text?.toString() ?: ""
        val sidCookie = sidCookieEditText.text?.toString() ?: ""
        val nidCookie = nidCookieEditText.text?.toString() ?: ""
        var notComplete = false

        if (hsidCookie.isEmpty()) {
            hsidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, hsidCookieEditText.hint)
            notComplete = true
        }

        if (ssidCookie.isEmpty()) {
            ssidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, ssidCookieEditText.hint)
            notComplete = true
        }

        if (sidCookie.isEmpty()) {
            sidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, sidCookieEditText.hint)
            notComplete = true
        }

        if (nidCookie.isEmpty()) {
            nidCookieEditText.error = getString(R.string.cookies_editor_bad_cookie, nidCookieEditText.hint)
            notComplete = true
        }

        if (notComplete) return

        val cookiesJar = JsCaptchaCookiesJar(
                hsidCookie = hsidCookie,
                ssidCookie = ssidCookie,
                sidCookie = sidCookie,
                nidCookie = nidCookie
        )

        val json = gson.toJson(cookiesJar)
        ChanSettings.jsCaptchaCookies.set(json)

        callbacks?.onFinished()
    }

    interface JsCaptchaCookiesEditorControllerCallbacks {
        fun onFinished()
    }
}