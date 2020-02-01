package com.github.adamantcheese.chan.ui.controller.settings.captcha

import android.content.Context
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller

class JsCaptchaCookiesEditorController(context: Context) :
        Controller(context), JsCaptchaCookiesEditorLayout.JsCaptchaCookiesEditorControllerCallbacks {

    override fun onCreate() {
        super.onCreate()
        inject(this)

        navigation.setTitle(R.string.js_captcha_cookies_editor_controller_title)
        view = JsCaptchaCookiesEditorLayout(context).apply {
            onReady(this@JsCaptchaCookiesEditorController)
        }
    }

    override fun onFinished() {
        navigationController.popController()
    }

}