package org.floens.chan.ui.layout;

public interface CaptchaCallback {
    void captchaLoaded(CaptchaLayoutInterface captchaLayout);

    void captchaEntered(CaptchaLayoutInterface captchaLayout, String challenge, String response);
}
