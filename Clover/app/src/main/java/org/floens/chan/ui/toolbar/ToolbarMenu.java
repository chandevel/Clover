package org.floens.chan.ui.toolbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.floens.chan.R;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ToolbarMenu extends LinearLayout {
    private List<ToolbarMenuItem> items = new ArrayList<>();

    public ToolbarMenu(Context context) {
        super(context);
        init();
    }

    public ToolbarMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ToolbarMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

//        overflowItem = new ToolbarMenuItem(getContext(), this, 100, R.drawable.ic_more_vert_white_24dp, 10 + 32);
//
//        List<ToolbarMenuItemSubMenu.SubItem> subItems = new ArrayList<>();
//        subItems.add(new ToolbarMenuItemSubMenu.SubItem(1, "Sub 1"));
//        subItems.add(new ToolbarMenuItemSubMenu.SubItem(2, "Sub 2"));
//        subItems.add(new ToolbarMenuItemSubMenu.SubItem(3, "Sub 3"));
//
//        ToolbarMenuItemSubMenu sub = new ToolbarMenuItemSubMenu(getContext(), overflowItem.getView(), subItems);
//        overflowItem.setSubMenu(sub);
//
//        addItem(overflowItem);
    }

    public ToolbarMenuItem addItem(ToolbarMenuItem item) {
        items.add(item);
        ImageView icon = item.getView();
        if (icon != null) {
            int viewIndex = Math.min(getChildCount(), item.getId());
            addView(icon, viewIndex);
        }
        return item;
    }

    public ToolbarMenuItem createOverflow(ToolbarMenuItem.ToolbarMenuItemCallback callback) {
        ToolbarMenuItem overflow = addItem(new ToolbarMenuItem(getContext(), callback, 100, R.drawable.ic_more));
        ImageView overflowImage = overflow.getView();
        // 36dp
        overflowImage.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(54)));
        int p = dp(16);
        overflowImage.setPadding(0, 0, p, 0);

        return overflow;
    }
}
