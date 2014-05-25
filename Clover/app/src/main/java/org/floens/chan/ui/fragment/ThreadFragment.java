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
package org.floens.chan.ui.fragment;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

import org.floens.chan.R;
import org.floens.chan.core.loader.EndOfLineException;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.BaseActivity;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.utils.Utils;

import java.util.List;

public class ThreadFragment extends Fragment implements ThreadManager.ThreadManagerListener {
    private BaseActivity baseActivity;
    private ThreadManager threadManager;
    private Loadable loadable;

    private PostAdapter postAdapter;
    private LoadView container;
    private ListView listView;
    private ImageView skip;
    private SkipLogic skipLogic;

    public static ThreadFragment newInstance(BaseActivity activity) {
        ThreadFragment fragment = new ThreadFragment();
        fragment.baseActivity = activity;
        fragment.threadManager = new ThreadManager(activity, fragment);

        return fragment;
    }

    public void bindLoadable(Loadable l) {
        if (loadable != null) {
            threadManager.unbindLoader();
        }

        setEmpty();

        loadable = l;
        threadManager.bindLoader(loadable);
    }

    public void requestData() {
        threadManager.requestData();
    }

    public void reload() {
        setEmpty();

        threadManager.requestData();
    }

    public void openReply() {
        if (threadManager.hasLoader()) {
            threadManager.openReply(true);
        }
    }

    public boolean hasLoader() {
        return threadManager.hasLoader();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (threadManager != null) {
            threadManager.onDestroy();
        }
        threadManager = null;
        loadable = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (threadManager != null) {
            threadManager.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (threadManager != null) {
            threadManager.onStop();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        container = new LoadView(inflater.getContext());
        return container;
    }

    @Override
    public void onThreadLoaded(List<Post> posts, boolean append) {
        if (postAdapter == null) {
            RelativeLayout compound = new RelativeLayout(baseActivity);
            listView = new ListView(baseActivity);

            postAdapter = new PostAdapter(baseActivity, threadManager, listView);

            listView.setLayoutParams(Utils.MATCH_PARAMS);
            listView.setAdapter(postAdapter);
            listView.setSelectionFromTop(loadable.listViewIndex, loadable.listViewTop);

            listView.setOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (skipLogic != null) {
                        skipLogic.onScrollStateChanged(view, scrollState);
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (loadable != null) {
                        loadable.listViewIndex = view.getFirstVisiblePosition();
                        View v = view.getChildAt(0);
                        loadable.listViewTop = (v == null) ? 0 : v.getTop();
                    }
                    if (skipLogic != null) {
                        skipLogic.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                    }
                }
            });

            compound.addView(listView);

            if (loadable.isThreadMode()) {
                skip = new ImageView(baseActivity);
                skip.setImageResource(R.drawable.skip_arrow_down);
                skip.setVisibility(View.GONE);
                compound.addView(skip, Utils.WRAP_PARAMS);

                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) skip.getLayoutParams();
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.setMargins(0, 0, Utils.dp(8), Utils.dp(8));
                skip.setLayoutParams(params);

                skipLogic = new SkipLogic(skip, listView);
            }

            if (container != null) {
                container.setView(compound);
            }
        }

        postAdapter.setErrorMessage(null);

        if (append) {
            postAdapter.appendList(posts);
        } else {
            postAdapter.setList(posts);
        }
    }

    private void setEmpty() {
        postAdapter = null;

        if (container != null) {
            container.setView(null);
        }

        if (listView != null) {
            listView.setOnScrollListener(null);
            listView = null;
        }

        skip = null;
        skipLogic = null;
    }

    @Override
    public void onThreadLoadError(VolleyError error) {
        if (error instanceof EndOfLineException) {
            postAdapter.setEndOfLine(true);
        } else {
            if (postAdapter == null) {
                if (container != null) {
                    container.setView(getLoadErrorTextView(error));
                }
            } else {
                postAdapter.setErrorMessage(getLoadErrorText(error));
            }
        }
    }

    /**
     * Returns an TextView containing the appropriate error message
     *
     * @param error
     * @return
     */
    private TextView getLoadErrorTextView(VolleyError error) {
        String errorMessage = getLoadErrorText(error);

        TextView view = new TextView(getActivity());
        view.setLayoutParams(Utils.MATCH_PARAMS);
        view.setText(errorMessage);
        view.setTextSize(24f);
        view.setGravity(Gravity.CENTER);

        return view;
    }

    private String getLoadErrorText(VolleyError error) {
        String errorMessage = "error";

        if ((error instanceof NoConnectionError) || (error instanceof NetworkError)) {
            errorMessage = getActivity().getString(R.string.thread_load_failed_network);
        } else if (error instanceof ServerError) {
            errorMessage = getActivity().getString(R.string.thread_load_failed_server);
        } else {
            errorMessage = getActivity().getString(R.string.thread_load_failed_parsing);
        }

        return errorMessage;
    }

    @Override
    public void onOPClicked(Post post) {
        baseActivity.onOPClicked(post);
    }

    @Override
    public void onThumbnailClicked(Post source) {
        if (postAdapter != null) {
            ImageViewActivity.setAdapter(postAdapter, source.no);

            Intent intent = new Intent(baseActivity, ImageViewActivity.class);
            baseActivity.startActivity(intent);
            baseActivity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void onScrollTo(Post post) {
        if (postAdapter != null) {
            postAdapter.scrollToPost(post);
        }
    }

    private static class SkipLogic {
        private final ImageView skip;
        private int lastFirstVisibleItem;
        private int lastTop;
        private boolean up = false;
        private final AbsListView listView;

        public SkipLogic(ImageView skipView, AbsListView list) {
            skip = skipView;
            listView = list;
            skip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (up) {
                        listView.setSelection(0);
                    } else {
                        listView.setSelection(listView.getCount() - 1);
                    }
                    skip.setVisibility(View.GONE);
                }
            });
        }

        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                skip.setVisibility(View.VISIBLE);
            }
        }

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            View v = view.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();

            if (firstVisibleItem == lastFirstVisibleItem) {
                if (top > lastTop) {
                    onUp();
                } else if (top < lastTop) {
                    onDown();
                }
            } else {
                if (firstVisibleItem > lastFirstVisibleItem) {
                    onDown();
                } else {
                    onUp();
                }
            }
            lastFirstVisibleItem = firstVisibleItem;
            lastTop = top;
        }

        private void onUp() {
            skip.setImageResource(R.drawable.skip_arrow_up);
            up = true;
        }

        private void onDown() {
            skip.setImageResource(R.drawable.skip_arrow_down);
            up = false;
        }
    }
}
