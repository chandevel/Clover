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

import java.util.List;

import org.floens.chan.R;
import org.floens.chan.core.loader.EndOfLineException;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.BaseActivity;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.LoadView;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

public class ThreadFragment extends Fragment implements ThreadManager.ThreadManagerListener {
    private BaseActivity baseActivity;
    private ThreadManager threadManager;
    private Loadable loadable;

    private PostAdapter postAdapter;
    private LoadView container;
    private ListView listView;

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

    private void setEmpty() {
        postAdapter = null;

        if (container != null) {
            container.setView(null);
        }

        if (listView != null) {
            listView.setOnScrollListener(null);
            listView = null;
        }
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
            listView = new ListView(baseActivity);

            postAdapter = new PostAdapter(baseActivity, threadManager, listView);

            listView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            listView.setAdapter(postAdapter);
            listView.setSelectionFromTop(loadable.listViewIndex, loadable.listViewTop);

            if (threadManager.getLoadable().isThreadMode()) {
                listView.setOnScrollListener(new OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(AbsListView view, int scrollState) {
                    }

                    @Override
                    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                            int totalItemCount) {
                        if (loadable != null) {
                            loadable.listViewIndex = view.getFirstVisiblePosition();
                            View v = view.getChildAt(0);
                            loadable.listViewTop = (v == null) ? 0 : v.getTop();
                        }
                    }
                });
            }

            if (container != null) {
                container.setView(listView);
            }
        }

        postAdapter.setErrorMessage(null);

        if (append) {
            postAdapter.appendList(posts);
        } else {
            postAdapter.setList(posts);
        }
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
        view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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
}
