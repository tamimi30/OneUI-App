package com.oneui.fontviewer.drawer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.AppScreen;

public class DrawerListAdapter extends RecyclerView.Adapter<DrawerListViewHolder> {

    private static final int VIEW_TYPE_SEPARATOR = 0;
    private static final int VIEW_TYPE_ITEM      = 1;

    private final Context mContext;

    private final List<AppScreen> mScreenList;

    private final DrawerListener mListener;

    private AppScreen mSelectedScreen = AppScreen.FONT_VIEWER;


    public interface DrawerListener {
        boolean onDrawerItemSelected(AppScreen screen);
    }


    public DrawerListAdapter(
            @NonNull Context context, List<AppScreen> screenList, DrawerListener listener) {
        mContext    = context;
        mScreenList = screenList;
        mListener   = listener;
    }


    @Override
    public int getItemCount() {
        return mScreenList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mScreenList.get(position) == null ? VIEW_TYPE_SEPARATOR : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public DrawerListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        boolean isSeparator = viewType == VIEW_TYPE_SEPARATOR;
        int layoutRes = isSeparator ? R.layout.drawer_list_separator
                                    : R.layout.drawer_list_item;
        View view = inflater.inflate(layoutRes, parent, false);
        return new DrawerListViewHolder(view, isSeparator);
    }

    @Override
    public void onBindViewHolder(@NonNull DrawerListViewHolder holder, int position) {
        if (holder.isSeparator()) {
            return;
        }

        AppScreen screen = mScreenList.get(position);
        if (screen == null) {
            return;
        }

        int iconRes = getIconForScreen(screen);
        String title = getTitleForScreen(screen);

        if (iconRes != 0) {
            holder.setIcon(iconRes);
        }
        if (!title.isEmpty()) {
            holder.setTitle(title);
        }

        holder.setSelected(screen == mSelectedScreen);

        holder.itemView.setOnClickListener(v -> {
            final int adapterPos = holder.getBindingAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) {
                return;
            }

            AppScreen clickedScreen = mScreenList.get(adapterPos);
            if (clickedScreen == null) {
                return;
            }

            boolean selectionChanged = false;
            if (mListener != null) {
                selectionChanged = mListener.onDrawerItemSelected(clickedScreen);
            }

            if (selectionChanged) {
                setSelectedItem(clickedScreen);
            }
        });
    }


    private int getIconForScreen(AppScreen screen) {
        switch (screen) {
            case HOME:
                return dev.oneuiproject.oneui.R.drawable.ic_oui_home_outline;
            case FONT_VIEWER:
                return R.drawable.ic_oui_text_style_default;
            case LOCAL_FONTS:
                return dev.oneuiproject.oneui.R.drawable.ic_oui_device_outline;
            case SYSTEM_FONTS:
                return R.drawable.ic_android_3;
            case FAVORITES:
                return dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off;
            case TRASH:
                return dev.oneuiproject.oneui.R.drawable.ic_oui_delete_outline;
            default:
                return 0;
        }
    }

    private String getTitleForScreen(AppScreen screen) {
        switch (screen) {
            case HOME:
                return mContext.getString(R.string.drawer_home);
            case FONT_VIEWER:
                return mContext.getString(R.string.drawer_font_viewer);
            case LOCAL_FONTS:
                return mContext.getString(R.string.drawer_local_fonts);
            case SYSTEM_FONTS:
                return mContext.getString(R.string.drawer_system_fonts);
            case FAVORITES:
                return mContext.getString(R.string.drawer_favorites);
            case TRASH:
                return mContext.getString(R.string.drawer_trash);
            default:
                return "";
        }
    }


    public void setSelectedItem(AppScreen screen) {
        if (screen == null || screen == mSelectedScreen) {
            return;
        }

        AppScreen prev = mSelectedScreen;
        mSelectedScreen = screen;

        int prevPos = indexOfScreen(prev);
        int newPos  = indexOfScreen(screen);

        if (prevPos >= 0) {
            notifyItemChanged(prevPos);
        }
        if (newPos >= 0) {
            notifyItemChanged(newPos);
        }
    }

    private int indexOfScreen(AppScreen screen) {
        if (screen == null) {
            return -1;
        }
        for (int i = 0; i < mScreenList.size(); i++) {
            if (mScreenList.get(i) == screen) {
                return i;
            }
        }
        return -1;
    }

    
}
