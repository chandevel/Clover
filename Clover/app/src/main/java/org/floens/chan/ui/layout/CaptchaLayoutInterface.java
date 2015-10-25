package org.floens.chan.ui.layout;

public interface CaptchaLayoutInterface {
    void initCaptcha(String baseUrl, String siteKey, boolean lightTheme, CaptchaCallback callback);

    void reset();
}
