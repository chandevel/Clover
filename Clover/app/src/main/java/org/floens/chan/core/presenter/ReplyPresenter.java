/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.presenter;

import android.text.TextUtils;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.http.ReplyHttpCall;
import org.floens.chan.core.http.ReplyManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.Reply;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.core.pool.LoadablePool;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.layout.CaptchaCallback;
import org.floens.chan.ui.layout.CaptchaLayoutInterface;

import java.io.File;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.floens.chan.utils.AndroidUtils.getReadableFileSize;
import static org.floens.chan.utils.AndroidUtils.getRes;
import static org.floens.chan.utils.AndroidUtils.getString;

public class ReplyPresenter implements ReplyManager.HttpCallback<ReplyHttpCall>, CaptchaCallback, ImagePickDelegate.ImagePickCallback {
    public enum Page {
        INPUT,
        CAPTCHA,
        LOADING
    }

    private static final Pattern QUOTE_PATTERN = Pattern.compile(">>\\d+");
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ReplyPresenterCallback callback;

    private ReplyManager replyManager;
    private BoardManager boardManager;
    private WatchManager watchManager;
    private DatabaseManager databaseManager;

    private Loadable loadable;
    private Board board;
    private Reply draft;

    private Page page = Page.INPUT;
    private boolean moreOpen;
    private boolean previewOpen;
    private boolean pickingFile;
    private boolean captchaInited;
    private int selectedQuote = -1;

    public ReplyPresenter(ReplyPresenterCallback callback) {
        this.callback = callback;
        replyManager = Chan.getReplyManager();
        boardManager = Chan.getBoardManager();
        watchManager = Chan.getWatchManager();
        databaseManager = Chan.getDatabaseManager();
    }

    public void bindLoadable(Loadable loadable) {
        if (this.loadable != null) {
            unbindLoadable();
        }
        this.loadable = loadable;

        Board board = boardManager.getBoardByValue(loadable.board);
        if (board != null) {
            this.board = board;
        }

        draft = replyManager.getReply(loadable);

        if (TextUtils.isEmpty(draft.name)) {
            draft.name = ChanSettings.postDefaultName.get();
        }

        callback.loadDraftIntoViews(draft);
        callback.updateCommentCount(0, board.maxCommentChars, false);
        callback.setCommentHint(getString(loadable.isThreadMode() ? R.string.reply_comment_thread : R.string.reply_comment_board));

        if (draft.file != null) {
            showPreview(draft.fileName, draft.file);
        }

        if (captchaInited) {
            callback.resetCaptcha();
        }

        switchPage(Page.INPUT, false);
    }

    public void unbindLoadable() {
        draft.file = null;
        draft.fileName = "";
        callback.loadViewsIntoDraft(draft);
        replyManager.putReply(loadable, draft);

        loadable = null;
        board = null;
        closeAll();
    }

    public boolean onBack() {
        if (page == Page.LOADING) {
            return true;
        } else if (page == Page.CAPTCHA) {
            switchPage(Page.INPUT, true);
            return true;
        } else if (moreOpen) {
            onMoreClicked();
            return true;
        }
        return false;
    }

