package com.github.adamantcheese.chan.core.model.save;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.save.spans.SerializableSpannableString;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Set;

public class SerializablePost {
    @SerializedName("board_id")
    private String boardId;
    @SerializedName("serializable_board")
    private SerializableBoard board;
    @SerializedName("no")
    private int no;
    @SerializedName("is_op")
    private boolean isOP;
    @SerializedName("name")
    private String name;
    @SerializedName("comment")
    private SerializableSpannableString comment;
    @SerializedName("subject")
    private SerializableSpannableString subject;
    @SerializedName("time")
    private long time;
    @SerializedName("images")
    private List<SerializablePostImage> images;
    @SerializedName("tripcode")
    private String tripcode;
    @SerializedName("id")
    private String id;
    @SerializedName("op_id")
    private int opId;
    @SerializedName("capcode")
    private String capcode;
    @SerializedName("is_saved_reply")
    private boolean isSavedReply;
//    private int filterHighlightedColor; // TODO: do we need this information in the saved thread?
//    private boolean filterStub;
//    private boolean filterRemove;
//    private boolean filterWatch;
//    private boolean filterReplies;
    @SerializedName("replies_to")
    private Set<Integer> repliesTo;
    @SerializedName("name_tripcode_id_capcode_span")
    private SerializableSpannableString nameTripcodeIdCapcodeSpan;
    @SerializedName("deleted")
    private Boolean deleted;
    @SerializedName("replies_from")
    private List<Integer> repliesFrom;
    @SerializedName("sticky")
    private boolean sticky;
    @SerializedName("closed")
    private boolean closed;
    @SerializedName("archived")
    private boolean archived;
    @SerializedName("replies")
    private int replies;
    @SerializedName("images_count")
    private int imagesCount;
    @SerializedName("unique_ips")
    private int uniqueIps;
    @SerializedName("last_modified")
    private long lastModified;
    @SerializedName("title")
    private String title;

    public SerializablePost(
            String boardId,
            SerializableBoard board,
            int no,
            boolean isOP,
            String name,
            SerializableSpannableString comment,
            SerializableSpannableString subject,
            long time,
            List<SerializablePostImage> images,
            String tripcode,
            String id,
            int opId,
            String capcode,
            boolean isSavedReply,
            Set<Integer> repliesTo,
            SerializableSpannableString nameTripcodeIdCapcodeSpan,
            Boolean deleted,
            List<Integer> repliesFrom,
            boolean sticky,
            boolean closed,
            boolean archived,
            int replies,
            int imagesCount,
            int uniqueIps,
            long lastModified,
            String title) {
        this.boardId = boardId;
        this.board = board;
        this.no = no;
        this.isOP = isOP;
        this.name = name;
        this.comment = comment;
        this.subject = subject;
        this.time = time;
        this.images = images;
        this.tripcode = tripcode;
        this.id = id;
        this.opId = opId;
        this.capcode = capcode;
        this.isSavedReply = isSavedReply;
        this.repliesTo = repliesTo;
        this.nameTripcodeIdCapcodeSpan = nameTripcodeIdCapcodeSpan;
        this.deleted = deleted;
        this.repliesFrom = repliesFrom;
        this.sticky = sticky;
        this.closed = closed;
        this.archived = archived;
        this.replies = replies;
        this.imagesCount = imagesCount;
        this.uniqueIps = uniqueIps;
        this.lastModified = lastModified;
        this.title = title;
    }

    public String getBoardId() {
        return boardId;
    }

    public int getNo() {
        return no;
    }

    public boolean isOP() {
        return isOP;
    }

    public String getName() {
        return name;
    }

    public SerializableSpannableString getComment() {
        return comment;
    }

    public SerializableSpannableString getSubject() {
        return subject;
    }

    public long getTime() {
        return time;
    }

    public List<SerializablePostImage> getImages() {
        return images;
    }

    public String getTripcode() {
        return tripcode;
    }

    public String getId() {
        return id;
    }

    public int getOpId() {
        return opId;
    }

    public String getCapcode() {
        return capcode;
    }

    public boolean isSavedReply() {
        return isSavedReply;
    }

    public Set<Integer> getRepliesTo() {
        return repliesTo;
    }

    public SerializableSpannableString getNameTripcodeIdCapcodeSpan() {
        return nameTripcodeIdCapcodeSpan;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public List<Integer> getRepliesFrom() {
        return repliesFrom;
    }

    public boolean isSticky() {
        return sticky;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isArchived() {
        return archived;
    }

    public int getReplies() {
        return replies;
    }

    public int getImagesCount() {
        return imagesCount;
    }

    public int getUniqueIps() {
        return uniqueIps;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int hashCode() {
        return 31 * no +
                31 * board.code.hashCode() +
                31 * board.siteId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        SerializablePost otherPost = (SerializablePost) other;

        return this.no == otherPost.no
                && this.board.code.equals(otherPost.board.code)
                && this.board.siteId == otherPost.board.siteId;
    }
}
