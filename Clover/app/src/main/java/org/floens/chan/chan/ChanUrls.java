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
package org.floens.chan.chan;

import org.floens.chan.core.settings.ChanSettings;

public class ChanUrls {
    public static String getCaptchaSiteKey() {
        return "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
    }

    public static String getBoardUrlDesktop(String board) {
        return scheme() + "://boards.4chan.org/" + board + "/";
    }

    public static String getThreadUrlDesktop(String board, int no) {
        return scheme() + "://boards.4chan.org/" + board + "/thread/" + no;
    }

    public static String getThreadUrlDesktop(String board, int no, int postNo) {
        return scheme() + "://boards.4chan.org/" + board + "/thread/" + no + "#p" + postNo;
    }

    public static String getCatalogUrlDesktop(String board) {
        return scheme() + "://boards.4chan.org/" + board + "/catalog";
    }

    private static String scheme() {
        return ChanSettings.networkHttps.get() ? "https" : "http";
    }
}
