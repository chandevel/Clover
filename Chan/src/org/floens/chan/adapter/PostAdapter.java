package org.floens.chan.adapter;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.entity.Post;
import org.floens.chan.manager.ThreadManager;
import org.floens.chan.view.PostView;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;


public class PostAdapter extends BaseAdapter {
    private final Context activity;
    private final ThreadManager threadManager;
    private boolean endOfLine;
    private int count = 0;
    private final List<Post> postList = new ArrayList<Post>();
    
    public PostAdapter(Context activity, ThreadManager threadManager) {
        this.activity = activity;
        this.threadManager = threadManager;
    }

    @Override
    public int getCount() {
        if (threadManager.getLoadable().isBoardMode()) {
            return count + 1;
        } else {
            return count;
        }
    }

    @Override
    public Post getItem(int position) {
        return postList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= getCount() - 1 && !endOfLine) {
            // Try to load more posts
            threadManager.loadMore();
        }
        
        if (position >= count) {
            if (endOfLine) {
                TextView textView = new TextView(activity);
                textView.setText(activity.getString(R.string.end_of_line));
                int padding = activity.getResources().getDimensionPixelSize(R.dimen.general_padding);
                textView.setPadding(padding, padding, padding, padding);
                return textView;
            } else {
                return new ProgressBar(activity);
            }
        } else {
            PostView postView = null;
            
            if (position >= 0 && position < postList.size()) {
                if (convertView != null && convertView instanceof PostView) {
                    postView = (PostView) convertView;
                } else {
                    postView = new PostView(activity);
                }
                
                postView.setPost(postList.get(position), threadManager);
            } else {
                Log.e("Chan", "PostAdapter: Invalid index: " + position + ", size: " + postList.size() + ", count: " + count);
                return new View(activity);
            }
            
            return postView;
        }
    }
    
    public void addToList(List<Post> list){
        count += list.size();
        postList.addAll(list);
        
        notifyDataSetChanged();
    }
    
    public List<Post> getList() {
        return postList;
    }
    
    public void setEndOfLine(boolean endOfLine) {
        this.endOfLine = endOfLine;
        
        notifyDataSetChanged();
    }
}





