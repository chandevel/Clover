package org.floens.chan.core.site.common;


import android.util.JsonReader;

public interface ChanReader {
    ChanParser getParser();

    void loadThread(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception;

    void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception;

    void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception;
}
