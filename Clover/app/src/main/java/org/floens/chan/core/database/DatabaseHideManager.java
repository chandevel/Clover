package org.floens.chan.core.database;

import android.support.annotation.Nullable;

import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.PostHide;
import org.floens.chan.utils.PostUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class DatabaseHideManager {
    private static final String TAG = "DatabaseHideManager";

    private static final long POST_HIDE_TRIM_TRIGGER = 25000;
    private static final long POST_HIDE_TRIM_COUNT = 5000;

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabaseHideManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(helper.postHideDao, DatabaseHelper.POST_HIDE_TABLE_NAME,
                    POST_HIDE_TRIM_TRIGGER, POST_HIDE_TRIM_COUNT);

            return null;
        };
    }

    /**
     * Searches for hidden posts in the PostHide table then checks whether there are posts with a reply
     * to already hidden posts and if there are hides them as well. This process is recursive.
     */
    public List<Post> filterHiddenPosts(List<Post> posts, int siteId, String board) {
        return databaseManager.runTask(() -> {
            List<Integer> postNoList = new ArrayList<>(posts.size());

            for (Post post : posts) {
                postNoList.add(post.no);
            }

            // find first iteration hidden posts among posts hidden in the database and posts hidden by filters
            List<PostHide> hiddenPostsFirstIteration = getHiddenPosts(siteId, board, posts, postNoList);

            List<PostHide> hiddenPosts = PostUtils.findHiddenPostsWithReplies(
                    hiddenPostsFirstIteration,
                    posts
            );

            for (PostHide newHiddenPost : hiddenPosts) {
                helper.postHideDao.createIfNotExists(newHiddenPost);
            }

            List<Post> resultList = new ArrayList<>();

            // filter out hidden posts
            for (Post post : posts) {
                if (post.filterRemove) {
                    // this post is already filtered by the filtering system
                    continue;
                }

                PostHide hiddenPost = findHiddenPost(hiddenPosts, post, siteId, board);

                if (hiddenPost != null) {
                    if (hiddenPost.hide) {
                        // hide post

                        // rebuild the whole post but with filter stub
                        Post newPost = rebuildPostWithCustomStub(post);
                        resultList.add(newPost);
                    } else {
                        // remove post

                        if (post.isOP) {
                            // hide OP post only if the user hid the whole thread
                            if (!hiddenPost.wholeThread) {
                                resultList.add(post);
                            }
                        }
                    }
                } else {
                    // no record of hidden post in the DB
                    resultList.add(post);
                }
            }

            //return posts that are NOT hidden
            return resultList;
        });
    }

    private List<PostHide> getHiddenPosts(
            int siteId,
            String board,
            List<Post> posts,
            List<Integer> postNoList
    ) throws SQLException {
        Set<PostHide> hiddenInDatabase = new HashSet<>(helper.postHideDao.queryBuilder().where()
                .in(PostHide.NO_COLUMN_NAME, postNoList)
                .and()
                .eq(PostHide.SITE_COLUMN_NAME, siteId)
                .and()
                .eq(PostHide.BOARD_COLUMN_NAME, board)
                .query());

        for (Post post : posts) {
            if (post.isOP) {
                continue;
            }

            if (post.filterRemove || post.filterHighlightedColor != 0 || post.filterReplies || post.filterStub) {
                PostHide postHide = new PostHide();
                postHide.no = post.no;
                postHide.site = siteId;
                postHide.board = board;
                postHide.wholeThread = false;
                postHide.hide = true;

                hiddenInDatabase.add(postHide);
            }
        }

        return new ArrayList<>(hiddenInDatabase);
    }

    private Post rebuildPostWithCustomStub(Post post) {
        return new Post.Builder()
                .board(post.board)
                .posterId(post.id)
                .opId(post.opId)
                .id(post.no)
                .op(post.isOP)
                .replies(post.getReplies())
                .images(post.getImagesCount())
                .uniqueIps(post.getUniqueIps())
                .sticky(post.isSticky())
                .archived(post.isArchived())
                .lastModified(post.getLastModified())
                .closed(post.isClosed())
                .subject(post.subject)
                .name(post.name)
                .comment(post.comment.toString())
                .tripcode(post.tripcode)
                .setUnixTimestampSeconds(post.time)
                .images(post.images)
                .moderatorCapcode(post.capcode)
                .httpIcons(post.httpIcons)
                // reset filterHighlightedColor and filterRemove to false because we will be using filterStub
                .filter(0, true, false, post.filterReplies)
                .isSavedReply(post.isSavedReply)
                .spans(post.subjectSpan, post.nameTripcodeIdCapcodeSpan)
                .linkables(post.linkables)
                .repliesTo(post.repliesTo)
                .build();
    }

    @Nullable
    private PostHide findHiddenPost(List<PostHide> hiddenPosts, Post post, int siteId, String board) {
        for (PostHide postHide : hiddenPosts) {
            if (post.no == postHide.no && siteId == postHide.site && board.equals(postHide.board)) {
                return postHide;
            }
        }

        return null;
    }

    public Callable<Void> addThreadHide(PostHide hide) {
        return () -> {
            if (contains(hide)) {
                return null;
            }

            helper.postHideDao.createIfNotExists(hide);

            return null;
        };
    }

    public Callable<Void> addPostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                if (contains(postHide)) {
                    continue;
                }

                helper.postHideDao.createIfNotExists(postHide);
            }

            return null;
        };
    }

    public Callable<Void> removeThreadHide(PostHide hide) {
        return () -> {
            helper.postHideDao.delete(hide);

            return null;
        };
    }

    public Callable<Void> removePostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                helper.postHideDao.delete(postHide);
            }

            return null;
        };
    }

    private boolean contains(PostHide hide) throws SQLException {
        PostHide inDb = helper.postHideDao.queryBuilder().where()
                .eq(PostHide.NO_COLUMN_NAME, hide.no)
                .and()
                .eq(PostHide.SITE_COLUMN_NAME, hide.site)
                .and()
                .eq(PostHide.BOARD_COLUMN_NAME, hide.board)
                .queryForFirst();

        //if this thread is already hidden - do nothing
        return inDb != null;
    }

    public Callable<Void> clearAllThreadHides() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), PostHide.class);

            return null;
        };
    }
}
