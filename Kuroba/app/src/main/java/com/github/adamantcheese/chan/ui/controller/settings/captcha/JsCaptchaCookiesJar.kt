package com.github.adamantcheese.chan.ui.controller.settings.captcha

import com.google.gson.annotations.SerializedName

data class JsCaptchaCookiesJar(
        @SerializedName("hsid_cookie")
        val hsidCookie: String = "",
        @SerializedName("ssid_cookie")
        val ssidCookie: String = "",
        @SerializedName("sid_cookie")
        val sidCookie: String = "",
        @SerializedName("nid_cookie")
        val nidCookie: String = ""
) {

    fun isValid(): Boolean {
        return hsidCookie.isNotEmpty()
                && ssidCookie.isNotEmpty()
                && sidCookie.isNotEmpty()
                && nidCookie.isNotEmpty()
    }

    companion object {
        @JvmStatic
        fun empty(): JsCaptchaCookiesJar {
            return JsCaptchaCookiesJar(
                    hsidCookie = "",
                    ssidCookie = "",
                    sidCookie = "",
                    nidCookie = ""
            )
        }
    }
}