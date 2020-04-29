package com.github.adamantcheese.chan.utils;

public class JavaUtils {
    public static boolean in(int value, int[] array) {
        for (int i : array) {
            if (value == i) {
                return true;
            }
        }

        return false;
    }

    public static <T> boolean arrayPrefixedWith(byte[] array, byte[] prefix) {
        if (prefix.length > array.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != array[i]) {
                return false;
            }
        }
        return true;
    }
}
