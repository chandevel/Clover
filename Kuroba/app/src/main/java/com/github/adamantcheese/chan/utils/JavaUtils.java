package com.github.adamantcheese.chan.utils;

import java.io.IOException;
import java.io.InputStream;

import okio.BufferedSource;
import okio.ByteString;
import okio.HashingSink;
import okio.Okio;

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

    public static String inputStreamMD5hash(InputStream inputStream) {
        try (BufferedSource buffer = Okio.buffer(Okio.source(inputStream));
             HashingSink sink = HashingSink.md5(Okio.blackhole())) {
            buffer.readAll(sink);
            return sink.hash().hex();
        } catch (IOException e) {
            return null;
        }
    }

    public static String stringMD5hash(String inputString) {
        return ByteString.encodeUtf8(inputString).md5().hex();
    }
}
