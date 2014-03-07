package org.floens.chan.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class SavedReply {
    @DatabaseField(generatedId = true)
    private int id;
    
    @DatabaseField
    public String board = "";
    
    @DatabaseField
    public int no;
    
    @DatabaseField
    public String password = "";
}
