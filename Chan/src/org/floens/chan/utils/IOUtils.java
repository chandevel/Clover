package org.floens.chan.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

public class IOUtils {
    public static String readString(InputStream is) {
        StringWriter sw = new StringWriter();

        try {
            copy(new InputStreamReader(is), sw);
            is.close();
            sw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sw.toString();
    }

    /**
     * Copies the inputstream to the outputstream and closes both streams.
     * 
     * @param is
     * @param os
     * @throws IOException
     */
    public static void copy(InputStream is, OutputStream os) throws IOException {
        int read = 0;
        byte[] buffer = new byte[4096];
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }

        is.close();
        os.close();
    }

    public static void copy(Reader input, Writer output) throws IOException {
        char[] buffer = new char[4096];
        int read = 0;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
