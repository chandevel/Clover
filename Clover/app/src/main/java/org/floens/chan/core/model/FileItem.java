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

import org.floens.chan.core.saver.StorageHelper;

import java.io.File;

public class FileItem {
    public File file;

    public FileItem(File file) {
        this.file = file;
    }

    public boolean isFile() {
        return file.isFile();
    }

    public boolean isFolder() {
        return file.isDirectory();
    }

    public boolean canNavigate() {
        return StorageHelper.canNavigate(file);
    }

    public boolean canOpen() {
        return StorageHelper.canOpen(file);
    }
}
