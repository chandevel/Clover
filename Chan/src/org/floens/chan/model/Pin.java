package org.floens.chan.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Pin {
    @DatabaseField(generatedId = true)
    private int id;
    
    public Type type = Type.THREAD;
    
    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable = new Loadable("", -1);
    
    /** Header is used to display a static header in the drawer listview. */
    public static enum Type {
        HEADER, 
        THREAD
    };
}





