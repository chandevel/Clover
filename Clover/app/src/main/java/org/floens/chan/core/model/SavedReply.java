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

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class SavedReply {
    public SavedReply() {
    }

    public SavedReply(String board, int no, String password) {
        this.board = board;
        this.no = no;
        this.password = password;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj == this) return true;
        if (!(obj instanceof SavedReply)) return false;
        SavedReply o = (SavedReply) obj;
        return o.board.equals(this.board) && o.id == this.id && o.no == this.no && o.password.equals(this.password);
    }

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField
    public String board = "";

    @DatabaseField
    public int no;

    @DatabaseField
    public String password = "";
}