    public void onMoreClicked() {
        moreOpen = !moreOpen;
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

    public void onAttachClicked() {
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
                callback.getImagePickDelegate().pick(this);
                pickingFile = true;
            }
        }
    }

    public void onSubmitClicked() {
        callback.loadViewsIntoDraft(draft);
        draft.board = loadable.board;
        draft.resto = loadable.isThreadMode() ? loadable.no : -1;

        if (ChanSettings.passLoggedIn()) {
            draft.usePass = true;
            draft.passId = ChanSettings.passId.get();
        } else {
            draft.usePass = false;
            draft.passId = null;
        }

        draft.spoilerImage = draft.spoilerImage && board.spoilers;

        draft.captchaResponse = null;
        if (draft.usePass) {
            makeSubmitCall();
        } else {
            switchPage(Page.CAPTCHA, true);
        }
    }

    @Override
    public void onHttpSuccess(ReplyHttpCall replyCall) {
        if (replyCall.posted) {
            if (ChanSettings.postPinThread.get() && loadable.isThreadMode()) {
                ChanThread thread = callback.getThread();
                if (thread != null) {
                    watchManager.addPin(loadable, thread.op);
                }
            }

            databaseManager.saveReply(new SavedReply(loadable.board, replyCall.postNo, replyCall.password));

            switchPage(Page.INPUT, false);
            closeAll();
            highlightQuotes();
            String name = draft.name;
            draft = new Reply();
            draft.name = name;
            replyManager.putReply(loadable, draft);
            callback.loadDraftIntoViews(draft);
            callback.onPosted();

            if (!loadable.isThreadMode()) {
                callback.showThread(LoadablePool.getInstance().obtain(new Loadable(loadable.board, replyCall.postNo)));
            }
        } else {
            if (replyCall.errorMessage == null) {
                replyCall.errorMessage = getString(R.string.reply_error);
            }

            switchPage(Page.INPUT, true);
            callback.openMessage(true, false, replyCall.errorMessage, true);
        }
    }

    @Override
    public void onHttpFail(ReplyHttpCall httpPost) {
        switchPage(Page.INPUT, true);
        callback.openMessage(true, false, getString(R.string.reply_error), true);
    }

    @Override
    public void captchaLoaded(CaptchaLayoutInterface captchaLayout) {
    }

    @Override
    public void captchaEntered(CaptchaLayoutInterface captchaLayout, String challenge, String response) {
        draft.captchaChallenge = challenge;
        draft.captchaResponse = response;
        captchaLayout.reset();
        makeSubmitCall();
    }

    public void onCommentTextChanged(CharSequence text) {
        int length = text.toString().getBytes(UTF_8).length;
        callback.updateCommentCount(length, board.maxCommentChars, length > board.maxCommentChars);
    }

    public void onSelectionChanged() {
        callback.loadViewsIntoDraft(draft);
        highlightQuotes();
    }

    public void quote(Post post, boolean withText) {
        callback.loadViewsIntoDraft(draft);

        String textToInsert = "";
        if (draft.selection - 1 >= 0 && draft.selection - 1 < draft.comment.length() && draft.comment.charAt(draft.selection - 1) != '\n') {
            textToInsert += "\n";
        }

        textToInsert += ">>" + post.no + "\n";

        if (withText) {
            String[] lines = post.comment.toString().split("\n+");
            for (String line : lines) {
                textToInsert += ">" + line + "\n";
            }
        }

        draft.comment = new StringBuilder(draft.comment).insert(draft.selection, textToInsert).toString();

        draft.selection += textToInsert.length();

        callback.loadDraftIntoViews(draft);

        highlightQuotes();
    }

    @Override
    public void onFilePickLoading() {
        callback.onFilePickLoading();
    }

    @Override
    public void onFilePicked(String name, File file) {
        pickingFile = false;
        draft.file = file;
        draft.fileName = name;
        showPreview(name, file);
    }

    @Override
    public void onFilePickError(boolean cancelled) {
        pickingFile = false;
        if (!cancelled) {
            callback.onFilePickError();
        }
    }

    private void closeAll() {
        moreOpen = false;
        previewOpen = false;
        selectedQuote = -1;
        callback.openMessage(false, true, "", false);
        callback.openSubject(false);
        callback.openNameOptions(false);
        callback.openFileName(false);
        callback.openSpoiler(false, false);
        callback.openPreview(false, null);
        callback.openPreviewMessage(false, null);
    }

    private void makeSubmitCall() {
        replyManager.makeHttpCall(new ReplyHttpCall(draft), this);
        switchPage(Page.LOADING, true);
    }

    private void switchPage(Page page, boolean animate) {
        if (this.page != page) {
            this.page = page;
            switch (page) {
                case LOADING:
                    callback.setPage(Page.LOADING, true);
                    break;
                case INPUT:
                    callback.setPage(Page.INPUT, animate);
                    break;
                case CAPTCHA:
                    callback.setPage(Page.CAPTCHA, true);

                    if (!captchaInited) {
                        captchaInited = true;
                        String baseUrl = loadable.isThreadMode() ?
                                ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no) :
                                ChanUrls.getBoardUrlDesktop(loadable.board);
                        callback.initCaptcha(baseUrl, ChanUrls.getCaptchaSiteKey(), this);
                    }
                    break;
            }
        }
    }

    private void highlightQuotes() {
        Matcher matcher = QUOTE_PATTERN.matcher(draft.comment);

        // Find all occurrences of >>\d+ with start and end between selection
        int no = -1;
        while (matcher.find()) {
            if (matcher.start() <= draft.selection && matcher.end() >= draft.selection - 1) {
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
        if (file.length() > maxSize) {
            String fileSize = getReadableFileSize(file.length(), false);
            String maxSizeString = getReadableFileSize(maxSize, false);
            String text = getRes().getString(probablyWebm ? R.string.reply_webm_too_big : R.string.reply_file_too_big, fileSize, maxSizeString);
            callback.openPreviewMessage(true, text);
        } else {
            callback.openPreviewMessage(false, null);
        }
    }

    public interface ReplyPresenterCallback {
        void loadViewsIntoDraft(Reply draft);

        void loadDraftIntoViews(Reply draft);

        void setPage(Page page, boolean animate);

        void initCaptcha(String baseUrl, String siteKey, CaptchaCallback callback);

        void resetCaptcha();

        void openMessage(boolean open, boolean animate, String message, boolean autoHide);

        void onPosted();

        void setCommentHint(String hint);

        void openNameOptions(boolean open);

        void openSubject(boolean open);

        void openFileName(boolean open);

        void setFileName(String fileName);

        void updateCommentCount(int count, int maxCount, boolean over);

        void openPreview(boolean show, File previewFile);

        void openPreviewMessage(boolean show, String message);

        void openSpoiler(boolean show, boolean checked);

        void onFilePickLoading();

        void onFilePickError();

        void highlightPostNo(int no);

        void showThread(Loadable loadable);

        ImagePickDelegate getImagePickDelegate();

        ChanThread getThread();
    }
}
