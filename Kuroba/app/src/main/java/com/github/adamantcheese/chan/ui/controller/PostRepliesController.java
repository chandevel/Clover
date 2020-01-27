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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.helper.PostPopupHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.LoadView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;

public class PostRepliesController
        extends BaseFloatingController {
    private PostPopupHelper postPopupHelper;
    private ThreadPresenter presenter;

    private boolean first = true;

    private LoadView loadView;
    private ListView listView;
    private PostPopupHelper.RepliesData displayingData;

    public PostRepliesController(Context context, PostPopupHelper postPopupHelper, ThreadPresenter presenter) {
        super(context);
        this.postPopupHelper = postPopupHelper;
        this.presenter = presenter;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Clicking outside the popup view
        view.setOnClickListener(v -> postPopupHelper.pop());

        loadView = view.findViewById(R.id.loadview);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_post_replies_container;
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        if (listView == null) {
            return null;
        } else {
            ThumbnailView thumbnail = null;
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (view instanceof PostCellInterface) {
                    PostCellInterface postView = (PostCellInterface) view;
                    Post post = postView.getPost();

                    if (!post.images.isEmpty()) {
                        for (int j = 0; j < post.images.size(); j++) {
                            if (post.images.get(j).equalUrl(postImage)) {
                                thumbnail = postView.getThumbnailView(postImage);
                            }
                        }
                    }
                }
            }
            return thumbnail;
        }
    }

    public void setPostRepliesData(Loadable loadable, PostPopupHelper.RepliesData data) {
        displayData(loadable, data);
    }

    public List<Post> getPostRepliesData() {
        return displayingData.posts;
    }

    public void scrollTo(int displayPosition) {
        listView.smoothScrollToPosition(displayPosition);
    }

    private void displayData(Loadable loadable, final PostPopupHelper.RepliesData data) {
        displayingData = data;

        View dataView;
        if (ChanSettings.repliesButtonsBottom.get()) {
            dataView = inflate(context, R.layout.layout_post_replies_bottombuttons);
        } else {
            dataView = inflate(context, R.layout.layout_post_replies);
        }

        listView = dataView.findViewById(R.id.post_list);
        listView.setDivider(null);
        listView.setDividerHeight(0);

        View repliesBack = dataView.findViewById(R.id.replies_back);
        repliesBack.setOnClickListener(v -> postPopupHelper.pop());

        View repliesClose = dataView.findViewById(R.id.replies_close);
        repliesClose.setOnClickListener(v -> postPopupHelper.popAll());

        Drawable backDrawable = ThemeHelper.getTheme().backDrawable.makeDrawable(context);
        Drawable doneDrawable = ThemeHelper.getTheme().doneDrawable.makeDrawable(context);

        TextView repliesBackText = dataView.findViewById(R.id.replies_back_icon);
        TextView repliesCloseText = dataView.findViewById(R.id.replies_close_icon);
        repliesBackText.setCompoundDrawablesWithIntrinsicBounds(backDrawable, null, null, null);
        repliesCloseText.setCompoundDrawablesWithIntrinsicBounds(doneDrawable, null, null, null);

        ArrayAdapter<Post> adapter = new ArrayAdapter<Post>(context, 0) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                PostCellInterface postCell;
                if (convertView instanceof PostCellInterface && !ChanSettings.shiftPostFormat.get()) {
                    postCell = (PostCellInterface) convertView;
                } else {
                    postCell = (PostCellInterface) inflate(context, R.layout.cell_post, parent, false);
                }

                final Post p = getItem(position);
                boolean showDivider = position < getCount() - 1;
                postCell.setPost(
                        loadable,
                        p,
                        presenter,
                        true,
                        false,
                        false,
                        data.forPost.no,
                        showDivider,
                        ChanSettings.PostViewMode.LIST,
                        false,
                        ThemeHelper.getTheme()
                );

                return (View) postCell;
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
}
