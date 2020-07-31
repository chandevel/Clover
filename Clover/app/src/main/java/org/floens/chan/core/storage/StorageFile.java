package org.floens.chan.core.storage;

import android.content.ContentResolver;
import android.media.MediaScannerConnection;
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

import static org.floens.chan.utils.AndroidUtils.getAppContext;

public class StorageFile {
    private final ContentResolver contentResolver;
    private final Uri uriOpenableByContentResolvers;
    private final File file;
    private final String name;

    public static StorageFile fromLegacyFile(File file) {
        return new StorageFile(file);
    }

    public static StorageFile fromUri(ContentResolver contentResolver, Uri uriOpenableByContentResolvers, String name) {
        return new StorageFile(contentResolver, uriOpenableByContentResolvers, name);
    }

    private StorageFile(ContentResolver contentResolver, Uri uriOpenableByContentResolvers, String name) {
        this.contentResolver = contentResolver;
        this.uriOpenableByContentResolvers = uriOpenableByContentResolvers;
        this.file = null;
        this.name = name;
    }

    private StorageFile(File file) {
        this.contentResolver = null;
        this.uriOpenableByContentResolvers = null;
        this.file = file;
        this.name = file.getName();
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
        return this.name;
    }

    public boolean exists() {
        if (isFile()) {
            return file.exists() && file.isFile();
        } else {
            return false; // TODO
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

    public void runMediaScanIfNeeded() {
        if (!isFile()) return;

        MediaScannerConnection.scanFile(
                getAppContext(),
                new String[]{file.getAbsolutePath()},
                null,
                (path, uri) -> {
                });
    }

    private boolean isFile() {
        return file != null;
    }
}
