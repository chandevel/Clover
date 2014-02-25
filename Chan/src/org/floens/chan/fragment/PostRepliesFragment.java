package org.floens.chan.fragment;

import java.util.List;

import org.floens.chan.R;
import org.floens.chan.manager.ThreadManager;
import org.floens.chan.model.Post;
import org.floens.chan.view.PostView;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * A DialogFragment that shows a list of posts. Use the newInstance method for instantiating.
 */
public class PostRepliesFragment extends DialogFragment {
    private Context context;
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
        context = inflater.getContext();
        
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
                    
                    postView.setPost(getItem(position), manager);
                    
                    return postView;
                }
            };
            
            adapter.addAll(posts);
            listView.setAdapter(adapter);
        }
    }
}





