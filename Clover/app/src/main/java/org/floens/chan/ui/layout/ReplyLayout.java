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

import android.content.Context;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.presenter.ReplyPresenter;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.captcha.CaptchaCallback;
import org.floens.chan.ui.captcha.CaptchaLayout;
import org.floens.chan.ui.captcha.CaptchaLayoutInterface;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.helper.HintPopup;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.ui.view.SelectionListeningEditText;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.ui.animation.AnimationUtils;
import org.floens.chan.utils.ImageDecoder;

import java.io.File;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class ReplyLayout extends LoadView implements View.OnClickListener, AnimationUtils.LayoutAnimationProgress, ReplyPresenter.ReplyPresenterCallback, TextWatcher, ImageDecoder.ImageDecoderCallback, SelectionListeningEditText.SelectionChangedListener {
    private ReplyPresenter presenter;
    private ReplyLayoutCallback callback;
    private boolean newCaptcha;

    private View replyInputLayout;
    private FrameLayout captchaContainer;
    private ImageView captchaHardReset;
    private CaptchaLayoutInterface authenticationLayout;

    private boolean openingName;
    private boolean blockSelectionChange = false;
    private TextView message;
    private EditText name;
    private EditText subject;
    private EditText options;
    private EditText fileName;
    private LinearLayout nameOptions;
    private SelectionListeningEditText comment;
    private TextView commentCounter;
    private LinearLayout previewContainer;
    private CheckBox spoiler;
    private ImageView preview;
    private TextView previewMessage;
    private ImageView more;
    private DropdownArrowDrawable moreDropdown;
    private ImageView attach;
    private ImageView submit;

    private Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            AnimationUtils.animateHeight(message, false, getWidth());
        }
    };

    public ReplyLayout(Context context) {
        super(context);
    }

    public ReplyLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReplyLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        setAnimateLayout(true, true);

        presenter = new ReplyPresenter(this);

        replyInputLayout = LayoutInflater.from(getContext()).inflate(R.layout.layout_reply_input, this, false);
        message = (TextView) replyInputLayout.findViewById(R.id.message);
        name = (EditText) replyInputLayout.findViewById(R.id.name);
        subject = (EditText) replyInputLayout.findViewById(R.id.subject);
        options = (EditText) replyInputLayout.findViewById(R.id.options);
        fileName = (EditText) replyInputLayout.findViewById(R.id.file_name);
        nameOptions = (LinearLayout) replyInputLayout.findViewById(R.id.name_options);
        comment = (SelectionListeningEditText) replyInputLayout.findViewById(R.id.comment);
        comment.addTextChangedListener(this);
        comment.setSelectionChangedListener(this);
        commentCounter = (TextView) replyInputLayout.findViewById(R.id.comment_counter);
        previewContainer = (LinearLayout) replyInputLayout.findViewById(R.id.preview_container);
        spoiler = (CheckBox) replyInputLayout.findViewById(R.id.spoiler);
        preview = (ImageView) replyInputLayout.findViewById(R.id.preview);
        previewMessage = (TextView) replyInputLayout.findViewById(R.id.preview_message);
        preview.setOnClickListener(this);
        more = (ImageView) replyInputLayout.findViewById(R.id.more);
        moreDropdown = new DropdownArrowDrawable(dp(16), dp(16), true, getAttrColor(getContext(), R.attr.dropdown_dark_color), getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color));
        more.setImageDrawable(moreDropdown);
        setRoundItemBackground(more);
        more.setOnClickListener(this);
        attach = (ImageView) replyInputLayout.findViewById(R.id.attach);
        theme().imageDrawable.apply(attach);
        setRoundItemBackground(attach);
        attach.setOnClickListener(this);
        submit = (ImageView) replyInputLayout.findViewById(R.id.submit);
        theme().sendDrawable.apply(submit);
        setRoundItemBackground(submit);
        submit.setOnClickListener(this);

        captchaContainer = (FrameLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_reply_captcha, this, false);
        captchaHardReset = (ImageView) captchaContainer.findViewById(R.id.reset);
        theme().refreshDrawable.apply(captchaHardReset);
        setRoundItemBackground(captchaHardReset);
        captchaHardReset.setOnClickListener(this);

        setView(replyInputLayout);
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

    @Override
    public LayoutParams getLayoutParamsForView(View view) {
        if (view == replyInputLayout || (view == captchaContainer && !newCaptcha)) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        } else if (view == captchaContainer && newCaptcha) {
            return new LayoutParams(LayoutParams.MATCH_PARENT, dp(300));
        } else {
            // Loadbar
            return new LayoutParams(LayoutParams.MATCH_PARENT, dp(100));
        }
    }

    @Override
    public void onLayoutAnimationProgress(View view, boolean vertical, int from, int to, int value, float progress) {
        if (view == nameOptions) {
            moreDropdown.setRotation(openingName ? progress : 1f - progress);
        }
    }

    public boolean onBack() {
        return presenter.onBack();
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
            authenticationLayout.hardReset();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void setPage(ReplyPresenter.Page page, boolean animate) {
        setAnimateLayout(animate, true);
        switch (page) {
            case LOADING:
                setView(null);
                break;
            case INPUT:
                setView(replyInputLayout);
                break;
            case AUTHENTICATION:
                if (authenticationLayout == null) {
                    if (newCaptcha) {
                        authenticationLayout = new CaptchaLayout(getContext());
                    } else {
                        authenticationLayout = (CaptchaLayoutInterface) LayoutInflater.from(getContext()).inflate(R.layout.layout_captcha_legacy, captchaContainer, false);
                    }
                    captchaContainer.addView((View) authenticationLayout, 0);
                }

                if (newCaptcha) {
                    AndroidUtils.hideKeyboard(this);
                }

                setView(captchaContainer);

                break;
        }
    }

    @Override
    public void setCaptchaVersion(boolean newCaptcha) {
        this.newCaptcha = newCaptcha;
    }

    @Override
    public void initCaptcha(String baseUrl, String siteKey, CaptchaCallback callback) {
        authenticationLayout.initCaptcha(baseUrl, siteKey, ThemeHelper.getInstance().getTheme().isLightTheme, callback);
        authenticationLayout.reset();
    }

    @Override
    public void resetCaptcha() {
        authenticationLayout.reset();
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

        if (animate) {
            AnimationUtils.animateHeight(message, open, getWidth());
        } else {
            message.setVisibility(open ? VISIBLE : GONE);
            message.getLayoutParams().height = open ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            message.requestLayout();
        }

        if (autoHide) {
            postDelayed(closeMessageRunnable, 5000);
        }
    }

    @Override
    public void openMessageWebview(String rawMessage) {
//        callback.
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
    public void openNameOptions(boolean open) {
        openingName = open;
        AnimationUtils.animateHeight(nameOptions, open, comment.getWidth(), 300, this);
    }

    @Override
    public void openSubject(boolean open) {
        AnimationUtils.animateHeight(subject, open, comment.getWidth());
    }

    @Override
    public void openFileName(boolean open) {
        AnimationUtils.animateHeight(fileName, open, comment.getWidth());
    }

    @Override
    public void setFileName(String name) {
        fileName.setText(name);
    }

    @Override
    public void updateCommentCount(int count, int maxCount, boolean over) {
        commentCounter.setText(count + "/" + maxCount);
        //noinspection ResourceAsColor
        commentCounter.setTextColor(over ? 0xffff0000 : getAttrColor(getContext(), R.attr.text_color_secondary));
    }

    public void focusComment() {
        comment.requestFocus();
        comment.postDelayed(new Runnable() {
            @Override
            public void run() {
                AndroidUtils.requestKeyboardFocus(comment);
            }
        }, 100);
    }

    @Override
    public void openPreview(boolean show, File previewFile) {
        if (show) {
            theme().clearDrawable.apply(attach);
        } else {
            theme().imageDrawable.apply(attach);
        }

        if (show) {
            ImageDecoder.decodeFileOnBackgroundThread(previewFile, dp(100), dp(100), this);
        } else {
            AnimationUtils.animateLayout(false, previewContainer, previewContainer.getWidth(), 0, 300, false, null);
        }
    }

    @Override
    public void openPreviewMessage(boolean show, String message) {
        previewMessage.setVisibility(show ? VISIBLE : GONE);
        previewMessage.setText(message);
    }

    @Override
    public void openSpoiler(boolean show, boolean checked) {
        AnimationUtils.animateHeight(spoiler, show);
        spoiler.setChecked(checked);
    }

    @Override
    public void onImageBitmap(File file, Bitmap bitmap) {
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
            AnimationUtils.animateLayout(false, previewContainer, 0, dp(100), 300, false, null);
        } else {
            openPreviewMessage(true, getString(R.string.reply_no_preview));
        }
    }

    @Override
    public void onFilePickLoading() {
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

    @Override
    public void showMoreHint() {
        HintPopup.show(getContext(), more, getString(R.string.reply_more_hint), dp(9), dp(4));
    }

    public interface ReplyLayoutCallback {
        void highlightPostNo(int no);

        void openReply(boolean open);

        void showThread(Loadable loadable);

        void requestNewPostLoad();

        ChanThread getThread();
    }
}
