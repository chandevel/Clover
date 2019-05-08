package org.floens.chan.core.database;

import android.annotation.SuppressLint;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.PostHide;
import org.floens.chan.utils.PostUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PostUtilsTest {

    /**
     * +---------------------+
     * | no = 0              | <- OP
     * | repliesTo = ...     |
     * +---------------------+
     * +---------------------+
     * | no = 1              | <- hidden
     * | repliesTo = ...     |
     * +---------------------+
     * +---------------------+
     * | no = 2              |
     * | repliesTo = ...     |
     * +---------------------+
     * +---------------------+
     * | no = 3              |
     * | repliesTo = ...     |
     * +---------------------+
     * +---------------------+
     * | no = 4              |
     * | repliesTo = 1,2     |
     * +---------------------+
     * +---------------------+
     * | no = 5              |
     * | repliesTo = 2,3,4   |
     * +---------------------+
     * +---------------------+
     * | no = 6              |
     * | repliesTo = 1,4,5   |
     * +---------------------+
     * +---------------------+
     * | no = 7              |
     * | repliesTo = 1,4,5,6 |
     * +---------------------+
     * +---------------------+
     * | no = 8              | <- hidden
     * | repliesTo = ...     |
     * +---------------------+
     * +---------------------+
     * | no = 9              |
     * | repliesTo = 1, 8    |
     * +---------------------+
     * +---------------------+
     * | no = 10             |
     * | repliesTo = 1       |
     * +---------------------+
     * +---------------------+
     * | no = 11             |
     * | repliesTo = 9       |
     * +---------------------+
     * +---------------------+
     * | no = 12             |
     * | repliesTo = 10, 11  |
     * +---------------------+
     * +---------------------+
     * | no = 13             |
     * | repliesTo = 2, 3    |
     * +---------------------+
     * <p>
     * INPUT:
     * - posts 1, 8 are hidden
     * - posts 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13 are not hidden
     * <p>
     * EXPECTED RESULT:
     * - posts 2, 3, 13 are not hidden
     * - posts 1, 4, 5, 6, 7, 8, 9, 10, 11, 12 are hidden
     * <p>
     * INPUT:
     * - posts 1, 8 are hidden
     * - posts 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13 are not hidden
     * <p>
     * EXPECTED RESULT:
     * - posts 2, 3, 13 are not hidden
     * - posts 1, 4, 5, 6, 7, 8, 9, 10, 11, 12 are hidden
     */

    @Test
    public void test_should_find_posts_that_reply_to_already_hidden_posts() {
        List<PostHide> firstGenerationHiddenPosts = new ArrayList<>();
        List<Post> posts = new ArrayList<>();
        Board board = new Board("test", "123");

        createPosts(posts, board);
        firstGenerationHiddenPosts.add(PostHide.hidePost(posts.get(0), 0, false, false, true));
        firstGenerationHiddenPosts.add(PostHide.hidePost(posts.get(7), 0, false, false, true));

        @SuppressLint("UseSparseArrays")
        Map<Integer, Post> postsFastLookupMap = new HashMap<>();
        for (Post post : posts) {
            postsFastLookupMap.put(post.no, post);
        }

        List<PostHide> updated = PostUtils.findHiddenPostsWithReplies(
                firstGenerationHiddenPosts,
                postsFastLookupMap);

        updated.sort((o1, o2) -> Integer.compare(o1.no, o2.no));

        assertEquals(10, updated.size());
        assertEquals(1, updated.get(0).no);
        assertEquals(4, updated.get(1).no);
        assertEquals(5, updated.get(2).no);
        assertEquals(6, updated.get(3).no);
        assertEquals(7, updated.get(4).no);
        assertEquals(8, updated.get(5).no);
        assertEquals(9, updated.get(6).no);
        assertEquals(10, updated.get(7).no);
        assertEquals(11, updated.get(8).no);
        assertEquals(12, updated.get(9).no);
    }

    private void createPosts(List<Post> posts, Board board) {
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(1)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>())
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(2)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test1")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>())
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(3)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test2")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>())
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(4)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(1, 2)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(5)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(2, 3, 4)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(6)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(1, 4, 5)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(7)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(1, 4, 5, 6)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(8)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>())
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(9)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(1, 8)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(10)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(1)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(11)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(9)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(12)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(10, 11)))
                        .build()
        );
        posts.add(
                new Post.Builder()
                        .board(board)
                        .id(13)
                        .opId(1)
                        .setUnixTimestampSeconds(1111L)
                        .comment("test3")
                        .filter(0, true, false, false, true)
                        .repliesTo(new HashSet<>(Arrays.asList(2, 3)))
                        .build()
        );
    }

}