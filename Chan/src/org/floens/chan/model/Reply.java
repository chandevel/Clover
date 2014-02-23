package org.floens.chan.model;

import java.io.File;

/**
 * The data needed to send a reply.
 */
public class Reply {
    public String name = "";
    public String email = "";
    public String subject = "";
    public String comment = "";
    public String board = "";
    public int resto = 0;
    public File file;
    public String fileName = "";
    public String captchaChallenge = "";
    public String captchaResponse = "";
}
