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
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.view.PostView;

import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * A DialogFragment that shows a list of posts. Use the newInstance method for
 * instantiating.
 */
public class PostRepliesFragment extends DialogFragment {
    private ListView listView;

    private List<Post> posts;
    private ThreadManager manager;
    private boolean callback = true;

    public static PostRepliesFragment newInstance(List<Post> posts, ThreadManager manager) {
        PostRepliesFragment fragment = new PostRepliesFragment();
        fragment.posts = posts;
        fragment.manager = manager;

        return fragment;
    }

    public void dismissNoCallback() {
        callback = false;
        dismiss();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NO_TITLE, 0);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        if (callback && manager != null) {
            manager.onPostRepliesPop();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup unused, Bundle savedInstanceState) {
        View container = inflater.inflate(R.layout.post_replies, null);

        listView = (ListView) container.findViewById(R.id.post_list);

        container.findViewById(R.id.replies_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        container.findViewById(R.id.replies_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.closeAllPostFragments();
                dismiss();
            }
        });

        return container;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (posts == null) {
            // Restoring from background.
            dismiss();
        } else {
            ArrayAdapter<Post> adapter = new ArrayAdapter<Post>(getActivity(), 0) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    PostView postView = null;
                    if (convertView instanceof PostView) {
                        postView = (PostView) convertView;
                    } else {
                        postView = new PostView(getActivity());
                    }

                    final Post p = getItem(position);

                    postView.setPost(p, manager);
                    postView.setOnClickListeners(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            manager.closeAllPostFragments();
                            dismiss();
                            manager.highlightPost(p);
                            manager.scrollToPost(p);
                        }
                    });

                    return postView;
                }
            };

            adapter.addAll(posts);
            listView.setAdapter(adapter);
        }
    }
}
