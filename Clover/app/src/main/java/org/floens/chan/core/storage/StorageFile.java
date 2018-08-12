package org.floens.chan.core.storage;

import android.content.ContentResolver;
import android.net.Uri;

import org.floens.chan.utils.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StorageFile {
    private final ContentResolver contentResolver;
    private final Uri uriOpenableByContentResolvers;
    private final File file;

    public static StorageFile fromFile(File file) {
        return new StorageFile(file);
    }

    public static StorageFile fromUri(ContentResolver contentResolver, Uri uriOpenableByContentResolvers) {
        return new StorageFile(contentResolver, uriOpenableByContentResolvers);
    }

    private StorageFile(ContentResolver contentResolver, Uri uriOpenableByContentResolvers) {
        this.contentResolver = contentResolver;
        this.uriOpenableByContentResolvers = uriOpenableByContentResolvers;
        this.file = null;
    }

    private StorageFile(File file) {
        this.contentResolver = null;
        this.uriOpenableByContentResolvers = null;
        this.file = file;
    }

    public InputStream inputStream() throws IOException {
        if (isFile()) {
            return new FileInputStream(file);
        } else {
            return contentResolver.openInputStream(uriOpenableByContentResolvers);
        }
    }

    public OutputStream outputStream() throws IOException {
        if (isFile()) {
            File parent = file.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Could not create parent directory");
            }

            if (file.isDirectory()) {
                throw new IOException("Destination not a file");
            }

            return new FileOutputStream(file);
        } else {
            return contentResolver.openOutputStream(uriOpenableByContentResolvers);
        }
    }

    public String name() {
        return "dummy name";
    }

    public boolean exists() {
        if (isFile()) {
            return file.exists() && file.isFile();
        } else {
            return false; // we don't know?
        }
    }

    public boolean delete() {
        if (isFile()) {
            return file.delete();
        } else {
            // TODO
            return true;
        }
    }

    public void copyFrom(File source) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(source));
            os = new BufferedOutputStream(outputStream());
            IOUtils.copy(is, os);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
    }

    private boolean isFile() {
        return file != null;
    }
}
