package com.github.adamantcheese.chan.ui.controller.settings.captcha

import android.content.Context
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
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
    private val saveAndApplyButton: AppCompatButton
    private val resetButton: AppCompatButton

    init {
        inject(this)

        inflate(context, R.layout.js_captcha_cookies_editor, this).apply {
            hsidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_hsid_cookie)
            ssidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_ssid_cookie)
            sidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_sid_cookie)
            nidCookieEditText = findViewById(R.id.js_captcha_cookies_editor_nid_cookie)
            saveAndApplyButton = findViewById(R.id.js_captcha_cookies_editor_save_and_apply)
            resetButton = findViewById(R.id.js_captcha_cookies_editor_reset)

            val prevCookiesJar = gson.fromJson<JsCaptchaCookiesJar>(
                    ChanSettings.jsCaptchaCookies.get(),
                    JsCaptchaCookiesJar::class.java
            )

            if (prevCookiesJar.hsidCookie.isNotEmpty()) {
                hsidCookieEditText.setText(prevCookiesJar.hsidCookie)
            }
            if (prevCookiesJar.ssidCookie.isNotEmpty()) {
                ssidCookieEditText.setText(prevCookiesJar.ssidCookie)
            }
            if (prevCookiesJar.sidCookie.isNotEmpty()) {
                sidCookieEditText.setText(prevCookiesJar.sidCookie)
            }
            if (prevCookiesJar.nidCookie.isNotEmpty()) {
                nidCookieEditText.setText(prevCookiesJar.nidCookie)
            }

            saveAndApplyButton.setOnClickListener {
                onSaveAndApplyClicked()
            }
            resetButton.setOnClickListener {
                onResetClicked()
            }
        }
    }

    fun onReady(callbacks: JsCaptchaCookiesEditorControllerCallbacks) {
        this.callbacks = callbacks
    }

    fun destroy() {
        this.callbacks = null
    }

    private fun onResetClicked() {
        hsidCookieEditText.setText("")
        ssidCookieEditText.setText("")
        sidCookieEditText.setText("")
        nidCookieEditText.setText("")

        ChanSettings.jsCaptchaCookies.set(EMPTY_JSON)
        callbacks?.onFinished()
    }

    private fun onSaveAndApplyClicked() {
        val hsidCookie = hsidCookieEditText.text?.toString() ?: ""
        val ssidCookie = ssidCookieEditText.text?.toString() ?: ""
        val sidCookie = sidCookieEditText.text?.toString() ?: ""
        val nidCookie = nidCookieEditText.text?.toString() ?: ""

        if (hsidCookie.isEmpty()) {
            hsidCookieEditText.error = context.getString(R.string.js_captcha_cookies_editor_bad_hsid)
            return
        }

        if (ssidCookie.isEmpty()) {
            ssidCookieEditText.error = context.getString(R.string.js_captcha_cookies_editor_bad_ssid)
            return
        }

        if (sidCookie.isEmpty()) {
            sidCookieEditText.error = context.getString(R.string.js_captcha_cookies_editor_bad_sid)
            return
        }

        if (nidCookie.isEmpty()) {
            nidCookieEditText.error = context.getString(R.string.js_captcha_cookies_editor_bad_nid)
            return
        }

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