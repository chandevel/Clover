package com.github.adamantcheese.chan.core.saf.file

enum class FileDescriptorMode(val mode: String) {
    Read("r"),
    Write("w"),
    // When overwriting an existing file it is a really good ide to use truncate mode,
    // because otherwise if a new file's length is less than the old one's then there will be
    // old file's data left at the end of the file. Truncate flags will make sure that the file
    // is truncated at the end to fit the new length.
    WriteTruncate("wt")

    // ReadWrite and ReadWriteTruncate are not supported!
}