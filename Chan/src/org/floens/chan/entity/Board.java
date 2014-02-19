package org.floens.chan.entity;

/**
 * Board key and value.
 * key is full name e.g. Literature.
 * value is board key e.g. lit.
 */
public class Board {
    /**
     * Name of the board, e.g. Literature
     */
    public String key;
    /**
     * Name of the url, e.g. lit
     */
    public String value;
    
    public boolean workSafe = false;
    
    public boolean finish() {
        if (key == null || value == null) return false;
        
        return true;
    }
    
    @Override
    public String toString() {
        return key;
    }
}
