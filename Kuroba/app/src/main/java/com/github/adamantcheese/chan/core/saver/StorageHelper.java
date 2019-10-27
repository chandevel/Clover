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
package com.github.adamantcheese.chan.core.saver;

import java.io.File;

public class StorageHelper {
    public static boolean canNavigate(File file) {
        return file != null && !isDirectoryBlacklisted(file) && file.exists()
                && file.isDirectory() && file.canRead();
    }

    public static boolean isDirectoryBlacklisted(File file) {
        String absolutePath = file.getAbsolutePath();
        switch (absolutePath) {
            case "/storage":
            case "/storage/emulated":
            case "/storage/emulated/0/0":
            case "/storage/emulated/legacy":
                return true;
        }
        return false;
    }

    public static boolean canOpen(File file) {
        return file != null && file.exists() && file.isFile() && file.canRead();
    }
}
