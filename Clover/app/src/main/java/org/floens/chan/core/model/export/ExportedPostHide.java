package org.floens.chan.core.model.export;

import com.google.gson.annotations.SerializedName;

public class ExportedPostHide {
    @SerializedName("site")
    private int site;
    @SerializedName("board")
    private String board;
    @SerializedName("no")
    private int no;

    //TODO: add "wholeThread" field when this task
    // https://github.com/Floens/Clover/pull/654/files#diff-5e9a09d29a1b99ff8a11eea249800b96R44 is merged

    public ExportedPostHide(int site, String board, int no) {
        this.site = site;
        this.board = board;
        this.no = no;
    }

    public int getSite() {
        return site;
    }

    public String getBoard() {
        return board;
    }

    public int getNo() {
        return no;
    }
}
