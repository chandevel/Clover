package org.floens.chan.core.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Board {
    public Board() {
    }
    
    public Board(String key, String value, boolean saved, boolean workSafe) {
        this.key = key;
        this.value = value;
        this.saved = saved;
        this.workSafe = workSafe;
    }
    
    @DatabaseField(generatedId = true)
    public int id;

    /**
     * Name of the board, e.g. Literature
     */
    @DatabaseField
    public String key;
    /**
     * Name of the url, e.g. lit
     */
    @DatabaseField
    public String value;

    @DatabaseField
    public boolean workSafe = false;

    @DatabaseField
    public boolean saved = false;

    @DatabaseField
    public int order;

    public boolean finish() {
        if (key == null || value == null)
            return false;

        return true;
    }

    @Override
    public String toString() {
        return key;
    }
    
    public boolean valueEquals(Board other) {
        return value.equals(other.value);
    }
}
