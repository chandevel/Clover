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
package com.github.adamantcheese.chan.ui.layout;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AndroidRuntimeException;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.captcha.CaptchaHolder;
import com.github.adamantcheese.chan.ui.captcha.CaptchaLayout;
import com.github.adamantcheese.chan.ui.captcha.GenericWebViewAuthenticationLayout;
import com.github.adamantcheese.chan.ui.captcha.LegacyCaptchaLayout;
import com.github.adamantcheese.chan.ui.captcha.v1.CaptchaNojsLayoutV1;
import com.github.adamantcheese.chan.ui.captcha.v2.CaptchaNoJsLayoutV2;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.theme.DropdownArrowDrawable;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.LoadView;
import com.github.adamantcheese.chan.ui.view.SelectionListeningEditText;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.ImageDecoder;
import com.github.adamantcheese.chan.utils.Logger;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;

import javax.inject.Inject;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.requestViewAndKeyboardFocus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ReplyLayout
        extends LoadView
        implements View.OnClickListener, ReplyPresenter.ReplyPresenterCallback, TextWatcher,
                   ImageDecoder.ImageDecoderCallback, SelectionListeningEditText.SelectionChangedListener,
                   CaptchaHolder.CaptchaValidationListener {
    private static final String TAG = "ReplyLayout";

    @Inject
    ReplyPresenter presenter;
    @Inject
    CaptchaHolder captchaHolder;

    private ReplyLayoutCallback callback;

    private AuthenticationLayoutInterface authenticationLayout;

    private boolean blockSelectionChange = false;

    // Progress view (when sending request to the server)
    private View progressLayout;
    private TextView currentProgress;

    // Reply views:
    private View replyInputLayout;
    private TextView message;
    private EditText name;
    private EditText subject;
    private EditText options;
    private EditText fileName;
    private LinearLayout nameOptions;
    private SelectionListeningEditText comment;
    private TextView commentCounter;
    private CheckBox spoiler;
    private LinearLayout previewHolder;
    private ImageView preview;
    private TextView previewMessage;
    private ImageView attach;
    private ConstraintLayout captcha;
    private TextView validCaptchasCount;
    private ImageView more;
    private ImageView submit;
    private DropdownArrowDrawable moreDropdown;
    @Nullable
    private HintPopup hintPopup = null;

    // Captcha views:
    private FrameLayout captchaContainer;
    private ImageView captchaHardReset;

    private Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            message.setVisibility(GONE);
        }
    };

    public ReplyLayout(Context context) {
        this(context, null);
    }

    public ReplyLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReplyLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }

        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        // Inflate reply input
        replyInputLayout = AndroidUtils.inflate(getContext(), R.layout.layout_reply_input, this, false);
        message = replyInputLayout.findViewById(R.id.message);
        name = replyInputLayout.findViewById(R.id.name);
        subject = replyInputLayout.findViewById(R.id.subject);
        options = replyInputLayout.findViewById(R.id.options);
        fileName = replyInputLayout.findViewById(R.id.file_name);
        nameOptions = replyInputLayout.findViewById(R.id.name_options);
        comment = replyInputLayout.findViewById(R.id.comment);
        commentCounter = replyInputLayout.findViewById(R.id.comment_counter);
        spoiler = replyInputLayout.findViewById(R.id.spoiler);
        preview = replyInputLayout.findViewById(R.id.preview);
        previewHolder = replyInputLayout.findViewById(R.id.preview_holder);
        previewMessage = replyInputLayout.findViewById(R.id.preview_message);
        attach = replyInputLayout.findViewById(R.id.attach);
        captcha = replyInputLayout.findViewById(R.id.captcha_container);
        validCaptchasCount = replyInputLayout.findViewById(R.id.valid_captchas_count);
        more = replyInputLayout.findViewById(R.id.more);
        submit = replyInputLayout.findViewById(R.id.submit);

        progressLayout = AndroidUtils.inflate(getContext(), R.layout.layout_reply_progress, this, false);
        currentProgress = progressLayout.findViewById(R.id.current_progress);

        spoiler.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));
        spoiler.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));

        // Setup reply layout views
        fileName.setOnLongClickListener(v -> presenter.fileNameLongClicked());

        comment.addTextChangedListener(this);
        comment.setSelectionChangedListener(this);
        comment.setOnFocusChangeListener((view, focused) -> {
            if (!focused) hideKeyboard(comment);
        });
        comment.setPlainTextPaste(true);
        setupCommentContextMenu();

        previewHolder.setOnClickListener(this);

        moreDropdown = new DropdownArrowDrawable(dp(16),
                dp(16),
                !ChanSettings.moveInputToBottom.get(),
                getAttrColor(getContext(), R.attr.dropdown_dark_color),
                getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)
        );
        more.setImageDrawable(moreDropdown);
        setRoundItemBackground(more);
        more.setOnClickListener(this);

        ThemeHelper.getTheme().imageDrawable.apply(attach);
        setRoundItemBackground(attach);
        attach.setOnClickListener(this);
        attach.setOnLongClickListener(v -> {
            presenter.onAttachClicked(true);
            return true;
        });

        ImageView captchaImage = replyInputLayout.findViewById(R.id.captcha);
        setRoundItemBackground(captchaImage);
        captcha.setOnClickListener(this);

        ThemeHelper.getTheme().sendDrawable.apply(submit);
        setRoundItemBackground(submit);
        submit.setOnClickListener(this);
        submit.setOnLongClickListener(v -> {
            presenter.onSubmitClicked(true);
            return true;
        });

        // Inflate captcha layout
        captchaContainer = (FrameLayout) AndroidUtils.inflate(getContext(), R.layout.layout_reply_captcha, this, false);
        captchaHardReset = captchaContainer.findViewById(R.id.reset);

        // Setup captcha layout views
        captchaContainer.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        ThemeHelper.getTheme().refreshDrawable.apply(captchaHardReset);
        setRoundItemBackground(captchaHardReset);
        captchaHardReset.setOnClickListener(this);

        setView(replyInputLayout);

        // Presenter
        presenter.create(this);
    }

    public void setCallback(ReplyLayoutCallback callback) {
        this.callback = callback;
    }

    public ReplyPresenter getPresenter() {
        return presenter;
    }

    public void onOpen(boolean open) {
        presenter.onOpen(open);
    }

    public void bindLoadable(Loadable loadable) {
        if (loadable.site.actions().postRequiresAuthentication()) {
            comment.setMinHeight(dp(144));
        } else {
            captcha.setVisibility(GONE);
        }
        presenter.bindLoadable(loadable);
        captchaHolder.setListener(this);
    }

    public void cleanup() {
        captchaHolder.removeListener();
        presenter.unbindLoadable();
        removeCallbacks(closeMessageRunnable);
    }

    public boolean onBack() {
        return presenter.onBack();
    }

    private void setWrappingMode(boolean matchParent) {
        LayoutParams params = (LayoutParams) getLayoutParams();
        params.width = MATCH_PARENT;
        params.height = matchParent ? MATCH_PARENT : WRAP_CONTENT;

        if (ChanSettings.moveInputToBottom.get()) {
            if (matchParent) {
                setPadding(0, ((ThreadListLayout) getParent()).toolbarHeight(), 0, 0);
                params.gravity = Gravity.TOP;
            } else {
                setPadding(0, 0, 0, 0);
                params.gravity = Gravity.BOTTOM;
            }
        }

        setLayoutParams(params);
    }

    @Override
    public void onClick(View v) {
        if (v == more) {
            presenter.onMoreClicked();
        } else if (v == attach) {
            presenter.onAttachClicked(false);
        } else if (v == captcha) {
            presenter.onAuthenticateCalled();
        } else if (v == submit) {
            presenter.onSubmitClicked(false);
        } else if (v == previewHolder) {
            callback.showImageReencodingWindow(presenter.isAttachedFileSupportedForReencoding());
        } else if (v == captchaHardReset) {
            if (authenticationLayout != null) {
                authenticationLayout.hardReset();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void initializeAuthentication(
            Site site,
            SiteAuthentication authentication,
            AuthenticationLayoutCallback callback,
            boolean useV2NoJsCaptcha,
            boolean autoReply
    ) {
        if (authenticationLayout == null) {
            switch (authentication.type) {
                case CAPTCHA1:
                    authenticationLayout = (LegacyCaptchaLayout) AndroidUtils.inflate(getContext(),
                            R.layout.layout_captcha_legacy,
                            captchaContainer,
                            false
                    );
                    break;
                case CAPTCHA2:
                    authenticationLayout = new CaptchaLayout(getContext());
                    break;
                case CAPTCHA2_NOJS:
                    if (useV2NoJsCaptcha) {
                        // new captcha window without webview
                        authenticationLayout = new CaptchaNoJsLayoutV2(getContext());
                    } else {
                        // default webview-based captcha view
                        authenticationLayout = new CaptchaNojsLayoutV1(getContext());
                    }

                    ImageView resetButton = captchaContainer.findViewById(R.id.reset);
                    if (resetButton != null) {
                        if (useV2NoJsCaptcha) {
                            // we don't need the default reset button because we have our own
                            resetButton.setVisibility(GONE);
                        } else {
                            // restore the button's visibility when using old v1 captcha view
                            resetButton.setVisibility(VISIBLE);
                        }
                    }

                    break;
                case GENERIC_WEBVIEW:
                    GenericWebViewAuthenticationLayout view = new GenericWebViewAuthenticationLayout(getContext());

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
                    view.setLayoutParams(params);

                    authenticationLayout = view;
                    break;
                case NONE:
                default:
                    throw new IllegalArgumentException();
            }

            captchaContainer.addView((View) authenticationLayout, 0);
        }

        if (!(authenticationLayout instanceof LegacyCaptchaLayout)) {
            hideKeyboard(this);
        }

        authenticationLayout.initialize(site, callback, autoReply);
        authenticationLayout.reset();
    }

    @Override
    public void setPage(ReplyPresenter.Page page) {
        Logger.d(TAG, "Switching to page " + page.name());
        switch (page) {
            case LOADING:
                setWrappingMode(false);
                setView(progressLayout);

                //reset progress to 0 upon uploading start
                currentProgress.setVisibility(INVISIBLE);
                break;
            case INPUT:
                setView(replyInputLayout);
                setWrappingMode(presenter.isExpanded());
                break;
            case AUTHENTICATION:
                setWrappingMode(true);
                setView(captchaContainer);
                captchaContainer.requestFocus(View.FOCUS_DOWN);
                break;
        }

        if (page != ReplyPresenter.Page.AUTHENTICATION) {
            destroyCurrentAuthentication();
        }
    }

    @Override
    public void resetAuthentication() {
        authenticationLayout.reset();
    }

    @Override
    public void destroyCurrentAuthentication() {
        if (authenticationLayout == null) {
            return;
        }

        // cleanup resources when switching from the new to the old captcha view
        if (authenticationLayout instanceof CaptchaNoJsLayoutV2) {
            ((CaptchaNoJsLayoutV2) authenticationLayout).onDestroy();
        }

        captchaContainer.removeView((View) authenticationLayout);
        authenticationLayout = null;
    }

    @Override
    public void showAuthenticationFailedError(Throwable error) {
        String message = getString(R.string.could_not_initialized_captcha, getReason(error));
        showToast(getContext(), message, Toast.LENGTH_LONG);
    }

    private String getReason(Throwable error) {
        if (error instanceof AndroidRuntimeException && error.getMessage() != null) {
            if (error.getMessage().contains("MissingWebViewPackageException")) {
                return getString(R.string.fail_reason_webview_is_not_installed);
            }

            // Fallthrough
        } else if (error instanceof Resources.NotFoundException) {
            return getString(R.string.fail_reason_some_part_of_webview_not_initialized, error.getMessage());
        }

        if (error.getMessage() != null) {
            return String.format("%s: %s", error.getClass().getSimpleName(), error.getMessage());
        }

        return error.getClass().getSimpleName();
    }

    @Override
    public void loadDraftIntoViews(Reply draft) {
        name.setText(draft.name);
        subject.setText(draft.subject);
        options.setText(draft.options);
        blockSelectionChange = true;
        comment.setText(draft.comment);
        comment.setSelection(draft.selectionStart, draft.selectionEnd);
        blockSelectionChange = false;
        fileName.setText(draft.fileName);
        spoiler.setChecked(draft.spoilerImage);
    }

    @Override
    public void loadViewsIntoDraft(Reply draft) {
        draft.name = name.getText().toString();
        draft.subject = subject.getText().toString();
        draft.options = options.getText().toString();
        draft.comment = comment.getText().toString();
        draft.selectionStart = comment.getSelectionStart();
        draft.selectionEnd = comment.getSelectionEnd();
        draft.fileName = fileName.getText().toString();
        draft.spoilerImage = spoiler.isChecked();
    }

    @Override
    public void openMessage(boolean open, boolean animate, String text, boolean autoHide) {
        removeCallbacks(closeMessageRunnable);
        message.setText(text);
        message.setVisibility(open ? VISIBLE : GONE);

        if (autoHide) {
            postDelayed(closeMessageRunnable, 5000);
        }
    }

    @Override
    public void onPosted() {
        showToast(getContext(), R.string.reply_success);
        callback.openReply(false);
        callback.requestNewPostLoad();
    }

    @Override
    public void setCommentHint(String hint) {
        comment.setHint(hint);
    }

    @Override
    public void showCommentCounter(boolean show) {
        commentCounter.setVisibility(show ? VISIBLE : GONE);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        if (!ChanSettings.moveInputToBottom.get()) {
            setPadding(0, ((ThreadListLayout) getParent()).toolbarHeight(), 0, 0);
            LayoutParams params = (LayoutParams) getLayoutParams();
            params.gravity = Gravity.TOP;
            setLayoutParams(params);
        } else if (ChanSettings.moveInputToBottom.get()) {
            setPadding(0, 0, 0, 0);
            LayoutParams params = (LayoutParams) getLayoutParams();
            params.gravity = Gravity.BOTTOM;
            setLayoutParams(params);
        }
    }

    @Override
    public void setExpanded(boolean expanded) {
        setWrappingMode(expanded);

        comment.setMaxLines(expanded ? 500 : 6);

        previewHolder.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, expanded ? dp(150) : dp(100)));

        float startRotation = ChanSettings.moveInputToBottom.get() ? 1f : 0f;
        float endRotation = ChanSettings.moveInputToBottom.get() ? 0f : 1f;
        ValueAnimator animator =
                ValueAnimator.ofFloat(expanded ? startRotation : endRotation, expanded ? endRotation : startRotation);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.setDuration(400);
        animator.addUpdateListener(animation -> moreDropdown.setRotation((float) animation.getAnimatedValue()));
        animator.start();
    }

    @Override
    public void openNameOptions(boolean open) {
        nameOptions.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openSubject(boolean open) {
        subject.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openFileName(boolean open) {
        fileName.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void setFileName(String name) {
        fileName.setText(name);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void updateCommentCount(int count, int maxCount, boolean over) {
        commentCounter.setText(count + "/" + maxCount);
        //noinspection ResourceAsColor
        commentCounter.setTextColor(over ? 0xffff0000 : getAttrColor(getContext(), R.attr.text_color_secondary));
    }

    public void focusComment() {
        //this is a hack to make sure text is selectable
        comment.setEnabled(false);
        comment.setEnabled(true);
        comment.post(() -> requestViewAndKeyboardFocus(comment));
    }

    @Override
    public void onFallbackToV1CaptchaView(boolean autoReply) {
        // fallback to v1 captcha window
        presenter.switchPage(ReplyPresenter.Page.AUTHENTICATION, false, autoReply);
    }

    @Override
    public void openPreview(boolean show, File previewFile) {
        previewHolder.setClickable(false);
        if (show) {
            ImageDecoder.decodeFileOnBackgroundThread(previewFile, dp(400), dp(300), this);
            ThemeHelper.getTheme().clearDrawable.apply(attach);
        } else {
            spoiler.setVisibility(GONE);
            previewHolder.setVisibility(GONE);
            previewMessage.setVisibility(GONE);
            callback.updatePadding();
            ThemeHelper.getTheme().imageDrawable.apply(attach);
        }
        // the delay is taken from LayoutTransition, as this class is set to automatically animate layout changes
        // only allow the preview to be clicked if it is fully visible
        postDelayed(() -> previewHolder.setClickable(true), 300);
    }

    @Override
    public void openPreviewMessage(boolean show, String message) {
        previewMessage.setVisibility(show ? VISIBLE : GONE);
        previewMessage.setText(message);
    }

    @Override
    public void openSpoiler(boolean show, boolean checked) {
        spoiler.setVisibility(show ? VISIBLE : GONE);
        spoiler.setChecked(checked);
    }

    @Override
    public void onImageBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
            previewHolder.setVisibility(VISIBLE);
            callback.updatePadding();

            showReencodeImageHint();
        } else {
            openPreviewMessage(true, getString(R.string.reply_no_preview));
        }
    }

    @Override
    public void highlightPostNo(int no) {
        callback.highlightPostNo(no);
    }

    @Override
    public void onSelectionChanged() {
        if (!blockSelectionChange) {
            presenter.onSelectionChanged();
        }
    }

    private void setupCommentContextMenu() {
        comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            private MenuItem quoteMenuItem;
            private MenuItem spoilerMenuItem;
            private MenuItem codeMenuItem;
            private MenuItem mathMenuItem;
            private MenuItem eqnMenuItem;
            private MenuItem sjisMenuItem;
            private boolean processed;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                if (callback.getThread() == null) return true;
                Loadable threadLoadable = callback.getThread().getLoadable();
                boolean is4chan = threadLoadable.board.site instanceof Chan4;
                //menu item cleanup, these aren't needed for this
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (menu.size() > 0) {
                        menu.removeItem(android.R.id.shareText);
                    }
                }
                //setup standard items
                // >greentext
                quoteMenuItem = menu.add(Menu.NONE, R.id.reply_selection_action_quote, 1, R.string.post_quote);
                // [spoiler] tags
                if (threadLoadable.board.spoilers) {
                    spoilerMenuItem = menu.add(Menu.NONE,
                            R.id.reply_selection_action_spoiler,
                            2,
                            R.string.reply_comment_button_spoiler
                    );
                }

                //setup specific items in a submenu
                SubMenu otherMods = menu.addSubMenu("Modify");
                // g [code]
                if (is4chan && threadLoadable.boardCode.equals("g")) {
                    codeMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_code,
                            1,
                            R.string.reply_comment_button_code
                    );
                }
                // sci [eqn] and [math]
                if (is4chan && threadLoadable.boardCode.equals("sci")) {
                    eqnMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_eqn,
                            2,
                            R.string.reply_comment_button_eqn
                    );
                    mathMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_math,
                            3,
                            R.string.reply_comment_button_math
                    );
                }
                // jp and vip [sjis]
                if (is4chan && (threadLoadable.boardCode.equals("jp") || threadLoadable.boardCode.equals("vip"))) {
                    eqnMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_sjis,
                            4,
                            R.string.reply_comment_button_sjis
                    );
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item == quoteMenuItem) {
                    int selectionStart = comment.getSelectionStart();
                    int selectionEnd = comment.getSelectionEnd();
                    String[] textLines =
                            comment.getText().subSequence(selectionStart, selectionEnd).toString().split("\n");
                    StringBuilder rebuilder = new StringBuilder();
                    for (int i = 0; i < textLines.length; i++) {
                        rebuilder.append(">").append(textLines[i]);
                        if (i != textLines.length - 1) {
                            rebuilder.append("\n");
                        }
                    }
                    comment.getText().replace(selectionStart, selectionEnd, rebuilder.toString());
                    processed = true;
                } else if (item == spoilerMenuItem) {
                    insertTags("[spoiler]", "[/spoiler]");
                } else if (item == codeMenuItem) {
                    insertTags("[code]", "[/code]");
                } else if (item == eqnMenuItem) {
                    insertTags("[eqn]", "[/eqn]");
                } else if (item == mathMenuItem) {
                    insertTags("[math]", "[/math]");
                } else if (item == sjisMenuItem) {
                    insertTags("[sjis]", "[/sjis]");
                }

                if (processed) {
                    mode.finish();
                    processed = false;
                    return true;
                } else {
                    return false;
                }
            }

            @SuppressWarnings("ConstantConditions")
            // for all items, can only be called if >=1 character selected
            private void insertTags(String before, String after) {
                int selectionStart = comment.getSelectionStart();
                comment.getText().insert(comment.getSelectionEnd(), after);
                comment.getText().insert(selectionStart, before);
                processed = true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        presenter.onCommentTextChanged(comment.getText());
    }

    @Override
    public void showThread(Loadable loadable) {
        callback.showThread(loadable);
    }

    @Override
    public ImagePickDelegate getImagePickDelegate() {
        return ((StartActivity) getContext()).getImagePickDelegate();
    }

    @Override
    public ChanThread getThread() {
        return callback.getThread();
    }

    public void onImageOptionsApplied(Reply reply, boolean filenameRemoved) {
        if (filenameRemoved) {
            fileName.setText(reply.fileName); //update edit field with new filename
        } else {
            //update reply with existing filename (may have been changed by user)
            reply.fileName = fileName.getText().toString();
        }

        presenter.onImageOptionsApplied(reply);
    }

    private void showReencodeImageHint() {
        if (!ChanSettings.reencodeHintShown.get()) {
            String message = getString(R.string.click_image_for_extra_options);

            if (hintPopup != null) {
                hintPopup.dismiss();
                hintPopup = null;
            }

            hintPopup = HintPopup.show(getContext(), preview, message, dp(-32), dp(16));
            hintPopup.wiggle();

            ChanSettings.reencodeHintShown.set(true);
        }
    }

    @Override
    public void onUploadingProgress(int percent) {
        if (currentProgress != null) {
            if (percent >= 0) {
                currentProgress.setVisibility(VISIBLE);
            }

            currentProgress.setText(String.valueOf(percent));
        }
    }

    @Override
    public void onCaptchaCountChanged(int validCaptchaCount) {
        if (validCaptchaCount == 0) {
            validCaptchasCount.setVisibility(INVISIBLE);
        } else {
            validCaptchasCount.setVisibility(VISIBLE);
        }

        validCaptchasCount.setText(String.valueOf(validCaptchaCount));
    }

    public interface ReplyLayoutCallback {
        void highlightPostNo(int no);

        void openReply(boolean open);

        void showThread(Loadable loadable);

        void requestNewPostLoad();

        ChanThread getThread();

        void showImageReencodingWindow(boolean supportsReencode);

        void updatePadding();
    }
}
