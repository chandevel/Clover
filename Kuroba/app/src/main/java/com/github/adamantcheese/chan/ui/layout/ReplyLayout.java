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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.util.AndroidRuntimeException;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.captcha.CaptchaTokenHolder;
import com.github.adamantcheese.chan.ui.captcha.CustomJsonLayout;
import com.github.adamantcheese.chan.ui.captcha.GenericWebViewAuthenticationLayout;
import com.github.adamantcheese.chan.ui.captcha.LegacyCaptchaLayout;
import com.github.adamantcheese.chan.ui.captcha.v2.js.CaptchaV2JsLayout;
import com.github.adamantcheese.chan.ui.captcha.v2.nojs.CaptchaV2NoJsFallbackLayout;
import com.github.adamantcheese.chan.ui.captcha.v2.nojs.CaptchaV2NoJsLayout;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.LoadView;
import com.github.adamantcheese.chan.ui.view.SelectionListeningEditText;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.BitmapUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.vdurmont.emoji.EmojiParser;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.requestViewAndKeyboardFocus;

public class ReplyLayout
        extends LoadView
        implements ReplyPresenter.ReplyPresenterCallback, TextWatcher,
                   SelectionListeningEditText.SelectionChangedListener, CaptchaTokenHolder.CaptchaValidationListener {

    ReplyPresenter presenter;

    private ReplyLayoutCallback callback;

    private AuthenticationLayoutInterface authenticationLayout;

    private boolean blockTextChange = false;

    // Progress view (when sending request to the server)
    private View progressLayout;
    private ProgressBar progressBar;
    private TextView currentProgress;

    // Reply views:
    private View replyInputLayout;
    private TextView message;
    private EditText name;
    private EditText subject;
    private Button flag;
    private EditText options;
    private EditText fileName;
    private LinearLayout postOptions;
    private Button commentQuoteButton;
    private Button commentSpoilerButton;
    private Button commentCodeButton;
    private Button commentEqnButton;
    private Button commentMathButton;
    private Button commentSJISButton;
    private SelectionListeningEditText comment;
    private TextView commentCounter;
    private LinearLayout previewHolder;
    private ImageView preview;
    private TextView previewMessage;
    private TextView validCaptchasCount;

    private ImageView more;
    private ImageView attach;
    private Space spacer;
    private ImageView reencodeImage;
    private ImageView filenameNew;
    // the tag on this is the spoiler state; that is, getTag -> true means image will be spoilered
    private ImageView spoiler;

    private View topDivider;
    private View botDivider;

    // Captcha views:
    private FrameLayout captchaContainer;

    private final Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            message.setText(R.string.empty);
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
        if (isInEditMode()) return;

        EventBus.getDefault().register(this);
        CaptchaTokenHolder.getInstance().addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isInEditMode()) return;

        EventBus.getDefault().unregister(this);
        CaptchaTokenHolder.getInstance().removeListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        presenter = new ReplyPresenter(getContext(), this);

        // Inflate reply input
        replyInputLayout = LayoutInflater.from(getContext()).inflate(R.layout.layout_reply_input, this, false);
        message = replyInputLayout.findViewById(R.id.message);
        name = replyInputLayout.findViewById(R.id.name);
        subject = replyInputLayout.findViewById(R.id.subject);
        flag = replyInputLayout.findViewById(R.id.flag_button);
        options = replyInputLayout.findViewById(R.id.options);
        fileName = replyInputLayout.findViewById(R.id.file_name);
        filenameNew = replyInputLayout.findViewById(R.id.filename_new);
        postOptions = replyInputLayout.findViewById(R.id.post_options);
        commentQuoteButton = replyInputLayout.findViewById(R.id.comment_quote);
        commentSpoilerButton = replyInputLayout.findViewById(R.id.comment_spoiler);
        commentCodeButton = replyInputLayout.findViewById(R.id.comment_code);
        commentEqnButton = replyInputLayout.findViewById(R.id.comment_eqn);
        commentMathButton = replyInputLayout.findViewById(R.id.comment_math);
        commentSJISButton = replyInputLayout.findViewById(R.id.comment_sjis);
        comment = replyInputLayout.findViewById(R.id.comment);
        commentCounter = replyInputLayout.findViewById(R.id.comment_counter);
        preview = replyInputLayout.findViewById(R.id.preview);
        previewHolder = replyInputLayout.findViewById(R.id.preview_holder);
        previewMessage = replyInputLayout.findViewById(R.id.preview_message);
        validCaptchasCount = replyInputLayout.findViewById(R.id.valid_captchas_count);

        more = replyInputLayout.findViewById(R.id.more);
        attach = replyInputLayout.findViewById(R.id.attach);
        ConstraintLayout submit = replyInputLayout.findViewById(R.id.submit);
        spacer = replyInputLayout.findViewById(R.id.spacer);
        reencodeImage = replyInputLayout.findViewById(R.id.reencode);
        filenameNew = replyInputLayout.findViewById(R.id.filename_new);
        spoiler = replyInputLayout.findViewById(R.id.spoiler);

        progressLayout = LayoutInflater.from(getContext()).inflate(R.layout.layout_reply_progress, this, false);
        progressBar = progressLayout.findViewById(R.id.progress_bar);
        currentProgress = progressLayout.findViewById(R.id.current_progress);

        topDivider = replyInputLayout.findViewById(R.id.top_div);
        botDivider = replyInputLayout.findViewById(R.id.bot_div);

        // Setup reply layout views
        message.setMovementMethod(new LinkMovementMethod());
        commentQuoteButton.setOnClickListener(v -> insertQuote());
        commentSpoilerButton.setOnClickListener(v -> insertTags("[spoiler]", "[/spoiler]"));
        commentCodeButton.setOnClickListener(v -> insertTags("[code]", "[/code]"));
        commentMathButton.setOnClickListener(v -> insertTags("[math]", "[/math]"));
        commentEqnButton.setOnClickListener(v -> insertTags("[eqn]", "[/eqn]"));
        commentSJISButton.setOnClickListener(v -> insertTags("[sjis]", "[/sjis]"));

        name.addTextChangedListener(this);
        flag.addTextChangedListener(this);
        flag.setOnClickListener(v -> {
            List<FloatingMenuItem<String>> items = new ArrayList<>();
            items.add(new FloatingMenuItem<>(null, "No Flag"));
            Map<String, String> boardFlags = presenter.getBoardFlags();
            FloatingMenuItem<String> selected = null;
            for (String key : boardFlags.keySet()) {
                FloatingMenuItem<String> flagItem = new FloatingMenuItem<>(key, boardFlags.get(key));
                if (key.contentEquals(flag.getText())) {
                    selected = flagItem;
                }
                items.add(flagItem);
            }
            if (items.isEmpty()) return;
            FloatingMenu<String> menu = new FloatingMenu<>(getContext(), flag, items);
            menu.setAnchorGravity(Gravity.CENTER, 0, 0);
            menu.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return items.size();
                }

                @Override
                public String getItem(int position) {
                    return items.get(position).getText();
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    TextView textView = (TextView) (convertView != null
                            ? convertView
                            : LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.toolbar_menu_item, parent, false));
                    textView.setText(getItem(position));
                    textView.setTypeface(ThemeHelper.getTheme().mainFont);
                    return textView;
                }
            });
            menu.setSelectedItem(selected);
            menu.setCallback(new FloatingMenu.ClickCallback<String>() {
                @Override
                public void onFloatingMenuItemClicked(
                        FloatingMenu<String> menu, FloatingMenuItem<String> item
                ) {
                    flag.setText(item.getId());
                }
            });
            menu.setPopupHeight(dp(300));
            menu.show();
        });
        options.addTextChangedListener(this);
        subject.addTextChangedListener(this);
        comment.addTextChangedListener(this);
        fileName.addTextChangedListener(this);
        comment.setSelectionChangedListener(this);
        comment.setOnFocusChangeListener((view, focused) -> {
            if (!focused) hideKeyboard(comment);
        });
        comment.setPlainTextPaste(true);
        setupCommentContextMenu();

        setupOptionsContextMenu();

        if (!isInEditMode()) {
            more.setRotation(ChanSettings.moveInputToBottom.get() ? 180f : 0f);
        }
        more.setOnClickListener(v -> presenter.onMoreClicked());

        attach.setOnClickListener(v -> presenter.onAttachClicked(false));
        attach.setOnLongClickListener(v -> {
            presenter.onAttachClicked(true);
            return true;
        });

        submit.setOnClickListener(v -> presenter.onSubmitClicked(false));
        submit.setOnLongClickListener(v -> {
            presenter.onSubmitClicked(true);
            return true;
        });

        reencodeImage.setOnClickListener(v -> {
            if (presenter.isAttachedFileSupportedForReencoding()) {
                attach.setClickable(false); // prevent immediately removing the file
                callback.showImageReencodingWindow();
            } else {
                showToast(getContext(), R.string.file_cannot_be_reencoded, Toast.LENGTH_LONG);
            }
        });

        filenameNew.setOnClickListener(v -> {
            presenter.filenameNewClicked();
            showToast(getContext(), "Filename changed.");
        });

        spoiler.setTag(false);
        spoiler.setOnClickListener(v -> {
            if ((boolean) spoiler.getTag()) {
                // unspoiler
                spoiler.setTag(false);
                spoiler.setImageResource(R.drawable.ic_fluent_eye_show_24_filled);
            } else {
                // spoiler
                spoiler.setTag(true);
                spoiler.setImageResource(R.drawable.ic_fluent_eye_hide_24_filled);
            }
        });

        // Inflate captcha layout
        captchaContainer =
                (FrameLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_reply_captcha, this, false);
        ImageView captchaHardReset = captchaContainer.findViewById(R.id.reset);

        // Setup captcha layout views
        captchaContainer.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        captchaHardReset.setOnClickListener(v -> {
            ((AnimatedVectorDrawable) ((ImageView) v).getDrawable()).start();

            if (authenticationLayout != null) {
                authenticationLayout.hardReset();
            }
        });

        setDividerVisibility(false);
        setView(replyInputLayout, !isInEditMode());
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

    public void cleanup() {
        presenter.unbindLoadable();
        removeCallbacks(closeMessageRunnable);
    }

    public boolean onBack() {
        return presenter.onBack();
    }

    private void setWrappingMode(boolean matchParent) {
        // MATCH_PARENT is used for the expanded view, WRAP_CONTENT is for the minimized view
        LayoutParams params = (LayoutParams) getLayoutParams();
        params.width = MATCH_PARENT;
        params.height = matchParent ? MATCH_PARENT : WRAP_CONTENT;

        if (ChanSettings.moveInputToBottom.get()) {
            if (matchParent) {
                params.setMargins(0, ((ThreadListLayout) getParent()).toolbarHeight(), 0, 0);
                params.gravity = -1;
            } else {
                params.setMargins(0, 0, 0, 0);
                params.gravity = Gravity.BOTTOM;
            }
        }

        setLayoutParams(params);
    }

    private boolean insertQuote() {
        int selectionStart = Math.min(comment.getSelectionEnd(), comment.getSelectionStart());
        int selectionEnd = Math.max(comment.getSelectionEnd(), comment.getSelectionStart());
        String[] textLines = comment.getText().subSequence(selectionStart, selectionEnd).toString().split("\n");
        StringBuilder rebuilder = new StringBuilder();
        for (int i = 0; i < textLines.length; i++) {
            rebuilder.append(">").append(textLines[i]);
            if (i != textLines.length - 1) {
                rebuilder.append("\n");
            }
        }
        comment.getText().replace(selectionStart, selectionEnd, rebuilder.toString());
        return true;
    }

    private boolean insertTags(String before, String after) {
        int selectionStart = comment.getSelectionStart();
        int selectLen = comment.getSelectionEnd() - comment.getSelectionStart();
        comment.getText().insert(comment.getSelectionEnd(), after);
        comment.getText().insert(selectionStart, before);
        comment.setSelection(before.length() + selectionStart + selectLen);
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void initializeAuthentication(
            Loadable loadable,
            SiteAuthentication authentication,
            AuthenticationLayoutCallback callback,
            boolean useV2NoJsCaptcha,
            boolean autoReply
    ) {
        if (authenticationLayout == null) {
            switch (authentication.type) {
                case CAPTCHA1:
                    authenticationLayout = (LegacyCaptchaLayout) LayoutInflater.from(getContext())
                            .inflate(R.layout.layout_captcha_legacy, captchaContainer, false);
                    break;
                case CAPTCHA2:
                    authenticationLayout = new CaptchaV2JsLayout(getContext());
                    break;
                case CAPTCHA2_NOJS:
                    if (useV2NoJsCaptcha) {
                        // new captcha window without webview
                        authenticationLayout = new CaptchaV2NoJsLayout(getContext());
                    } else {
                        // default webview-based captcha view
                        authenticationLayout = new CaptchaV2NoJsFallbackLayout(getContext());
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
                case CUSTOM_JSON:
                    // specific to 4chan, may abstract in the future
                    authenticationLayout = (CustomJsonLayout) LayoutInflater.from(getContext())
                            .inflate(R.layout.layout_captcha_custom_json, captchaContainer, false);
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

        authenticationLayout.initialize(loadable, callback, autoReply);
        authenticationLayout.reset();
    }

    @Override
    public void setPage(ReplyPresenter.Page page) {
        Logger.d(this, "Switching to page " + page.name());
        switch (page) {
            case LOADING:
                setWrappingMode(false);
                setView(progressLayout);
                onUploadingProgress(0);
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
    public void destroyCurrentAuthentication() {
        if (authenticationLayout == null) {
            return;
        }

        // cleanup resources when switching from the new to the old captcha view
        if (authenticationLayout instanceof CaptchaV2NoJsLayout) {
            ((CaptchaV2NoJsLayout) authenticationLayout).onDestroy();
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
        if (ChanSettings.enableEmoji.get()) {
            draft.name = EmojiParser.parseToUnicode(draft.name);
            draft.comment = EmojiParser.parseToUnicode(draft.comment);
        }
        blockTextChange = true;
        name.setText(draft.name);
        subject.setText(draft.subject);
        flag.setText(draft.flag);
        options.setText(draft.options);
        fileName.setText(draft.fileName);
        comment.setText(draft.comment);
        blockTextChange = false;
        spoiler.setTag(draft.spoilerImage);
        setSpoilerIcon();
    }

    @Override
    public void loadViewsIntoDraft(Reply draft) {
        draft.name = name.getText().toString();
        draft.subject = subject.getText().toString();
        draft.flag = flag.getText().toString();
        draft.options = options.getText().toString();
        draft.comment = comment.getText().toString();
        draft.fileName = fileName.getText().toString();
        draft.spoilerImage = (boolean) spoiler.getTag();

        if (ChanSettings.enableEmoji.get()) {
            draft.name = StringUtils.parseEmojiToAscii(draft.name);
            draft.comment = StringUtils.parseEmojiToAscii(draft.comment);
        }
    }

    @Override
    public int getSelectionStart() {
        return comment.getSelectionStart();
    }

    @Override
    public void adjustSelection(int start, int amount) {
        try {
            comment.setSelection(start + amount);
        } catch (Exception e) {
            comment.setSelection(comment.getText().length()); // set selection to the end if it fails for any reason
        }
    }

    @Override
    public void openMessage(CharSequence text) {
        if (text == null) text = "";
        removeCallbacks(closeMessageRunnable);
        message.setText(text);
        message.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);

        if (!TextUtils.isEmpty(text)) {
            postDelayed(closeMessageRunnable, 5000);
        }
    }

    @Override
    public void onPosted(boolean newThread, Loadable newLoadable) {
        if (showToastInsteadOfAnimation(newThread, newLoadable)) {
            if (newThread) {
                showToast(getContext(), "Posted new thread \"" + newLoadable.toShortestString() + "\"!");
            } else {
                showToast(getContext(), "Posted reply to \"" + newLoadable.toShortestString() + "\"!");
            }
            postComplete(newThread, newLoadable);
        } else {
            // basically, fade out the progress, fade in a confirmation text, and then switch to the thread
            AnimatorSet fade = new AnimatorSet();

            ObjectAnimator fadeOutProg = ObjectAnimator.ofFloat(progressBar, View.ALPHA, 0f).setDuration(150);
            ObjectAnimator fadeOutText = ObjectAnimator.ofFloat(currentProgress, View.ALPHA, 0f).setDuration(150);

            AnimatorSet fadeOutPair = new AnimatorSet();
            fadeOutPair.playTogether(fadeOutProg, fadeOutText);
            fadeOutPair.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    SpannableString done;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ChanSettings.enableEmoji.get()
                            && ChanSettings.addDubs.get()) {
                        done = new SpannableString("\uD83D\uDE29\uD83D\uDC4C");
                    } else {
                        done = new SpannableString("✓");
                    }
                    done.setSpan(new AbsoluteSizeSpan(36, true), 0, done.length(), 0);
                    currentProgress.setText(done);
                }
            });

            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(currentProgress, View.ALPHA, 1f).setDuration(150);

            fade.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    postDelayed(() -> postComplete(newThread, newLoadable), 850);
                }
            });

            fade.playSequentially(fadeOutPair, fadeIn);
            fade.start();
        }
    }

    private boolean showToastInsteadOfAnimation(boolean newThread, Loadable newLoadable) {
        // if new thread
        //      if not viewing catalog, show toast
        // if not new thread
        //      if loadable doesn't match the one passed in, show toast
        return newThread
                ? !callback.isViewingCatalog()
                : !callback.getThread().getLoadable().databaseEquals(newLoadable);
    }

    private void postComplete(boolean newThread, Loadable newLoadable) {
        presenter.switchPage(ReplyPresenter.Page.INPUT);
        presenter.closeAll();
        callback.openReply(false);
        progressBar.setAlpha(1f);
        currentProgress.setText("");
        // only swap to the new thread if on the catalog still
        if (newThread && callback.isViewingCatalog()) {
            callback.showThread(newLoadable);
        }
        callback.requestNewPostLoad();
    }

    @Override
    public void setCommentHint(boolean isThreadMode) {
        comment.setHint(getString(isThreadMode ? R.string.reply_comment_thread : R.string.reply_comment_board));
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

        setDividerVisibility(false);
    }

    @Override
    public void setExpanded(boolean expanded) {
        setWrappingMode(expanded);
        comment.setMaxLines(expanded ? 500 : 6);
        previewHolder.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, expanded ? dp(300) : dp(200)));
        more.setRotation(ChanSettings.moveInputToBottom.get() ? (expanded ? 0f : 180f) : (expanded ? 180f : 0f));

        setDividerVisibility(expanded);

        if (!expanded) {
            hideKeyboard(comment);
        }
    }

    private void setDividerVisibility(boolean hide) {
        if (isInEditMode()) {
            topDivider.setVisibility(GONE);
            botDivider.setVisibility(VISIBLE);
            return;
        }

        if (hide) {
            topDivider.setVisibility(GONE);
            botDivider.setVisibility(GONE);
        } else {
            topDivider.setVisibility(ChanSettings.moveInputToBottom.get() ? VISIBLE : GONE);
            botDivider.setVisibility(ChanSettings.moveInputToBottom.get() ? GONE : VISIBLE);
        }
    }

    private void setSpoilerIcon() {
        if ((boolean) spoiler.getTag()) {
            spoiler.setImageResource(R.drawable.ic_fluent_eye_hide_24_filled);
        } else {
            spoiler.setImageResource(R.drawable.ic_fluent_eye_show_24_filled);
        }
    }

    @Override
    public void openPostOptions(boolean open) {
        postOptions.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openSubject(boolean open) {
        subject.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openFlag(boolean open) {
        flag.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openCommentQuoteButton(boolean open) {
        commentQuoteButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentSpoilerButton(boolean open) {
        commentSpoilerButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentCodeButton(boolean open) {
        commentCodeButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentEqnButton(boolean open) {
        commentEqnButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentMathButton(boolean open) {
        commentMathButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentSJISButton(boolean open) {
        commentSJISButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setFileName(String name) {
        fileName.setText(name);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void updateCommentCount(int count, int maxCount, boolean over) {
        commentCounter.setText(count + "/" + maxCount);
        commentCounter.setTextColor(over ? Color.RED : getAttrColor(getContext(), android.R.attr.textColorSecondary));
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
        if (show) {
            BitmapUtils.decodeFilePreviewImage(previewFile, dp(400), dp(300), bitmap -> {
                if (bitmap != null) {
                    preview.setImageBitmap(bitmap);
                    previewHolder.setVisibility(VISIBLE);
                    callback.updatePadding();
                    showImageOptionHints();
                } else {
                    openPreviewMessage(true, getString(R.string.reply_no_preview));
                }
            }, true);
            fileName.setVisibility(presenter.isExpanded() ? VISIBLE : GONE);
            spacer.setVisibility(VISIBLE);
            reencodeImage.setVisibility(VISIBLE);
            filenameNew.setVisibility(VISIBLE);
            spoiler.setVisibility(presenter.canPostSpoileredImages() ? VISIBLE : GONE);
            attach.setImageResource(R.drawable.ic_fluent_dismiss_24_filled);
        } else {
            fileName.setVisibility(GONE);
            spacer.setVisibility(GONE);
            reencodeImage.setVisibility(GONE);
            filenameNew.setVisibility(GONE);
            spoiler.setVisibility(GONE);
            spoiler.setTag(previewFile == null ? false : spoiler.getTag());
            setSpoilerIcon();
            previewHolder.setVisibility(GONE);
            previewMessage.setVisibility(GONE);
            callback.updatePadding();
            attach.setImageResource(R.drawable.ic_fluent_image_add_24_filled);
        }
    }

    @Override
    public void openPreviewMessage(boolean show, String message) {
        previewMessage.setVisibility(show ? VISIBLE : GONE);
        previewMessage.setText(message);
    }

    @Override
    public void enableImageAttach(boolean canAttach) {
        attach.setVisibility(canAttach ? VISIBLE : GONE);
        attach.setEnabled(canAttach);
    }

    @Override
    public void enableName(boolean canName) {
        name.setVisibility(canName ? VISIBLE : GONE);
        name.setEnabled(canName);
    }

    @Override
    public void highlightPostNo(int no) {
        callback.highlightPostNo(no);
    }

    @Override
    public void onSelectionChanged() {
        presenter.onSelectionChanged();
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
                    sjisMenuItem = otherMods.add(Menu.NONE,
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
                    processed = insertQuote();
                } else if (item == spoilerMenuItem) {
                    processed = insertTags("[spoiler]", "[/spoiler]");
                } else if (item == codeMenuItem) {
                    processed = insertTags("[code]", "[/code]");
                } else if (item == eqnMenuItem) {
                    processed = insertTags("[eqn]", "[/eqn]");
                } else if (item == mathMenuItem) {
                    processed = insertTags("[math]", "[/math]");
                } else if (item == sjisMenuItem) {
                    processed = insertTags("[sjis]", "[/sjis]");
                }

                if (processed) {
                    processed = false;
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    private void setupOptionsContextMenu() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        options.setCustomInsertionActionModeCallback(new ActionMode.Callback() {
            private MenuItem sageMenuItem;
            private MenuItem passMenuItem;
            private MenuItem fortuneMenuItem;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                if (callback.getThread() == null) return true;
                Loadable threadLoadable = callback.getThread().getLoadable();
                sageMenuItem = menu.add(Menu.NONE, R.id.options_selection_action_sage, 1, R.string.options_button_sage);
                if (threadLoadable.board.site instanceof Chan4) {
                    passMenuItem =
                            menu.add(Menu.NONE, R.id.options_selection_action_pass, 2, R.string.options_button_pass);
                    if (threadLoadable.boardCode.equals("s4s")) {
                        fortuneMenuItem = menu.add(Menu.NONE,
                                R.id.options_selection_action_fortune,
                                3,
                                R.string.options_button_fortune
                        );
                    }
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                String currentText = options.getText() == null ? "" : options.getText().toString();
                if (item == sageMenuItem) {
                    options.setText(String.format("%ssage", currentText));
                } else if (item == passMenuItem) {
                    options.setText(String.format("%ssince4pass", currentText));
                } else if (item == fortuneMenuItem) {
                    options.setText(String.format("%sfortune", currentText));
                }

                return true;
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
        if (!blockTextChange) {
            presenter.onTextChanged();
        }
        if (s.equals(comment.getText())) {
            presenter.onCommentTextChanged(comment.getText());
        }
    }

    @Override
    public ImagePickDelegate getImagePickDelegate() {
        return ((StartActivity) getContext()).getImagePickDelegate();
    }

    @Override
    public ChanThread getThread() {
        return callback.getThread();
    }

    public void onImageOptionsApplied() {
        presenter.onImageOptionsApplied();
    }

    public void onImageOptionsComplete() {
        attach.setClickable(true); // reencode window gone, allow the file to be removed
    }

    private void showImageOptionHints() {
        Balloon reencodeHint = AndroidUtils.getBaseToolTip(getContext())
                .setPreferenceName("ReencodeHint")
                .setArrowOrientation(ArrowOrientation.RIGHT)
                .setTextResource(R.string.reencode_button_hint)
                .build();
        Balloon filenameHint = AndroidUtils.getBaseToolTip(getContext())
                .setPreferenceName("ReplyFilenameRefreshHint")
                .setArrowOrientation(ArrowOrientation.RIGHT)
                .setTextResource(R.string.reply_filename_hint)
                .build();
        Balloon spoilerHint = AndroidUtils.getBaseToolTip(getContext())
                .setPreferenceName("ReplyImageSpoilerHint")
                .setArrowOrientation(ArrowOrientation.RIGHT)
                .setTextResource(R.string.reply_spoiler_hint)
                .build();

        if (presenter.canPostSpoileredImages()) {
            spoilerHint.relayShowAlignLeft(filenameHint, filenameNew).relayShowAlignLeft(reencodeHint, reencodeImage);
            spoilerHint.showAlignLeft(spoiler);
        } else {
            filenameHint.relayShowAlignLeft(reencodeHint, reencodeImage);
            filenameHint.showAlignLeft(filenameNew);
        }
    }

    @Override
    public void onUploadingProgress(int percent) {
        currentProgress.setAlpha(percent > 0 ? 1f : 0f);
        currentProgress.setText(String.valueOf(percent));
    }

    @Override
    public void onCaptchaCountChanged(int validCaptchaCount) {
        validCaptchasCount.setVisibility(validCaptchaCount == 0 ? GONE : VISIBLE);
        validCaptchasCount.setText(String.valueOf(validCaptchaCount));
    }

    public interface ReplyLayoutCallback {
        void highlightPostNo(int no);

        void openReply(boolean open);

        void showThread(Loadable loadable);

        void requestNewPostLoad();

        ChanThread getThread();

        void showImageReencodingWindow();

        void updatePadding();

        boolean isViewingCatalog();
    }
}
