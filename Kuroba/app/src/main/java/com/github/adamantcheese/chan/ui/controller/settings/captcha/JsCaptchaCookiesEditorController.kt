package com.github.adamantcheese.chan.ui.controller.settings.captcha

import android.content.Context
import androidx.appcompat.widget.AppCompatButton
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON
import com.github.adamantcheese.chan.utils.AndroidUtils.inflate
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import javax.inject.Inject

class JsCaptchaCookiesEditorController(context: Context) : Controller(context) {

    @Inject
    lateinit var gson: Gson

    private lateinit var hsidCookieEditText: TextInputEditText
    private lateinit var ssidCookieEditText: TextInputEditText
    private lateinit var sidCookieEditText: TextInputEditText
    private lateinit var nidCookieEditText: TextInputEditText

    private lateinit var saveAndApplyButton: AppCompatButton
    private lateinit var resetButton: AppCompatButton

    private var onFinishedListener: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        inject(this)

        navigation.setTitle(R.string.js_captcha_cookies_editor_controller_title)
        view = inflate(context, R.layout.js_captcha_cookies_editor)

        hsidCookieEditText = view.findViewById(R.id.js_captcha_cookies_editor_hsid_cookie)
        ssidCookieEditText = view.findViewById(R.id.js_captcha_cookies_editor_ssid_cookie)
        sidCookieEditText = view.findViewById(R.id.js_captcha_cookies_editor_sid_cookie)
        nidCookieEditText = view.findViewById(R.id.js_captcha_cookies_editor_nid_cookie)
        saveAndApplyButton = view.findViewById(R.id.js_captcha_cookies_editor_save_and_apply)
        resetButton = view.findViewById(R.id.js_captcha_cookies_editor_reset)

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

    override fun onDestroy() {
        super.onDestroy()

        onFinishedListener = null
    }

    fun setOnFinishedCallback(callback: () -> Unit) {
        this.onFinishedListener = callback
    }

    private fun onResetClicked() {
        hsidCookieEditText.setText("")
        ssidCookieEditText.setText("")
        sidCookieEditText.setText("")
        nidCookieEditText.setText("")

        ChanSettings.jsCaptchaCookies.set(EMPTY_JSON)
        onFinishedListener?.invoke()
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

        onFinishedListener?.invoke()
    }
}