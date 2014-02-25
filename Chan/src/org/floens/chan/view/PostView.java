package org.floens.chan.view;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.manager.ThreadManager;
import org.floens.chan.model.Post;
import org.floens.chan.model.PostLinkable;
import org.floens.chan.net.ChanUrls;
import org.floens.chan.utils.IconCache;
import org.floens.chan.utils.ViewUtils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

public class PostView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener {
    private final static LinearLayout.LayoutParams matchParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    private final static LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    private final static LinearLayout.LayoutParams matchWrapParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    private final static LinearLayout.LayoutParams wrapMatchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
    
    private final Activity context;
    
    private ThreadManager manager;
    private Post post;
    
    private boolean isBuild = false;
    private NetworkImageView imageView;
    private TextView titleView;
    private TextView commentView;
    private TextView repliesCountView;
    private LinearLayout iconView;
    private ImageView stickyView;
    private NetworkImageView countryView;
    
    /**
     * Represents a post.
     * Use setPost(Post ThreadManager) to fill it with data.
     * setPost can be called multiple times (useful for ListView).
     * @param activity
     */
    public PostView(Context activity) {
        super(activity);
        context = (Activity) activity;
        init();
    }
    
    public PostView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
        context = (Activity) activity;
        init();
    }
    
    public PostView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
        context = (Activity) activity;
        init();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
        if (post != null) {
            post.setLinkableListener(null);
        }
    }
    
    public void setPost(final Post post, final ThreadManager manager) {
        this.post = post;
        this.manager = manager;
        
        buildView(context);
        
        if (post.hasImage) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageUrl(post.thumbnailUrl, ChanApplication.getImageLoader());
        } else {
            imageView.setVisibility(View.GONE);
            imageView.setImageUrl(null, null);
        }
        
        CharSequence total = new SpannableString("");
        
        if (!TextUtils.isEmpty(post.subject)) {
            SpannableString subject = new SpannableString(post.subject);
            subject.setSpan(new ForegroundColorSpan(Color.argb(255, 15, 12, 93)), 0, subject.length(), 0);
            total = TextUtils.concat(total, subject, "\n");
        }
        
        if (!TextUtils.isEmpty(post.name)) {
            SpannableString name = new SpannableString(post.name);
            name.setSpan(new ForegroundColorSpan(Color.argb(255, 17, 119, 67)), 0, name.length(), 0);
            total = TextUtils.concat(total, name, " ");
        }
        
        if (!TextUtils.isEmpty(post.tripcode)) {
            SpannableString tripcode = new SpannableString(post.tripcode);
            tripcode.setSpan(new ForegroundColorSpan(Color.argb(255, 17, 119, 67)), 0, tripcode.length(), 0);
            tripcode.setSpan(new AbsoluteSizeSpan(10, true), 0, tripcode.length(), 0);
            total = TextUtils.concat(total, tripcode, " ");
        }
        
        if (!TextUtils.isEmpty(post.id)) {
            SpannableString id = new SpannableString("  ID: " + post.id + "  ");
            IDColor c = computeIDColor(post.id);
            id.setSpan(new ForegroundColorSpan(c.color), 0, id.length(), 0);
            id.setSpan(new BackgroundColorSpan(c.backgroundColor), 0, id.length(), 0);
            id.setSpan(new AbsoluteSizeSpan(10, true), 0, id.length(), 0);
            total = TextUtils.concat(total, id, " ");
        }
        
        if (!TextUtils.isEmpty(post.capcode)) {
            SpannableString tripcode = new SpannableString("Capcode: " + post.capcode);
            tripcode.setSpan(new ForegroundColorSpan(Color.argb(255, 255, 0, 0)), 0, tripcode.length(), 0);
            tripcode.setSpan(new AbsoluteSizeSpan(10, true), 0, tripcode.length(), 0);
            total = TextUtils.concat(total, tripcode, " ");
        }
        
        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(post.time * 1000L, System.currentTimeMillis(), 
                DateUtils.SECOND_IN_MILLIS, 0);
        
        SpannableString date = new SpannableString("No." + post.no + " " + relativeTime);
        date.setSpan(new ForegroundColorSpan(Color.argb(255, 100, 100, 100)), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpan(10, true), 0, date.length(), 0);
        total = TextUtils.concat(total, date, " ");
        
        titleView.setText(total);
        
        if (!TextUtils.isEmpty(post.comment)) {
            commentView.setVisibility(View.VISIBLE);
            commentView.setText(post.comment);
            commentView.setMovementMethod(LinkMovementMethod.getInstance());
            commentView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    PostView.this.onClick(v);
                }
            });
            
            commentView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    return PostView.this.onLongClick(v);
                }
            });
            
            if (manager.getLoadable().isThreadMode()) {
                post.setLinkableListener(this);
            }
            
            if (manager.getLoadable().isBoardMode()) {
                int maxHeight = context.getResources().getDimensionPixelSize(R.dimen.post_max_height);
                commentView.setMaxHeight(maxHeight);
            } else {
                commentView.setMaxHeight(10000);
            }
        } else {
            commentView.setVisibility(View.GONE);
            commentView.setText("");
            commentView.setOnClickListener(null);
            commentView.setOnLongClickListener(null);
            post.setLinkableListener(null);
        }
        
        if ((post.isOP && manager.getLoadable().isBoardMode() && post.replies > 0) || (post.repliesFrom.size() > 0)) {
            repliesCountView.setVisibility(View.VISIBLE);
            
            String text = "";
            
            int count = manager.getLoadable().isBoardMode() ? post.replies : post.repliesFrom.size();
            
            if (count > 1) {
                text = count + " " + context.getString(R.string.multiple_replies);
            } else if (count == 1) {
                text = count + " " + context.getString(R.string.one_reply);
            }
            
            if (manager.getLoadable().isThreadMode()) {
                repliesCountView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        manager.showPostReplies(post);
                    }
                });
            }
            
            repliesCountView.setText(text);
        } else {
            repliesCountView.setVisibility(View.GONE);
            repliesCountView.setOnClickListener(null);
        }
        
        boolean showCountryFlag = !TextUtils.isEmpty(post.country);
        boolean showStickyIcon = post.sticky;
        
        iconView.setVisibility((showCountryFlag || showStickyIcon) ? View.VISIBLE : View.GONE);
        
        stickyView.setVisibility(showStickyIcon ? View.VISIBLE : View.GONE);
        if (showCountryFlag) {
            countryView.setVisibility(View.VISIBLE);
            countryView.setImageUrl(ChanUrls.getCountryFlagUrl(post.country), ChanApplication.getImageLoader());
        } else {
            countryView.setVisibility(View.GONE);
        }
        
        if (post.isOP && manager.getLoadable().isBoardMode()) {
            setClickable(true);
            setFocusable(true);
            
            ViewUtils.setPressedDrawable(this);
        }
    }
    
    private void init() {
        setOnClickListener(this);
        setOnLongClickListener(this);
    }
    
    private void buildView(final Context context) {
        if (isBuild) return;
        isBuild = true;
        
        Resources resources = context.getResources();
        int postPadding = resources.getDimensionPixelSize(R.dimen.post_padding);
        int commentPadding = resources.getDimensionPixelSize(R.dimen.post_comment_padding);
        int iconPadding = resources.getDimensionPixelSize(R.dimen.post_icon_padding);
        int iconWidth = resources.getDimensionPixelSize(R.dimen.post_icon_width);
        int iconHeight = resources.getDimensionPixelSize(R.dimen.post_icon_height);
        int imageSize = resources.getDimensionPixelSize(R.dimen.thumbnail_size);
        
        LinearLayout full = new LinearLayout(context);
        full.setLayoutParams(matchParams);
        full.setOrientation(HORIZONTAL);
        
        // Create thumbnail
        imageView = new NetworkImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setFadeIn(100);
        
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.onThumbnailClicked(post);
            }
        });
        
        LinearLayout left = new LinearLayout(context);
        left.setOrientation(VERTICAL);
        left.setBackgroundColor(0xffdddddd);
        
        left.addView(imageView, new LinearLayout.LayoutParams(imageSize, imageSize));
        
        full.addView(left, wrapMatchParams);
        full.setMinimumHeight(imageSize);
        
        LinearLayout right = new LinearLayout(context);
        right.setOrientation(VERTICAL);
        
            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);
            
                titleView = new TextView(context);
                titleView.setTextSize(14);
                titleView.setPadding(postPadding, postPadding, postPadding, 0);
                header.addView(titleView, wrapParams);
                
            right.addView(header, matchWrapParams);
            
            iconView = new LinearLayout(context);
            iconView.setOrientation(HORIZONTAL);
            iconView.setPadding(postPadding, iconPadding, postPadding, 0);
            
                stickyView = new ImageView(context);
                stickyView.setImageBitmap(IconCache.stickyIcon);
                iconView.addView(stickyView, new LinearLayout.LayoutParams(iconWidth, iconHeight));
                
                countryView = new NetworkImageView(context);
                countryView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconView.addView(countryView, new LinearLayout.LayoutParams(iconWidth, iconHeight));
            
            right.addView(iconView, matchWrapParams);
            
            commentView = new TextView(context);
            commentView.setTextSize(15);
            commentView.setPadding(postPadding, commentPadding, postPadding, commentPadding);
            right.addView(commentView, matchWrapParams);
            
            repliesCountView = new TextView(context);
            repliesCountView.setTextColor(Color.argb(255, 100, 100, 100));
            repliesCountView.setPadding(postPadding, postPadding, postPadding, postPadding);
            repliesCountView.setTextSize(14);
            ViewUtils.setPressedDrawable(repliesCountView);
            
            right.addView(repliesCountView, wrapParams);
            
        full.addView(right, matchWrapParams);
        
        addView(full, matchParams);
    }
    
    private IDColor computeIDColor(String id) {
        // Stolen from the 4chan extension
        int hash = post.id.hashCode();
        
        int r = (hash >> 24) & 0xff;
        int g = (hash >> 16) & 0xff;
        int b = (hash >> 8) & 0xff;
        
        IDColor c = new IDColor();
        c.color = (0xff << 24) + (r << 16) + (g << 8) + b;
        c.backgroundColor = ((r * 0.299f) + (g * 0.587f) + (b * 0.114f)) > 125 ? 0xff636363 : 0x00000000;
        return c;
    }
    
    public void onLinkableClick(PostLinkable linkable) {
        manager.onPostLinkableClicked(linkable);
    }
    
    @Override
    public void onClick(View v) {
        manager.onPostClicked(post);
    }

    @Override
    public boolean onLongClick(View v) {
        manager.onPostLongClicked(post);
        
        return true;
    }
    
    private static class IDColor {
        public int color;
        public int backgroundColor;
    }
}





