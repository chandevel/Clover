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
import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.loader.EndOfLineException;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.BaseActivity;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.utils.ImageSaver;
import org.floens.chan.utils.ThemeHelper;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLException;

public class ThreadFragment extends Fragment implements ThreadManager.ThreadManagerListener, PostAdapter.PostAdapterListener {
    private ThreadManager threadManager;
    private Loadable loadable;

    private PostAdapter postAdapter;
    private LoadView container;
    private AbsListView listView;
    private ImageView skip;
    private FilterView filterView;

    private SkipLogic skipLogic;
    private int highlightedPost = -1;
    private ThreadManager.ViewMode viewMode = ThreadManager.ViewMode.LIST;
    private boolean isFiltering = false;

    public static ThreadFragment newInstance(BaseActivity activity) {
        ThreadFragment fragment = new ThreadFragment();
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

    public void requestNextData() {
        threadManager.requestNextData();
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

    public void setViewMode(ThreadManager.ViewMode viewMode) {
        this.viewMode = viewMode;
    }

    public Loader getLoader() {
        return threadManager.getLoader();
    }

    public void startFiltering() {
        if (filterView != null) {
            isFiltering = true;
            filterView.setVisibility(View.VISIBLE);
            filterView.focusSearch();
        }
    }

    public void highlightPost(int no) {
        highlightedPost = no;
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
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
        if (loadable == null) {
            container.setView(getCenteredMessageView(R.string.thread_not_specified));
        }

        return container;
    }

    @Override
    public void onPostClicked(Post post) {
        if (loadable.isBoardMode() || loadable.isCatalogMode()) {
            ((BaseActivity) getActivity()).onOPClicked(post);
        } else if (loadable.isThreadMode() && isFiltering) {
            filterView.clearSearch();
            postAdapter.scrollToPost(post.no);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (loadable.isThreadMode()) {
            switch (item.getItemId()) {
                case R.id.action_download_album:
                    // Get the posts with images
                    ArrayList<Post> imagePosts = new ArrayList<>();
                    for (Post post : postAdapter.getList()) {
                        if (post.hasImage) {
                            imagePosts.add(post);
                        }
                    }
                    if (imagePosts.size() > 0) {
                        List<ImageSaver.DownloadPair> list = new ArrayList<>();

                        String folderName = Post.generateTitle(imagePosts.get(0), 10);

                        String filename;
                        for (Post post : imagePosts) {
                            filename = (ChanPreferences.getImageSaveOriginalFilename() ? post.tim : post.filename) + "." + post.ext;
                            list.add(new ImageSaver.DownloadPair(post.imageUrl, filename));
                        }

                        ImageSaver.getInstance().saveAll(getActivity(), folderName, list);
                    }

                    return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onThumbnailClicked(Post source) {
        if (postAdapter != null) {
            ImageViewActivity.launch(getActivity(), postAdapter, source.no, threadManager);
        }
    }

    @Override
    public void onScrollTo(int post) {
        if (postAdapter != null) {
            postAdapter.scrollToPost(post);
        }
    }

    @Override
    public void onRefreshView() {
        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onOpenThread(final Loadable thread, int highlightedPost) {
        ((BaseActivity) getActivity()).onOpenThread(thread);
        this.highlightedPost = highlightedPost;
    }

    @Override
    public ThreadManager.ViewMode getViewMode() {
        return viewMode;
    }

    @Override
    public void onThreadLoaded(ChanThread thread) {
        if (postAdapter == null) {
            if (container != null) {
                container.setView(createView());
            }
        }

        postAdapter.setStatusMessage(null);
        postAdapter.setThread(thread);

        if (highlightedPost >= 0) {
            threadManager.highlightPost(highlightedPost);
            postAdapter.scrollToPost(highlightedPost);
            highlightedPost = -1;
        }

        ((BaseActivity) getActivity()).onThreadLoaded(thread);
    }

    @Override
    public void onThreadLoadError(VolleyError error) {
        if (error instanceof EndOfLineException) {
            postAdapter.setEndOfLine(true);
        } else {
            if (postAdapter == null) {
                if (container != null) {
                    container.setView(getLoadErrorView(error));
                }
            } else {
                postAdapter.setStatusMessage(getLoadErrorText(error));
            }
        }

        highlightedPost = -1;
    }

    public void onFilterResults(String filter, int count, boolean all) {
        isFiltering = !all;

        if (filterView != null) {
            filterView.setText(filter, count, all);
        }
    }

    private RelativeLayout createView() {
        RelativeLayout compound = new RelativeLayout(getActivity());

        LinearLayout listViewContainer = new LinearLayout(getActivity());
        listViewContainer.setOrientation(LinearLayout.VERTICAL);

        filterView = new FilterView(getActivity());
        filterView.setVisibility(View.GONE);
        listViewContainer.addView(filterView, Utils.MATCH_WRAP_PARAMS);

        if (viewMode == ThreadManager.ViewMode.LIST) {
            ListView list = new ListView(getActivity());
            listView = list;
            postAdapter = new PostAdapter(getActivity(), threadManager, listView, this);
            listView.setAdapter(postAdapter);
            list.setSelectionFromTop(loadable.listViewIndex, loadable.listViewTop);
        } else if (viewMode == ThreadManager.ViewMode.GRID) {
            GridView grid = new GridView(getActivity());
            grid.setNumColumns(GridView.AUTO_FIT);
            int postGridWidth = getActivity().getResources().getDimensionPixelSize(R.dimen.post_grid_width);
            grid.setColumnWidth(postGridWidth);
            listView = grid;
            postAdapter = new PostAdapter(getActivity(), threadManager, listView, this);
            listView.setAdapter(postAdapter);
            listView.setSelection(loadable.listViewIndex);
        }

        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (!isFiltering) {
                    if (skipLogic != null) {
                        skipLogic.onScrollStateChanged(view, scrollState);
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isFiltering) {
                    if (loadable != null) {
                        int index = view.getFirstVisiblePosition();
                        View v = view.getChildAt(0);
                        int top = v == null ? 0 : v.getTop();
                        if (index != 0 || top != 0) {
                            loadable.listViewIndex = index;
                            loadable.listViewTop = top;
                        }
                    }
                    if (skipLogic != null) {
                        skipLogic.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                    }
                }
            }
        });

        listViewContainer.addView(listView, Utils.MATCH_PARAMS);

        compound.addView(listViewContainer, Utils.MATCH_PARAMS);

        if (loadable.isThreadMode()) {
            skip = new ImageView(getActivity());
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

        return compound;
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
        filterView = null;
    }

    private void doFilter(String filter) {
        if (postAdapter != null) {
            postAdapter.setFilter(filter);
        }
    }

    /**
     * Returns an TextView containing the appropriate error message
     *
     * @param error
     * @return
     */
    private View getLoadErrorView(VolleyError error) {
        String errorMessage = getLoadErrorText(error);

        LinearLayout wrapper = new LinearLayout(getActivity());
        wrapper.setLayoutParams(Utils.MATCH_PARAMS);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        TextView text = new TextView(getActivity());
        text.setLayoutParams(Utils.WRAP_PARAMS);
        text.setText(errorMessage);
        text.setTextSize(24f);
        wrapper.addView(text);

        Button retry = new Button(getActivity());
        retry.setText(R.string.thread_load_failed_retry);
        retry.setLayoutParams(Utils.WRAP_PARAMS);
        retry.setGravity(Gravity.CENTER);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (threadManager != null) {
                    reload();
                }
            }
        });

        wrapper.addView(retry);

        LinearLayout.LayoutParams retryParams = (LinearLayout.LayoutParams) retry.getLayoutParams();
        retryParams.topMargin = Utils.dp(12);
        retry.setLayoutParams(retryParams);

        return wrapper;
    }

    private String getLoadErrorText(VolleyError error) {
        String errorMessage;

        if (error.getCause() instanceof SSLException) {
            errorMessage = getString(R.string.thread_load_failed_ssl);
        } else if ((error instanceof NoConnectionError) || (error instanceof NetworkError)) {
            errorMessage = getString(R.string.thread_load_failed_network);
        } else if (error instanceof ServerError) {
            errorMessage = getString(R.string.thread_load_failed_server);
        } else {
            errorMessage = getString(R.string.thread_load_failed_parsing);
        }

        return errorMessage;
    }

    private View getCenteredMessageView(int stringResourceId) {
        LinearLayout layout = new LinearLayout(getActivity());
        layout.setGravity(Gravity.CENTER);
        TextView messageView = new TextView(getActivity());
        messageView.setText(getString(stringResourceId));
        layout.addView(messageView);

        return layout;
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

    public class FilterView extends LinearLayout {
        private SearchView searchView;
        private TextView textView;

        public FilterView(Context activity) {
            super(activity);
            init();
        }

        public FilterView(Context activity, AttributeSet attr) {
            super(activity, attr);
            init();
        }

        public FilterView(Context activity, AttributeSet attr, int style) {
            super(activity, attr, style);
            init();
        }

        public void focusSearch() {
            searchView.requestFocus();
        }

        public void clearSearch() {
            searchView.setQuery("", false);
            doFilter("");
            setVisibility(View.GONE);
        }

        private void init() {
            setOrientation(LinearLayout.VERTICAL);

            LinearLayout searchViewContainer = new LinearLayout(getContext());
            searchViewContainer.setOrientation(LinearLayout.HORIZONTAL);

            searchView = new SearchView(getContext());
            searchView.setIconifiedByDefault(false);
            searchView.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);
            searchViewContainer.addView(searchView);
            LinearLayout.LayoutParams searchViewParams = (LinearLayout.LayoutParams) searchView.getLayoutParams();
            searchViewParams.weight = 1f;
            searchViewParams.width = 0;
            searchViewParams.height = LayoutParams.MATCH_PARENT;
            searchView.setLayoutParams(searchViewParams);

            ImageView closeButton = new ImageView(getContext());
            searchViewContainer.addView(closeButton);
            closeButton.setImageResource(ThemeHelper.getInstance().getTheme().isLightTheme ? R.drawable.ic_action_cancel : R.drawable.ic_action_cancel_dark);
            LinearLayout.LayoutParams closeButtonParams = (LinearLayout.LayoutParams) closeButton.getLayoutParams();
            searchViewParams.width = Utils.dp(48);
            searchViewParams.height = LayoutParams.MATCH_PARENT;
            closeButton.setLayoutParams(closeButtonParams);
            Utils.setPressedDrawable(closeButton);
            int padding = Utils.dp(8);
            closeButton.setPadding(padding, padding, padding, padding);

            closeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearSearch();
                }
            });

            addView(searchViewContainer, new LayoutParams(LayoutParams.MATCH_PARENT, Utils.dp(48)));

            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    doFilter(query);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    doFilter(newText);
                    return false;
                }
            });

            textView = new TextView(getContext());
            textView.setGravity(Gravity.CENTER);
            addView(textView, new LayoutParams(LayoutParams.MATCH_PARENT, Utils.dp(28)));
        }

        private void setText(String filter, int count, boolean all) {
            if (all) {
                textView.setText("");
            } else {
                String posts = getContext().getString(count == 1 ? R.string.one_post : R.string.multiple_posts);
                String text = getContext().getString(R.string.search_results, Integer.toString(count), posts, filter);
                textView.setText(text);
            }
        }
    }
}
