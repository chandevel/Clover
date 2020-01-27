package com.github.adamantcheese.chan.core.site

data class ChunkDownloaderSiteProperties(
        /**
         * Whether the site send file size info  in bytes or not. Some sites may send it in KB which
         * breaks ChunkedFileDownloader. To figure out whether a site sends us bytes or kilobytes
         * (or something else) you will have to look into the thread json of a concrete site.
         * If a site uses Vichan or Futaba chan engine then they most likely send file size in bytes.
         * */
        val siteSendsCorrectFileSizeInBytes: Boolean,

        /**
         * Some sites (Wired-7) may send incorrect file md5 to us (sometimes) so we have no other way other
         * than file md5 disabling for such sites
         * */
        val canFileHashBeTrusted: Boolean
)