package org.floens.chan.ui.controller;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.PostPopupHelper;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.ThemeHelper;

public class PostRepliesController extends Controller {
    private static final int TRANSITION_DURATION = 200;

    private PostPopupHelper postPopupHelper;
    private ThreadPresenter presenter;

    private int statusBarColorPrevious;
    private boolean first = true;

    private LoadView loadView;
    private ListView listView;

    public PostRepliesController(Context context, PostPopupHelper postPopupHelper, ThreadPresenter presenter) {
        super(context);
        this.postPopupHelper = postPopupHelper;
        this.presenter = presenter;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.post_replies_container);

        // Clicking outside the popup view
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                postPopupHelper.popAll();
            }
        });

        loadView = (LoadView) view.findViewById(R.id.loadview);

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
            if (statusBarColorPrevious != 0) {
                animateStatusBar(true, statusBarColorPrevious);
            }
        }
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();
        if (Build.VERSION.SDK_INT >= 21) {
            if (statusBarColorPrevious != 0) {
                animateStatusBar(false, statusBarColorPrevious);
            }
        }
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        if (listView == null) {
            return null;
        } else {
            ThumbnailView thumbnail = null;
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (view instanceof PostView) {
                    PostView postView = (PostView) view;
                    Post post = postView.getPost();
                    if (post.hasImage && post.imageUrl.equals(postImage.imageUrl)) {
                        thumbnail = postView.getThumbnail();
                        break;
                    }
                }
            }
            return thumbnail;
        }
    }

    public void setPostRepliesData(PostPopupHelper.RepliesData data) {
        displayData(data);
    }

    private void displayData(final PostPopupHelper.RepliesData data) {
        View dataView;
        if (ChanSettings.repliesButtonsBottom.get()) {
            dataView = inflateRes(R.layout.post_replies_bottombuttons);
        } else {
            dataView = inflateRes(R.layout.post_replies);
        }

        listView = (ListView) dataView.findViewById(R.id.post_list);

        View repliesBack = dataView.findViewById(R.id.replies_back);
        repliesBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                postPopupHelper.pop();
            }
        });

        View repliesClose = dataView.findViewById(R.id.replies_close);
        repliesClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                postPopupHelper.popAll();
            }
        });

        if (!ThemeHelper.getInstance().getTheme().isLightTheme) {
            ((TextView) dataView.findViewById(R.id.replies_back_icon)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_back_dark, 0, 0, 0);
            ((TextView) dataView.findViewById(R.id.replies_close_icon)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_done_dark, 0, 0, 0);
            dataView.findViewById(R.id.container).setBackgroundResource(R.drawable.dialog_full_dark);
        }

        ArrayAdapter<Post> adapter = new ArrayAdapter<Post>(context, 0) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                PostView postView;
                if (convertView instanceof PostView) {
                    postView = (PostView) convertView;
                } else {
                    postView = new PostView(context);
                }

                final Post p = getItem(position);

                postView.setPost(p, presenter, false);
                postView.setHighlightQuotesWithNo(data.forPost.no);
                postView.setOnClickListeners(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        postPopupHelper.postClicked(p);
                    }
                });

                return postView;
            }
        };

        adapter.addAll(data.posts);
        listView.setAdapter(adapter);

        listView.setSelectionFromTop(data.listViewIndex, data.listViewTop);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                data.listViewIndex = view.getFirstVisiblePosition();
                View v = view.getChildAt(0);
                data.listViewTop = (v == null) ? 0 : v.getTop();
            }
        });

        loadView.setFadeDuration(first ? 0 : 150);
        first = false;
        loadView.setView(dataView);
    }

    @Override
    public boolean onBack() {
        postPopupHelper.pop();
        return true;
    }

    private void animateStatusBar(boolean in, final int originalColor) {
        ValueAnimator statusBar = ValueAnimator.ofFloat(in ? 0f : 0.5f, in ? 0.5f : 0f);
        statusBar.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (Build.VERSION.SDK_INT >= 21) { // Make lint happy
                    float progress = (float) animation.getAnimatedValue();
                    if (progress == 0f) {
                        getWindow().setStatusBarColor(originalColor);
                    } else {
                        int r = (int) ((1f - progress) * Color.red(originalColor));
                        int g = (int) ((1f - progress) * Color.green(originalColor));
                        int b = (int) ((1f - progress) * Color.blue(originalColor));
                        getWindow().setStatusBarColor(Color.argb(255, r, g, b));
                    }
                }
            }
        });
        statusBar.setDuration(TRANSITION_DURATION).setInterpolator(new LinearInterpolator());
        statusBar.start();
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }
}
