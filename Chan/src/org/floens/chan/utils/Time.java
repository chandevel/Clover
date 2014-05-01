package org.floens.chan.utils;

public class Time {
    public static long get() {
        return System.currentTimeMillis();
    }
    
    public static long get(long other) {
        return System.currentTimeMillis() - other;
    }
}
