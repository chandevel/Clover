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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.exifinterface.media.ExifInterface;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.repository.LastReplyRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder.CaptchaToken;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.common.io.Files;

import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.site.Site.BoardFeature.FORCED_ANONYMOUS;
import static com.github.adamantcheese.chan.core.site.Site.BoardFeature.POSTING_IMAGE;
import static com.github.adamantcheese.chan.core.site.Site.BoardFeature.POSTING_SPOILER;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ReplyPresenter
        implements AuthenticationLayoutCallback, ImagePickDelegate.ImagePickCallback, SiteActions.PostListener {

    public enum Page {
        INPUT,
        AUTHENTICATION,
        LOADING
    }

    private final Context context;
    private static final Pattern QUOTE_PATTERN = Pattern.compile(">>\\d+");

    private final ReplyPresenterCallback callback;
    private Loadable loadable;
    private Reply draft;

    private Page page = Page.INPUT;
    private boolean moreOpen;
    private boolean previewOpen;
    private boolean pickingFile;
    private int selectedQuote = -1;

    public ReplyPresenter(Context context, ReplyPresenterCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void bindLoadable(Loadable loadable) {
        unbindLoadable();
        this.loadable = loadable;
        this.draft = loadable.draft;

        callback.loadDraftIntoViews(loadable.draft);
        int length = draft.comment.getBytes(UTF_8).length;
        callback.updateCommentCount(length, loadable.board.maxCommentChars, length > loadable.board.maxCommentChars);
        callback.setCommentHint(loadable.isThreadMode());
        callback.showCommentCounter(loadable.board.maxCommentChars > 0);
        callback.enableImageAttach(canPostImages());
        callback.enableName(canPostName());

        switchPage(Page.INPUT);

        callback.openFlag(ChanSettings.alwaysShowPostOptions.get() && (loadable.board.countryFlags
                || !loadable.board.boardFlags.isEmpty()));
        callback.openPostOptions(ChanSettings.alwaysShowPostOptions.get());
        callback.openSubject(ChanSettings.alwaysShowPostOptions.get() && loadable.isCatalogMode());
    }

    public void unbindLoadable() {
        if (loadable != null) {
            callback.loadViewsIntoDraft(draft);
            // null out the file/name; only one loadable should have a picked file at a time
            draft.file = null;
            draft.fileName = "";
            loadable = null;
            draft = null;
        }

        closeAll();
    }

    public boolean canPostImages() {
        return loadable.site.boardFeature(POSTING_IMAGE, loadable.board);
    }

    public boolean canPostSpoileredImages() {
        return loadable.site.boardFeature(POSTING_SPOILER, loadable.board);
    }

    public boolean canPostName() {
        return !loadable.site.boardFeature(FORCED_ANONYMOUS, loadable.board);
    }

    public Map<String, String> getBoardFlags() {
        return loadable.board.boardFlags;
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
        callback.openPostOptions(moreOpen || ChanSettings.alwaysShowPostOptions.get());
        if (loadable.isCatalogMode()) {
            callback.openSubject(moreOpen || ChanSettings.alwaysShowPostOptions.get());
        }
        if (previewOpen) {
            callback.openPreview(draft.file != null, draft.file);
        }
        callback.openCommentQuoteButton(moreOpen);
        if (loadable.board.spoilers) {
            callback.openCommentSpoilerButton(moreOpen);
        }
        if (loadable.board.codeTags) {
            callback.openCommentCodeButton(moreOpen);
        }
        if (loadable.board.mathTags) {
            callback.openCommentEqnButton(moreOpen);
            callback.openCommentMathButton(moreOpen);
        }
        if (loadable.site instanceof Chan4 && (loadable.boardCode.equals("jp") || loadable.boardCode.equals("vip"))) {
            callback.openCommentSJISButton(moreOpen);
        }
        if (loadable.board.countryFlags || !loadable.board.boardFlags.isEmpty()) {
            callback.openFlag(moreOpen || ChanSettings.alwaysShowPostOptions.get());
        }
    }

    public boolean isExpanded() {
        return moreOpen;
    }

    public void onAttachClicked(boolean longPressed) {
        if (draft == null) return;
        if (!pickingFile) {
            if (previewOpen) {
                callback.openPreview(false, null);
                draft.file = null;
                draft.fileName = "";
                previewOpen = false;
            } else {
                pickingFile = true;
                callback.getImagePickDelegate().pick(this, longPressed);
            }
        }
    }

    public void onSubmitClicked(boolean longClicked) {
        long timeLeft = LastReplyRepository.getTimeUntilDraftPostable(loadable);

        boolean authenticateOnly = timeLeft > 0L && !longClicked;
        if (!onPrepareToSubmit(authenticateOnly)) {
            return;
        }

        submitOrAuthenticate(authenticateOnly);
    }

    private void submitOrAuthenticate(boolean authenticateOnly) {
        if (loadable.site.actions().postRequiresAuthentication()) {
            switchPage(Page.AUTHENTICATION, true, !authenticateOnly);
        } else {
            makeSubmitCall();
        }
    }

    private boolean onPrepareToSubmit(boolean isAuthenticateOnly) {
        if (draft == null) return false;
        callback.loadViewsIntoDraft(draft);

        if (!isAuthenticateOnly && (draft.comment.trim().isEmpty() && draft.file == null)) {
            callback.openMessage(getString(R.string.reply_comment_empty));
            return false;
        }

        draft.spoilerImage = draft.spoilerImage && loadable.board.spoilers;
        draft.token = null;

        return true;
    }

    // Do NOT use the loadable from ReplyPresenter in this method, as it is not guaranteed to match the loadable associated with the reply object
    // Instead use the response's reply to get the loadable or generate a fresh loadable for a new thread
    @Override
    public void onSuccess(ReplyResponse replyResponse) {
        if (replyResponse.posted) {
            LastReplyRepository.putLastReply(replyResponse.originatingLoadable);
            Loadable originatingLoadable = replyResponse.originatingLoadable;
            Loadable newThreadLoadable = Loadable.forThread(originatingLoadable.board,
                    replyResponse.threadNo == 0 ? replyResponse.postNo : replyResponse.threadNo,
                    "Title will update shortly…"
            );

            if (ChanSettings.postPinThread.get()) {
                WatchManager watchManager = instance(WatchManager.class);
                if (originatingLoadable.isThreadMode()) {
                    ChanThread thread = callback.getThread();
                    //ensure that the thread's loadable matches the one from the original reply, in case the user navigated away
                    if (thread != null && thread.getLoadable() == originatingLoadable) {
                        // we know the OP, we're in the thread
                        if (thread.getOp().image() != null) {
                            originatingLoadable.thumbnailUrl = thread.getOp().image().getThumbnailUrl();
                        }
                    }
                    watchManager.createPin(originatingLoadable);
                } else {
                    // this is a new thread, we can construct an OP for initial display
                    Post fakeOP = new Post.Builder().subject(originatingLoadable.draft.subject)
                            .comment(originatingLoadable.draft.comment)
                            .board(originatingLoadable.board)
                            .no(replyResponse.postNo)
                            .opId(replyResponse.postNo)
                            .setUnixTimestampSeconds(System.currentTimeMillis() / 1000L)
                            .build();
                    newThreadLoadable.title = PostHelper.getTitle(fakeOP, newThreadLoadable);
                    watchManager.createPin(newThreadLoadable);
                }
            }

            SavedReply savedReply = SavedReply.fromBoardNoPassword(originatingLoadable.board,
                    replyResponse.postNo,
                    originatingLoadable.draft.password
            );
            DatabaseUtils.runTaskAsync(instance(DatabaseSavedReplyManager.class).saveReply(savedReply));

            highlightQuotes();

            originatingLoadable.draft.reset(true);
            callback.loadDraftIntoViews(originatingLoadable.draft);
            callback.onPosted(originatingLoadable.isCatalogMode(), newThreadLoadable);
        } else if (replyResponse.requireAuthentication) {
            switchPage(Page.AUTHENTICATION);
        } else {
            SpannableStringBuilder errorMessage = new SpannableStringBuilder(getString(R.string.reply_error));
            int prefixLen = errorMessage.length();
            if (replyResponse.errorMessage != null) {
                SpannableStringBuilder error =
                        new SpannableStringBuilder(HtmlCompat.fromHtml(replyResponse.errorMessage,
                                HtmlCompat.FROM_HTML_MODE_LEGACY
                        ));
                // update colors for url spans; unfortunately that means re-making them
                URLSpan[] spans = error.getSpans(0, error.length(), URLSpan.class);
                for (URLSpan s : spans) {
                    String url = s.getURL();
                    error.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            openLinkInBrowser(context, url);
                        }

                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {
                            ds.setColor(getColor(R.color.md_red_500));
                            ds.setUnderlineText(true);
                            ds.setFakeBoldText(true);
                        }
                    }, error.getSpanStart(s), error.getSpanEnd(s), 0);
                    error.removeSpan(s);
                }
                errorMessage.clear();
                errorMessage.append(getString(R.string.reply_error_message, error));
                prefixLen += 1;
            }
            errorMessage.setSpan(new StyleSpan(Typeface.BOLD), 0, prefixLen, 0);

            Logger.e(this, "onPostComplete error", errorMessage);
            switchPage(Page.INPUT);
            callback.openMessage(errorMessage);
        }
    }

    @Override
    public void onUploadProgress(int percent) {
        //called on a background thread!
        BackgroundUtils.runOnMainThread(() -> callback.onUploadingProgress(percent));
    }

    @Override
    public void onFailure(Exception exception) {
        Logger.e(this, "onPostError", exception);

        switchPage(Page.INPUT);

        String errorMessage = getString(R.string.reply_error);
        if (exception != null) {
            String message = exception.getMessage();
            if (message != null) {
                errorMessage = getString(R.string.reply_error_message, message);
            }
        }

        callback.openMessage(errorMessage);
    }

    @Override
    public void onAuthenticationComplete(
            AuthenticationLayoutInterface authenticationLayout, CaptchaToken token, boolean autoReply
    ) {
        if (draft == null) {
            switchPage(Page.INPUT);
            return;
        }

        draft.token = token;

        long timeLeft = LastReplyRepository.getTimeUntilDraftPostable(loadable);
        if (timeLeft > 0L && !autoReply) {
            String errorMessage = getString(R.string.reply_error_message_timer, timeLeft);
            switchPage(Page.INPUT);
            callback.openMessage(errorMessage);
            return;
        }

        if (autoReply) {
            makeSubmitCall();
        } else {
            switchPage(Page.INPUT);
        }
    }

    @Override
    public void onAuthenticationFailed(Throwable error) {
        callback.showAuthenticationFailedError(error);
        switchPage(Page.INPUT);
    }

    @Override
    public void onFallbackToV1CaptchaView(boolean autoReply) {
        callback.onFallbackToV1CaptchaView(autoReply);
    }

    public void onCommentTextChanged(CharSequence text) {
        int length = text.toString().getBytes(UTF_8).length;
        callback.updateCommentCount(length, loadable.board.maxCommentChars, length > loadable.board.maxCommentChars);
    }

    public void onTextChanged() {
        callback.loadViewsIntoDraft(draft);
    }

    public void onSelectionChanged() {
        highlightQuotes();
    }

    public void filenameNewClicked() {
        if (draft == null) return;
        draft.fileName = System.currentTimeMillis() + "." + Files.getFileExtension(draft.fileName);
        callback.loadDraftIntoViews(draft);
    }

    public void quote(Post post, boolean withText) {
        handleQuote(post, withText ? post.comment.toString() : null);
    }

    public void quote(Post post, CharSequence text) {
        handleQuote(post, text.toString());
    }

    private void handleQuote(Post post, String textQuote) {
        if (draft == null) return;
        callback.loadViewsIntoDraft(draft);

        StringBuilder insert = new StringBuilder();
        int selectStart = callback.getSelectionStart();
        if (selectStart - 1 >= 0 && selectStart - 1 < draft.comment.length()
                && draft.comment.charAt(selectStart - 1) != '\n') {
            insert.append('\n');
        }

        if (post != null && !draft.comment.contains(">>" + post.no)) {
            insert.append(">>").append(post.no).append("\n");
        }

        if (textQuote != null) {
            textQuote = textQuote.replace(CommentParser.EXIF_INFO_STRING, "").trim();
            String[] lines = textQuote.split("\n+");
            // matches for >>123, >>123 (text), >>>/fit/123
            final Pattern quotePattern = Pattern.compile("^>>(>/[a-z0-9]+/)?\\d+.*$");
            for (String line : lines) {
                // do not include post no from quoted post
                if (!quotePattern.matcher(line).matches()) {
                    insert.append(">").append(line).append("\n");
                }
            }
        }

        draft.comment = new StringBuilder(draft.comment).insert(selectStart, insert).toString();

        callback.loadDraftIntoViews(draft);
        callback.adjustSelection(selectStart, insert.length());

        highlightQuotes();
    }

    @Override
    public void onFilePicked(String name, File file) {
        pickingFile = false;
        if (draft == null) return;
        draft.file = file;
        draft.fileName = name;
        if (ChanSettings.alwaysSetNewFilename.get()) {
            filenameNewClicked();
        }
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                callback.openMessage(getString(R.string.file_has_orientation_exif_data));
            }
        } catch (Exception ignored) {
        }
        if (BitmapUtils.getImageFormat(file) == Bitmap.CompressFormat.WEBP) {
            callback.openMessage(getString(R.string.file_type_may_not_be_supported));
        }
        showPreview(draft.fileName, file);
    }

    @Override
    public void onFilePickError(boolean canceled) {
        pickingFile = false;
        if (!canceled) {
            showToast(context, R.string.reply_file_open_failed, Toast.LENGTH_LONG);
        }
    }

    public void closeAll() {
        moreOpen = false;
        previewOpen = false;
        selectedQuote = -1;
        callback.openMessage(null);
        callback.setExpanded(false);
        callback.openSubject(loadable != null && ChanSettings.alwaysShowPostOptions.get() && loadable.isCatalogMode());
        callback.openFlag(loadable != null && (loadable.board.countryFlags || !loadable.board.boardFlags.isEmpty())
                && ChanSettings.alwaysShowPostOptions.get());
        callback.openCommentQuoteButton(false);
        callback.openCommentSpoilerButton(false);
        callback.openCommentCodeButton(false);
        callback.openCommentEqnButton(false);
        callback.openCommentMathButton(false);
        callback.openCommentSJISButton(false);
        callback.openPostOptions(ChanSettings.alwaysShowPostOptions.get());
        callback.openPreview(false, null);
        callback.openPreviewMessage(false, null);
        callback.destroyCurrentAuthentication();
    }

    private void makeSubmitCall() {
        loadable.site.actions().post(loadable, this);
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
                case INPUT:
                    callback.setPage(page);
                    break;
                case AUTHENTICATION:
                    callback.setPage(Page.AUTHENTICATION);
                    SiteAuthentication authentication = loadable.site.actions().postAuthenticate(loadable);

                    // cleanup resources tied to the new captcha layout/presenter
                    callback.destroyCurrentAuthentication();

                    try {
                        // If the user doesn't have WebView installed it will throw an error
                        callback.initializeAuthentication(loadable,
                                authentication,
                                this,
                                useV2NoJsCaptcha,
                                autoReply
                        );
                    } catch (Throwable error) {
                        onAuthenticationFailed(error);
                    }

                    break;
            }
        }
    }

    public Page getPage() {
        return page;
    }

    private void highlightQuotes() {
        if (draft == null) return;
        Matcher matcher = QUOTE_PATTERN.matcher(draft.comment);

        // Find all occurrences of >>\d+ with start and end between selectionStart
        int no = -1;
        while (matcher.find()) {
            int selectStart = callback.getSelectionStart();
            if (matcher.start() <= selectStart && matcher.end() >= selectStart - 1) {
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
        callback.setFileName(name);
        previewOpen = true;

        boolean probablyWebm = "webm".equalsIgnoreCase(Files.getFileExtension(name));
        int maxSize = probablyWebm ? loadable.board.maxWebmSize : loadable.board.maxFileSize;
        //if the max size is undefined for the board, ignore this message
        if (file != null && file.length() > maxSize && maxSize != -1) {
            String fileSize = getReadableFileSize(file.length());
            int stringResId = probablyWebm ? R.string.reply_webm_too_big : R.string.reply_file_too_big;
            callback.openPreviewMessage(true, getString(stringResId, fileSize, getReadableFileSize(maxSize)));
        } else {
            callback.openPreviewMessage(false, null);
        }
    }

    public void onImageOptionsApplied() {
        if (draft == null) return;
        showPreview(draft.fileName, draft.file);
    }

    public boolean isAttachedFileSupportedForReencoding() {
        if (draft == null || draft.file == null) {
            return false;
        }

        return BitmapUtils.isFileSupportedForReencoding(draft.file);
    }

    public interface ReplyPresenterCallback {
        void loadViewsIntoDraft(Reply draft);

        void loadDraftIntoViews(Reply draft);

        int getSelectionStart();

        void adjustSelection(int start, int amount);

        void setPage(Page page);

        void initializeAuthentication(
                Loadable loadable,
                SiteAuthentication authentication,
                AuthenticationLayoutCallback callback,
                boolean useV2NoJsCaptcha,
                boolean autoReply
        );

        void openMessage(CharSequence message);

        void onPosted(boolean newThread, Loadable newThreadLoadable);

        void setCommentHint(boolean isThreadMode);

        void showCommentCounter(boolean show);

        void setExpanded(boolean expanded);

        void openPostOptions(boolean open);

        void openSubject(boolean open);

        void openFlag(boolean open);

        void openCommentQuoteButton(boolean open);

        void openCommentSpoilerButton(boolean open);

        void openCommentCodeButton(boolean open);

        void openCommentEqnButton(boolean open);

        void openCommentMathButton(boolean open);

        void openCommentSJISButton(boolean open);

        void setFileName(String fileName);

        void updateCommentCount(int count, int maxCount, boolean over);

        void openPreview(boolean show, File previewFile);

        void openPreviewMessage(boolean show, String message);

        void highlightPostNo(int no);

        ImagePickDelegate getImagePickDelegate();

        ChanThread getThread();

        void focusComment();

        void onUploadingProgress(int percent);

        void onFallbackToV1CaptchaView(boolean autoReply);

        void destroyCurrentAuthentication();

        void showAuthenticationFailedError(Throwable error);

        void enableImageAttach(boolean canAttach);

        void enableName(boolean canName);
    }
}
