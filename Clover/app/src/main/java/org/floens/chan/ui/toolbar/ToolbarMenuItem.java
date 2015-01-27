package org.floens.chan.ui.toolbar;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrDrawable;

public class ToolbarMenuItem implements View.OnClickListener, ToolbarMenuSubMenu.ToolbarMenuItemSubMenuCallback {
    private ToolbarMenuItemCallback callback;
    private int id;
    private ToolbarMenuSubMenu subMenu;

    private ImageView imageView;

    public ToolbarMenuItem(Context context, ToolbarMenuItem.ToolbarMenuItemCallback callback, int id, int drawable) {
        this(context, callback, id, context.getResources().getDrawable(drawable));
    }

    public ToolbarMenuItem(Context context, ToolbarMenuItem.ToolbarMenuItemCallback callback, int id, Drawable drawable) {
        this.id = id;
        this.callback = callback;

        if (drawable != null) {
            imageView = new ImageView(context);
            imageView.setOnClickListener(this);
            imageView.setFocusable(true);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
            int p = dp(16);
            imageView.setPadding(p, p, p, p);

            imageView.setImageDrawable(drawable);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //noinspection deprecation
                imageView.setBackgroundDrawable(getAttrDrawable(android.R.attr.selectableItemBackgroundBorderless));
            } else {
                //noinspection deprecation
                imageView.setBackgroundResource(R.drawable.gray_background_selector);
            }
        }
    }

    public void setSubMenu(ToolbarMenuSubMenu subMenu) {
        this.subMenu = subMenu;
        subMenu.setCallback(this);
    }

    public void showSubMenu() {
        subMenu.show();
    }

    @Override
    public void onClick(View v) {
        if (subMenu != null) {
            subMenu.show();
        }
        callback.onMenuItemClicked(this);
    }

    public int getId() {
        return id;
    }

    public ImageView getView() {
        return imageView;
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuSubItem item) {
        callback.onSubMenuItemClicked(this, item);
    }

    public interface ToolbarMenuItemCallback {
        public void onMenuItemClicked(ToolbarMenuItem item);

        public void onSubMenuItemClicked(ToolbarMenuItem parent, ToolbarMenuSubItem item);
    }
}
