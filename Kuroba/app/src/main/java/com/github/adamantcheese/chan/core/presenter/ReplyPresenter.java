/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.presenter;

import android.text.TextUtils;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.LastReplyRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.vdurmont.emoji.EmojiParser;

import java.io.File;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class ReplyPresenter
        implements AuthenticationLayoutCallback, ImagePickDelegate.ImagePickCallback, SiteActions.PostListener {

    public enum Page {
        INPUT,
        AUTHENTICATION,
        LOADING
    }

    private static final String TAG = "ReplyPresenter";
    private static final Pattern QUOTE_PATTERN = Pattern.compile(">>\\d+");
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ReplyPresenterCallback callback;

    private ReplyManager replyManager;
    private WatchManager watchManager;
    private DatabaseManager databaseManager;
    private LastReplyRepository lastReplyRepository;

    private boolean bound = false;
    private Loadable loadable;
    private Board board;
    private Reply draft;

    private Page page = Page.INPUT;
    private boolean moreOpen;
    private boolean previewOpen;
    private boolean pickingFile;
    private int selectedQuote = -1;

    @Inject
    public ReplyPresenter(
            ReplyManager replyManager,
            WatchManager watchManager,
            DatabaseManager databaseManager,
            LastReplyRepository lastReplyRepository
    ) {
        this.replyManager = replyManager;
        this.watchManager = watchManager;
        this.databaseManager = databaseManager;
        this.lastReplyRepository = lastReplyRepository;
    }

    public void create(ReplyPresenterCallback callback) {
        this.callback = callback;
    }

    public void bindLoadable(Loadable loadable) {
        if (this.loadable != null) {
            unbindLoadable();
        }
        bound = true;
        this.loadable = loadable;

        this.board = loadable.board;

        draft = replyManager.getReply(loadable);

        if (TextUtils.isEmpty(draft.name)) {
            draft.name = ChanSettings.postDefaultName.get();
        }

        callback.loadDraftIntoViews(draft);
        callback.updateCommentCount(0, board.maxCommentChars, false);
        callback.setCommentHint(getString(loadable.isThreadMode()
                ? R.string.reply_comment_thread
                : R.string.reply_comment_board));
        callback.showCommentCounter(board.maxCommentChars > 0);

        if (draft.file != null) {
            showPreview(draft.fileName, draft.file);
        }

        switchPage(Page.INPUT);
    }

    public void unbindLoadable() {
        bound = false;
        draft.file = null;
        draft.fileName = "";
        callback.loadViewsIntoDraft(draft);
        replyManager.putReply(loadable, draft);

        closeAll();
    }

    public void onOpen(boolean open) {
        if (open) {
            callback.focusComment();
        }
    }

    public boolean onBack() {
        if (page == Page.LOADING) {
            return true;
        } else if (page == Page.AUTHENTICATION) {
            switchPage(Page.INPUT);
            return true;
        } else if (moreOpen) {
            onMoreClicked();
            return true;
        }
        return false;
    }

    public void onMoreClicked() {
        moreOpen = !moreOpen;
        callback.setExpanded(moreOpen);
        callback.openNameOptions(moreOpen);
        if (!loadable.isThreadMode()) {
            callback.openSubject(moreOpen);
        }
        if (previewOpen) {
            callback.openFileName(moreOpen);
            if (board.spoilers) {
                callback.openSpoiler(moreOpen, false);
            }
        }
    }

    public boolean isExpanded() {
        return moreOpen;
    }

    public void onAttachClicked(boolean longPressed) {
        if (!pickingFile) {
            if (previewOpen) {
                callback.openPreview(false, null);
                draft.file = null;
                draft.fileName = "";
                if (moreOpen) {
                    callback.openFileName(false);
                    if (board.spoilers) {
                        callback.openSpoiler(false, false);
                    }
                }
                previewOpen = false;
            } else {
                pickingFile = true;
                callback.getImagePickDelegate().pick(this, longPressed);
            }
        }
    }

    public void onAuthenticateCalled() {
        if (loadable.site.actions().postRequiresAuthentication()) {
            if (!onPrepareToSubmit(true)) {
                return;
            }

            switchPage(Page.AUTHENTICATION, true, false);
        }
    }

    public void onSubmitClicked() {
        if (!onPrepareToSubmit(false)) {
            return;
        }

        //only 4chan seems to have the post delay, this is a hack for that
        if (draft.loadable.site.name().equals("4chan")) {
            if (loadable.isThreadMode()) {
                if (lastReplyRepository.canPostReply(draft.loadable.site, draft.loadable.board, draft.file != null)) {
                    submitOrAuthenticate();
                } else {
                    long lastPostTime = lastReplyRepository.getLastReply(draft.loadable.site, draft.loadable.board);

                    long waitTime = draft.file != null
                            ? draft.loadable.board.cooldownImages
                            : draft.loadable.board.cooldownReplies;

                    if (draft.loadable.site.actions().isLoggedIn()) {
                        waitTime /= 2;
                    }

                    long timeLeft = waitTime - ((System.currentTimeMillis() - lastPostTime) / 1000L);
                    String errorMessage = getString(R.string.reply_error_message_timer_reply, timeLeft);
                    switchPage(Page.INPUT);
                    callback.openMessage(true, false, errorMessage, true);
                }
            } else if (loadable.isCatalogMode()) {
                if (lastReplyRepository.canPostThread(draft.loadable.site, draft.loadable.board)) {
                    submitOrAuthenticate();
                } else {
                    long lastThreadTime = lastReplyRepository.getLastThread(draft.loadable.site, draft.loadable.board);

                    long waitTime = draft.loadable.board.cooldownThreads;
                    if (draft.loadable.site.actions().isLoggedIn()) {
                        waitTime /= 2;
                    }

                    long timeLeft = waitTime - ((System.currentTimeMillis() - lastThreadTime) / 1000L);
                    String errorMessage = getString(R.string.reply_error_message_timer_thread, timeLeft);
                    switchPage(Page.INPUT);

                    callback.openMessage(true, false, errorMessage, true);
                }
            } else {
                Logger.wtf(TAG, "Loadable isn't a thread or a catalog loadable????");
            }
        } else {
            submitOrAuthenticate();
        }
    }

    private void submitOrAuthenticate() {
        if (loadable.site.actions().postRequiresAuthentication()) {
            switchPage(Page.AUTHENTICATION);
        } else {
            makeSubmitCall();
        }
    }

    private boolean onPrepareToSubmit(boolean isAuthenticateOnly) {
        callback.loadViewsIntoDraft(draft);

        if (!isAuthenticateOnly && (draft.comment.trim().isEmpty() && draft.file == null)) {
            callback.openMessage(true, false, getString(R.string.reply_comment_empty), true);
            return false;
        }

        draft.loadable = loadable;
        draft.spoilerImage = draft.spoilerImage && board.spoilers;
        draft.captchaResponse = null;
        if (ChanSettings.enableEmoji.get()) {
            draft.comment = EmojiParser.parseFromUnicode(draft.comment,
                    e -> ":" + e.getEmoji().getAliases().get(0) + (e.hasFitzpatrick()
                            ? "|" + e.getFitzpatrickType()
                            : "") + ": "
            );
        }

        return true;
    }

    @Override
    public void onPostComplete(HttpCall httpCall, ReplyResponse replyResponse) {
        if (replyResponse.posted) {
            //if the thread being presented has changed in the time waiting for this call to complete, the loadable field in
            //ReplyPresenter will be incorrect; reconstruct the loadable (local to this method) from the reply response
            Site localSite = instance(SiteRepository.class).forId(replyResponse.siteId);
            Board localBoard = instance(BoardRepository.class).getFromCode(localSite, replyResponse.boardCode);
            Loadable localLoadable =
                    databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(localSite, localBoard,
                            //this loadable is for the reply response's site and board
                            replyResponse.threadNo == 0 ? replyResponse.postNo : replyResponse.threadNo,
                            //for the time being, will be updated later when the watchmanager updates
                            "/" + localBoard.code + "/"
                    ));

            lastReplyRepository.putLastReply(localLoadable.site, localLoadable.board);
            if (localLoadable.isCatalogMode()) {
                lastReplyRepository.putLastThread(localLoadable.site, localLoadable.board);
            }

            if (ChanSettings.postPinThread.get()) {
                if (localLoadable.isThreadMode()) {
                    //reply
                    ChanThread thread = callback.getThread();
                    if (thread != null) {
                        watchManager.createPin(localLoadable, thread.getOp(), PinType.WATCH_NEW_POSTS);
                    } else {
                        watchManager.createPin(localLoadable);
                    }
                } else {
                    //new thread
                    watchManager.createPin(localLoadable);
                }
            }

            SavedReply savedReply = SavedReply.fromSiteBoardNoPassword(localLoadable.site,
                    localLoadable.board,
                    replyResponse.postNo,
                    replyResponse.password
            );
            databaseManager.runTaskAsync(databaseManager.getDatabaseSavedReplyManager().saveReply(savedReply));

            switchPage(Page.INPUT);
            closeAll();
            highlightQuotes();
            String name = draft.name;
            draft = new Reply();
            draft.name = name;
            replyManager.putReply(localLoadable, draft);
            callback.loadDraftIntoViews(draft);
            callback.onPosted();

            //special case for new threads, check if we were on the catalog with the nonlocal loadable
            if (bound && loadable.isCatalogMode()) {
                callback.showThread(localLoadable);
            }
        } else if (replyResponse.requireAuthentication) {
            switchPage(Page.AUTHENTICATION);
        } else {
            String errorMessage = getString(R.string.reply_error);
            if (replyResponse.errorMessage != null) {
                errorMessage = getString(R.string.reply_error_message, replyResponse.errorMessage);
            }

            Logger.e(TAG, "onPostComplete error", errorMessage);
            switchPage(Page.INPUT);
            callback.openMessage(true, false, errorMessage, true);
        }
    }

    @Override
    public void onUploadingProgress(int percent) {
        //called on a background thread!
        runOnUiThread(() -> callback.onUploadingProgress(percent));
    }

    @Override
    public void onPostError(HttpCall httpCall, Exception exception) {
        Logger.e(TAG, "onPostError", exception);

        switchPage(Page.INPUT);

        String errorMessage = getString(R.string.reply_error);
        if (exception != null) {
            String message = exception.getMessage();
            if (message != null) {
                errorMessage = getString(R.string.reply_error_message, message);
            }
        }

        callback.openMessage(true, false, errorMessage, true);
    }

    @Override
    public void onAuthenticationComplete(
            AuthenticationLayoutInterface authenticationLayout, String challenge, String response, boolean autoReply
    ) {
        draft.captchaChallenge = challenge;
        draft.captchaResponse = response;

        if (autoReply) {
            makeSubmitCall();
        } else {
            switchPage(Page.INPUT);
        }
    }

    @Override
    public void onFallbackToV1CaptchaView(boolean autoReply) {
        callback.onFallbackToV1CaptchaView(autoReply);
    }

    public void onCommentTextChanged(CharSequence text) {
        int length = text.toString().getBytes(UTF_8).length;
        callback.updateCommentCount(length, board.maxCommentChars, length > board.maxCommentChars);
    }

    public void onSelectionChanged() {
        callback.loadViewsIntoDraft(draft);
        highlightQuotes();
    }

    public boolean fileNameLongClicked() {
        String currentExt = StringUtils.extractFileNameExtension(draft.fileName);
        if (currentExt == null) {
            currentExt = "";
        } else {
            currentExt = "." + currentExt;
        }
        draft.fileName = System.currentTimeMillis() + currentExt;
        callback.loadDraftIntoViews(draft);
        return true;
    }

    public void quote(Post post, boolean withText) {
        handleQuote(post, withText ? post.comment.toString() : null);
    }

    public void quote(Post post, CharSequence text) {
        handleQuote(post, text.toString());
    }

    private void handleQuote(Post post, String textQuote) {
        callback.loadViewsIntoDraft(draft);

        String extraNewline = "";
        if (draft.selectionStart - 1 >= 0 && draft.selectionStart - 1 < draft.comment.length()
                && draft.comment.charAt(draft.selectionStart - 1) != '\n') {
            extraNewline = "\n";
        }

        String postQuote = "";
        if (post != null) {
            if (!draft.comment.contains(">>" + post.no)) {
                postQuote = ">>" + post.no + "\n";
            }
        }

        StringBuilder textQuoteResult = new StringBuilder();
        if (textQuote != null) {
            String[] lines = textQuote.split("\n+");
            // matches for >>123, >>123 (OP), >>123 (You), >>>/fit/123
            final Pattern quotePattern = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$");
            for (String line : lines) {
                // do not include post no from quoted post
                if (!quotePattern.matcher(line).matches()) {
                    textQuoteResult.append(">").append(line).append("\n");
                }
            }
        }

        String insert = extraNewline + postQuote + textQuoteResult.toString();
        draft.comment = new StringBuilder(draft.comment).insert(draft.selectionStart, insert).toString();
        // Set the selection start to the new end
        draft.selectionEnd += insert.length();
        draft.selectionStart = draft.selectionEnd;
        callback.loadDraftIntoViews(draft);

        highlightQuotes();
    }

    @Override
    public void onFilePicked(String name, File file) {
        pickingFile = false;
        draft.file = file;
        draft.fileName = name;
        showPreview(name, file);
    }

    @Override
    public void onFilePickError(boolean canceled) {
        pickingFile = false;
        if (!canceled) {
            showToast(R.string.reply_file_open_failed, Toast.LENGTH_LONG);
        }
    }

    private void closeAll() {
        moreOpen = false;
        previewOpen = false;
        selectedQuote = -1;
        callback.openMessage(false, true, "", false);
        callback.setExpanded(false);
        callback.openSubject(false);
        callback.openNameOptions(false);
        callback.openFileName(false);
        callback.openSpoiler(false, false);
        callback.openPreview(false, null);
        callback.openPreviewMessage(false, null);
        callback.destroyCurrentAuthentication();
    }

    private void makeSubmitCall() {
        loadable.getSite().actions().post(draft, this);
        switchPage(Page.LOADING);
    }

    public void switchPage(Page page) {
        switchPage(page, true, true);
    }

    public void switchPage(Page page, boolean useV2NoJsCaptcha, boolean autoReply) {
        if (!useV2NoJsCaptcha || this.page != page) {
            this.page = page;
            switch (page) {
                case LOADING:
                    callback.setPage(Page.LOADING);
                    break;
                case INPUT:
                    callback.setPage(Page.INPUT);
                    break;
                case AUTHENTICATION:
                    SiteAuthentication authentication = loadable.site.actions().postAuthenticate();

                    // cleanup resources tied to the new captcha layout/presenter
                    callback.destroyCurrentAuthentication();
                    callback.initializeAuthentication(loadable.site, authentication, this, useV2NoJsCaptcha, autoReply);
                    callback.setPage(Page.AUTHENTICATION);
                    break;
            }
        }
    }

    private void highlightQuotes() {
        Matcher matcher = QUOTE_PATTERN.matcher(draft.comment);

        // Find all occurrences of >>\d+ with start and end between selectionStart
        int no = -1;
        while (matcher.find()) {
            if (matcher.start() <= draft.selectionStart && matcher.end() >= draft.selectionStart - 1) {
                String quote = matcher.group().substring(2);
                try {
                    no = Integer.parseInt(quote);
                    break;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Allow no = -1 removing the highlight
        if (no != selectedQuote) {
            selectedQuote = no;
            callback.highlightPostNo(no);
        }
    }

    private void showPreview(String name, File file) {
        callback.openPreview(true, file);
        if (moreOpen) {
            callback.openFileName(true);
            if (board.spoilers) {
                callback.openSpoiler(true, false);
            }
        }
        callback.setFileName(name);
        previewOpen = true;

        boolean probablyWebm = file.getName().endsWith(".webm");
        int maxSize = probablyWebm ? board.maxWebmSize : board.maxFileSize;
        //if the max size is undefined for the board, ignore this message
        if (file.length() > maxSize && maxSize != -1) {
            String fileSize = getReadableFileSize(file.length());
            String maxSizeString = getReadableFileSize(maxSize);

            int stringResId = probablyWebm ? R.string.reply_webm_too_big : R.string.reply_file_too_big;

            callback.openPreviewMessage(true, getString(stringResId, fileSize, maxSizeString));
        } else {
            callback.openPreviewMessage(false, null);
        }
    }

    /**
     * Applies the new file and filename if they have been changed. They may change when user
     * re-encodes the picked image file (they may want to scale it down/remove metadata/change quality etc.)
     */
    public void onImageOptionsApplied(Reply reply) {
        draft.file = reply.file;
        draft.fileName = reply.fileName;
        showPreview(draft.fileName, draft.file);
    }

    public boolean isAttachedFileSupportedForReencoding() {
        if (draft == null) {
            return false;
        }

        return BitmapUtils.isFileSupportedForReencoding(draft.file);
    }

    public interface ReplyPresenterCallback {
        void loadViewsIntoDraft(Reply draft);

        void loadDraftIntoViews(Reply draft);

        void setPage(Page page);

        void initializeAuthentication(
                Site site,
                SiteAuthentication authentication,
                AuthenticationLayoutCallback callback,
                boolean useV2NoJsCaptcha,
                boolean autoReply
        );

        void resetAuthentication();

        void openMessage(boolean open, boolean animate, String message, boolean autoHide);

        void onPosted();

        void setCommentHint(String hint);

        void showCommentCounter(boolean show);

        void setExpanded(boolean expanded);

        void openNameOptions(boolean open);

        void openSubject(boolean open);

        void openFileName(boolean open);

        void setFileName(String fileName);

        void updateCommentCount(int count, int maxCount, boolean over);

        void openPreview(boolean show, File previewFile);

        void openPreviewMessage(boolean show, String message);

        void openSpoiler(boolean show, boolean checked);

        void highlightPostNo(int no);

        void showThread(Loadable loadable);

        ImagePickDelegate getImagePickDelegate();

        ChanThread getThread();

        void focusComment();

        void onUploadingProgress(int percent);

        void onFallbackToV1CaptchaView(boolean autoReply);

        void destroyCurrentAuthentication();
    }
}
