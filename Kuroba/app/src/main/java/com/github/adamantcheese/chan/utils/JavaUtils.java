package com.github.adamantcheese.chan.utils;

import java.io.InputStream;
import java.util.Arrays;

import kotlin.random.Random;
import okio.ByteString;

public class JavaUtils {
    public static boolean in(int value, int[] array) {
        return Arrays.binarySearch(array, value) >= 0;
    }

    public static boolean arrayPrefixedWith(byte[] array, byte[] prefix) {
        if (prefix.length > array.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != array[i]) {
                return false;
            }
        }
        return true;
    }

    public static String inputStreamMD5hash(InputStream inputStream) {
        try {
            return ByteString.read(inputStream, inputStream.available()).md5().hex();
        } catch (Throwable t) {
            return ByteString.encodeUtf8(String.valueOf(Random.Default.nextLong())).md5().hex();
        }
    }

    public static String stringMD5hash(String inputString) {
        try {
            return ByteString.encodeUtf8(inputString).md5().hex();
        } catch (Throwable t) {
            return ByteString.encodeUtf8(String.valueOf(Random.Default.nextLong())).md5().hex();
        }
    }
}
