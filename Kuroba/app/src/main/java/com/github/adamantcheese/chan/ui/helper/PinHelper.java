/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.helper;

public class PinHelper {
    public static String getShortUnreadCount(int value) {
        String count;
        if (value < 0) {
            count = "?";
        } else if (value < 1000) {
            count = String.valueOf(value);
        } else {
            int k = value / 1000;
            if (k < 10) {
                count = k + "k+";
            } else if (k < 100) {
                count = k + "k";
            } else {
                count = ":D";
            }
        }
        return count;
    }
}
