package org.floens.chan.entity;

/**
 * Anything that links to something in a post uses this entity.
 */
public class PostLinkable {
    public static enum Type {QUOTE, LINK};
    
    public final String key;
    public final String value;
    public final Type type;
    
    public PostLinkable(String key, String value, Type type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }
}
