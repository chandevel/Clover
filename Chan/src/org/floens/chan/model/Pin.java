package org.floens.chan.model;


public class Pin {
    public Type type = Type.THREAD;
    public Loadable loadable = new Loadable("", -1);
    
    /** Header is used to display a static header in the drawer listview. */
    public static enum Type {
        HEADER, 
        THREAD
    };
}





