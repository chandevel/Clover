/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.model;

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
    public String password = "";
    public boolean usePass = false;
    public String passId = "";
    public boolean spoilerImage = false;
}
