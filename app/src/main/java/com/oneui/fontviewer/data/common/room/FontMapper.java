package com.oneui.fontviewer.data.common.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.oneui.fontviewer.data.common.model.FontItem;

import java.util.ArrayList;
import java.util.List;

// الجسر الوحيد بين كيان Room (FontEntity) ونموذج الواجهة (FontItem).
// يبقى Room محصورًا في طبقة البيانات، والـ Repositories تُرجع FontItem فقط.
public final class FontMapper {

    private FontMapper() {}

    @NonNull
    public static FontItem toItem(@NonNull FontEntity e) {
        FontItem item = new FontItem(e.getPath(), e.getFileName());
        item.setId(e.getId());
        item.setRealName(e.getRealName());
        item.setSize(e.getSize());
        item.setLastModified(e.getLastModified());
        item.setFontType(e.getFontType());
        item.setTtcIndex(e.getTtcIndex());
        item.setSystemFont(e.isSystemFont());
        item.setVariableFont(e.isVariableFont());
        item.setCached(e.isCached());
        item.setLastAccessTime(e.getLastAccessTime());
        item.setAccessCount(e.getAccessCount());
        item.setCreatedAt(e.getCreatedAt());
        item.setUpdatedAt(e.getUpdatedAt());
        item.setWeightWidthLabel(e.getWeightWidthLabel());
        item.setFavorite(e.isFavorite());
        item.setTrashed(e.isTrashed());
        item.setDeletedAt(e.getDeletedAt());
        item.setOriginalPath(e.getOriginalPath());
        return item;
    }

    @NonNull
    public static FontEntity toEntity(@NonNull FontItem item) {
        FontEntity e = new FontEntity(item.getPath(), item.getFileName());
        e.setId(item.getId());
        e.setRealName(item.getRealName());
        e.setSize(item.getSize());
        e.setLastModified(item.getLastModified());
        e.setFontType(item.getFontType());
        e.setTtcIndex(item.getTtcIndex());
        e.setSystemFont(item.isSystemFont());
        e.setVariableFont(item.isVariableFont());
        e.setCached(item.isCached());
        e.setLastAccessTime(item.getLastAccessTime());
        e.setAccessCount(item.getAccessCount());
        e.setCreatedAt(item.getCreatedAt());
        e.setUpdatedAt(item.getUpdatedAt());
        e.setWeightWidthLabel(item.getWeightWidthLabel());
        e.setFavorite(item.isFavorite());
        e.setTrashed(item.isTrashed());
        e.setDeletedAt(item.getDeletedAt());
        e.setOriginalPath(item.getOriginalPath());
        return e;
    }

    @Nullable
    public static List<FontItem> toItems(@Nullable List<FontEntity> entities) {
        if (entities == null) return null;
        List<FontItem> result = new ArrayList<>(entities.size());
        for (FontEntity e : entities) {
            result.add(toItem(e));
        }
        return result;
    }

    @Nullable
    public static List<FontEntity> toEntities(@Nullable List<FontItem> items) {
        if (items == null) return null;
        List<FontEntity> result = new ArrayList<>(items.size());
        for (FontItem item : items) {
            result.add(toEntity(item));
        }
        return result;
    }

    // تحويل LiveData قادم من Room إلى LiveData من FontItem (يعمل فقط أثناء وجود مراقب، كسلوك Room نفسه).
    @NonNull
    public static LiveData<List<FontItem>> toItemsLive(@NonNull LiveData<List<FontEntity>> source) {
        MediatorLiveData<List<FontItem>> out = new MediatorLiveData<>();
        out.addSource(source, list -> out.setValue(toItems(list)));
        return out;
    }

    @NonNull
    public static LiveData<FontItem> toItemLive(@NonNull LiveData<FontEntity> source) {
        MediatorLiveData<FontItem> out = new MediatorLiveData<>();
        out.addSource(source, e -> out.setValue(e == null ? null : toItem(e)));
        return out;
    }
}
