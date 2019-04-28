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
package org.floens.chan.ui.layout;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.presenter.ReplyPresenter;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.captcha.AuthenticationLayoutCallback;
import org.floens.chan.ui.captcha.AuthenticationLayoutInterface;
import org.floens.chan.ui.captcha.CaptchaLayout;
import org.floens.chan.ui.captcha.GenericWebViewAuthenticationLayout;
import org.floens.chan.ui.captcha.LegacyCaptchaLayout;
import org.floens.chan.ui.captcha.v1.CaptchaNojsLayoutV1;
import org.floens.chan.ui.captcha.v2.CaptchaNoJsLayoutV2;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.ui.view.SelectionListeningEditText;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.ImageDecoder;

import java.io.File;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class ReplyLayout extends LoadView implements View.OnClickListener, ReplyPresenter.ReplyPresenterCallback, TextWatcher, ImageDecoder.ImageDecoderCallback, SelectionListeningEditText.SelectionChangedListener {
    @Inject
    ReplyPresenter presenter;

    private ReplyLayoutCallback callback;
    private boolean newCaptcha;

    private AuthenticationLayoutInterface authenticationLayout;
    private boolean openingName;

    private boolean blockSelectionChange = false;

    // Reply views:
    private View replyInputLayout;
    private TextView message;
    private EditText name;
    private EditText subject;
    private EditText options;
    private EditText fileName;
    private LinearLayout nameOptions;
    private ViewGroup commentButtons;
    private Button commentQuoteButton;
    private Button commentSpoilerButton;
    private SelectionListeningEditText comment;
    private TextView commentCounter;
    private CheckBox spoiler;
    private ImageView preview;
    private TextView previewMessage;
    private ImageView attach;
    private ImageView more;
    private ImageView submit;
    private DropdownArrowDrawable moreDropdown;

    // Captcha views:
    private FrameLayout captchaContainer;
    private ImageView captchaHardReset;

    private Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            message.setVisibility(View.GONE);
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
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        final LayoutInflater inflater = LayoutInflater.from(getContext());

        // Inflate reply input
        replyInputLayout = inflater.inflate(R.layout.layout_reply_input, this, false);
        message = replyInputLayout.findViewById(R.id.message);
        name = replyInputLayout.findViewById(R.id.name);
        subject = replyInputLayout.findViewById(R.id.subject);
        options = replyInputLayout.findViewById(R.id.options);
        fileName = replyInputLayout.findViewById(R.id.file_name);
        nameOptions = replyInputLayout.findViewById(R.id.name_options);
        commentButtons = replyInputLayout.findViewById(R.id.comment_buttons);
        commentQuoteButton = replyInputLayout.findViewById(R.id.comment_quote);
        commentSpoilerButton = replyInputLayout.findViewById(R.id.comment_spoiler);
        comment = replyInputLayout.findViewById(R.id.comment);
        commentCounter = replyInputLayout.findViewById(R.id.comment_counter);
        spoiler = replyInputLayout.findViewById(R.id.spoiler);
        preview = replyInputLayout.findViewById(R.id.preview);
        previewMessage = replyInputLayout.findViewById(R.id.preview_message);
        attach = replyInputLayout.findViewById(R.id.attach);
        more = replyInputLayout.findViewById(R.id.more);
        submit = replyInputLayout.findViewById(R.id.submit);

        // Setup reply layout views
        commentQuoteButton.setOnClickListener(this);
        commentSpoilerButton.setOnClickListener(this);

        comment.addTextChangedListener(this);
        comment.setSelectionChangedListener(this);

        preview.setOnClickListener(this);

        moreDropdown = new DropdownArrowDrawable(dp(16), dp(16), true,
                getAttrColor(getContext(), R.attr.dropdown_dark_color),
                getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color));
        more.setImageDrawable(moreDropdown);
        setRoundItemBackground(more);
        more.setOnClickListener(this);

        theme().imageDrawable.apply(attach);
        setRoundItemBackground(attach);
        attach.setOnClickListener(this);

        theme().sendDrawable.apply(submit);
        setRoundItemBackground(submit);
        submit.setOnClickListener(this);

        // Inflate captcha layout
        captchaContainer = (FrameLayout) inflater.inflate(R.layout.layout_reply_captcha, this, false);
        captchaHardReset = captchaContainer.findViewById(R.id.reset);

        // Setup captcha layout views
        captchaContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        theme().refreshDrawable.apply(captchaHardReset);
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
        presenter.bindLoadable(loadable);
    }

    public void cleanup() {
        presenter.unbindLoadable();
        removeCallbacks(closeMessageRunnable);
    }

    public boolean onBack() {
        return presenter.onBack();
    }

    private void setWrap(boolean wrap) {
        setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                wrap ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    public void onClick(View v) {
        if (v == more) {
            presenter.onMoreClicked();
        } else if (v == attach) {
            presenter.onAttachClicked();
        } else if (v == submit) {
            presenter.onSubmitClicked();
        }/* else if (v == preview) {
            // TODO
        }*/ else if (v == captchaHardReset) {
            if (authenticationLayout != null) {
                authenticationLayout.hardReset();
            }
        } else if (v == commentQuoteButton) {
            presenter.commentQuoteClicked();
        } else if (v == commentSpoilerButton) {
            presenter.commentSpoilerClicked();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void initializeAuthentication(Site site,
                                         SiteAuthentication authentication,
                                         AuthenticationLayoutCallback callback,
                                         boolean useV2NoJsCaptcha) {
        if (authenticationLayout == null) {
            switch (authentication.type) {
                case CAPTCHA1: {
                    final LayoutInflater inflater = LayoutInflater.from(getContext());
                    authenticationLayout = (LegacyCaptchaLayout) inflater.inflate(
                            R.layout.layout_captcha_legacy, captchaContainer, false);
                    break;
                }
                case CAPTCHA2: {
                    authenticationLayout = new CaptchaLayout(getContext());
                    break;
                }
                case CAPTCHA2_NOJS:
                    if (!useV2NoJsCaptcha) {
                        // default webview-based captcha view
                        authenticationLayout = new CaptchaNojsLayoutV1(getContext());
                    } else {
                        // new captcha window without webview
                        authenticationLayout = new CaptchaNoJsLayoutV2(getContext());
                    }

                    ImageView resetButton = captchaContainer.findViewById(R.id.reset);
                    if (resetButton != null) {
                        if (!useV2NoJsCaptcha) {
                            // restore the button's visibility when using old v1 captcha view
                            resetButton.setVisibility(View.VISIBLE);
                        } else {
                            // we don't need the default reset button because we have our own
                            resetButton.setVisibility(View.GONE);
                        }
                    }

                    break;
                case GENERIC_WEBVIEW: {
                    GenericWebViewAuthenticationLayout view = new GenericWebViewAuthenticationLayout(getContext());

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT
                    );
//                    params.setMargins(dp(8), dp(8), dp(8), dp(200));
                    view.setLayoutParams(params);

                    authenticationLayout = view;
                    break;
                }
                case NONE:
                default: {
                    throw new IllegalArgumentException();
                }
            }

            captchaContainer.addView((View) authenticationLayout, 0);
        }

        if (!(authenticationLayout instanceof LegacyCaptchaLayout)) {
            AndroidUtils.hideKeyboard(this);
        }

        authenticationLayout.initialize(site, callback);
        authenticationLayout.reset();
    }

    @Override
    public void setPage(ReplyPresenter.Page page, boolean animate) {
        switch (page) {
            case LOADING:
                setWrap(true);
                View progressBar = setView(null);
                progressBar.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(100)));
                break;
            case INPUT:
                setView(replyInputLayout);
                setWrap(!presenter.isExpanded());
                break;
            case AUTHENTICATION:
                setWrap(false);

                setView(captchaContainer);

                captchaContainer.requestFocus(View.FOCUS_DOWN);

                break;
        }

        if (page != ReplyPresenter.Page.AUTHENTICATION && authenticationLayout != null) {
            AndroidUtils.removeFromParentView((View) authenticationLayout);
            authenticationLayout = null;
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

        if (!(authenticationLayout instanceof CaptchaNoJsLayoutV2)) {
            return;
        }

        // cleanup resources when switching from the new to the old captcha view
        ((CaptchaNoJsLayoutV2) authenticationLayout).onDestroy();
        captchaContainer.removeView((CaptchaNoJsLayoutV2) authenticationLayout);
        authenticationLayout = null;
    }

    @Override
    public void loadDraftIntoViews(Reply draft) {
        name.setText(draft.name);
        subject.setText(draft.subject);
        options.setText(draft.options);
        blockSelectionChange = true;
        comment.setText(draft.comment);
        comment.setSelection(draft.selection);
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
        draft.selection = comment.getSelectionStart();
        draft.fileName = fileName.getText().toString();
        draft.spoilerImage = spoiler.isChecked();
    }

    @Override
    public void openMessage(boolean open, boolean animate, String text, boolean autoHide) {
        removeCallbacks(closeMessageRunnable);
        message.setText(text);
        message.setVisibility(open ? View.VISIBLE : View.GONE);

        if (autoHide) {
            postDelayed(closeMessageRunnable, 5000);
        }
    }

    @Override
    public void onPosted() {
        Toast.makeText(getContext(), R.string.reply_success, Toast.LENGTH_SHORT).show();
        callback.openReply(false);
        callback.requestNewPostLoad();
    }

    @Override
    public void setCommentHint(String hint) {
        comment.setHint(hint);
    }

    @Override
    public void showCommentCounter(boolean show) {
        commentCounter.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setExpanded(boolean expanded) {
        setWrap(!expanded);

        comment.setMaxLines(expanded ? 500 : 6);

        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                expanded ? dp(150) : dp(100)
        ));

        ValueAnimator animator = ValueAnimator.ofFloat(expanded ? 0f : 1f, expanded ? 1f : 0f);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.setDuration(400);
        animator.addUpdateListener(animation ->
                moreDropdown.setRotation((float) animation.getAnimatedValue()));
        animator.start();
    }

    @Override
    public void openNameOptions(boolean open) {
        openingName = open;
        nameOptions.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openSubject(boolean open) {
        subject.setVisibility(open ? View.VISIBLE : View.GONE);
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
    public void openFileName(boolean open) {
        fileName.setVisibility(open ? View.VISIBLE : View.GONE);
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
        comment.post(() -> AndroidUtils.requestViewAndKeyboardFocus(comment));
    }

    @Override
    public void onFallbackToV1CaptchaView() {
        // fallback to v1 captcha window
        presenter.switchPage(ReplyPresenter.Page.AUTHENTICATION, true, false);
    }

    @Override
    public void openPreview(boolean show, File previewFile) {
        if (show) {
            theme().clearDrawable.apply(attach);
        } else {
            theme().imageDrawable.apply(attach);
        }

        if (show) {
            ImageDecoder.decodeFileOnBackgroundThread(previewFile, dp(400), dp(300), this);
        } else {
            spoiler.setVisibility(View.GONE);
            preview.setVisibility(View.GONE);
            previewMessage.setVisibility(View.GONE);
        }
    }

    @Override
    public void openPreviewMessage(boolean show, String message) {
        previewMessage.setVisibility(show ? VISIBLE : GONE);
        previewMessage.setText(message);
    }

    @Override
    public void openSpoiler(boolean show, boolean checked) {
        spoiler.setVisibility(show ? View.VISIBLE : View.GONE);
        spoiler.setChecked(checked);
    }

    @Override
    public void onImageBitmap(File file, Bitmap bitmap) {
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
            preview.setVisibility(View.VISIBLE);
        } else {
            openPreviewMessage(true, getString(R.string.reply_no_preview));
        }
    }

    @Override
    public void onFilePickLoading() {
        // TODO
    }

    @Override
    public void onFilePickError() {
        Toast.makeText(getContext(), R.string.reply_file_open_failed, Toast.LENGTH_LONG).show();
    }

    @Override
    public void highlightPostNo(int no) {
        callback.highlightPostNo(no);
    }

    @Override
    public void onSelectionChanged(int selStart, int selEnd) {
        if (!blockSelectionChange) {
            presenter.onSelectionChanged();
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

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

    public interface ReplyLayoutCallback {
        void highlightPostNo(int no);

        void openReply(boolean open);

        void showThread(Loadable loadable);

        void requestNewPostLoad();

        ChanThread getThread();
    }
}
