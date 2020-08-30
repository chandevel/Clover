package com.github.adamantcheese.chan.ui.controller.settings.captcha

import android.content.Context
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser

class JsCaptchaCookiesEditorController(context: Context) :
        Controller(context), JsCaptchaCookiesEditorLayout.JsCaptchaCookiesEditorControllerCallbacks {

    override fun onCreate() {
        super.onCreate()

        navigation.setTitle(R.string.settings_js_captcha_cookies_title)
        navigation.buildMenu().withItem(R.drawable.ic_help_outline_white_24dp) { displayHelp() }
        view = JsCaptchaCookiesEditorLayout(context).apply {
            onReady(this@JsCaptchaCookiesEditorController)
        }
    }

    override fun onFinished() {
        navigationController.popController()
    }

    fun displayHelp() {
        openLinkInBrowser(context, BuildConfig.GITHUB_ENDPOINT + "/wiki/JS-Captcha-Cookies-Guide")
    }

}