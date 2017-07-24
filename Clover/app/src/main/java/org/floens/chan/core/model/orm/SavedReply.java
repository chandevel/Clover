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
package org.floens.chan.core.model.orm;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "savedreply")
public class SavedReply {
    public SavedReply() {
    }

    public SavedReply(String board, int no, String password) {
        this.board = board;
        this.no = no;
        this.password = password;
    }

    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(columnName = "site")
    public int site;

    @DatabaseField(index = true, canBeNull = false)
    public String board;

    @DatabaseField(index = true)
    public int no;

    @DatabaseField
    public String password = "";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SavedReply that = (SavedReply) o;

        return no == that.no && board.equals(that.board);
    }

    @Override
    public int hashCode() {
        int result = board.hashCode();
        result = 31 * result + no;
        return result;
    }
}
