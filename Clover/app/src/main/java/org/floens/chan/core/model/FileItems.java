package org.floens.chan.core.model;

import java.io.File;
import java.util.List;

public class FileItems {
    public final File path;
    public final List<FileItem> fileItems;

    public final boolean canNavigateUp;

    public FileItems(File path, List<FileItem> fileItems, boolean canNavigateUp) {
        this.path = path;
        this.fileItems = fileItems;
        this.canNavigateUp = canNavigateUp;
    }
}
