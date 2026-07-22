package com.oneui.fontviewer.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.oneui.fontviewer.data.dao.FontDao;
import com.oneui.fontviewer.data.entity.FontEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppDatabase - قاعدة البيانات الرئيسية للتطبيق
 *
 * ★ الإصدار 2: إضافة عمود weight_width_label في جدول fonts ★
 * يُخزّن وصف الوزن والعرض المُستخرج من جدول OS/2 لكل خط.
 *
 * ★ الإصدار 3: إضافة عمود is_favorite في جدول fonts ★
 * يُخزّن حالة المفضلة لكل خط (0 = غير مفضل، 1 = مفضل).
 *
 * ★ الإصدار 4: إضافة أعمدة سلة المحذوفات في جدول fonts ★
 * - is_trashed  : يُحدّد ما إذا كان الخط في سلة المحذوفات (0 = لا، 1 = نعم).
 * - deleted_at  : يُخزّن وقت النقل إلى السلة بالميلي ثانية (Unix timestamp)
 *                 لحساب الـ 30 يوماً المتبقية قبل الحذف النهائي.
 * - original_path: يُخزّن المسار الأصلي للملف قبل نقله إلى السلة
 *                  لاستخدامه عند استعادة الخط إلى موقعه الأصلي.
 *
 * بما أن fallbackToDestructiveMigration() مُفعَّل، تُعاد إنشاء
 * قاعدة البيانات تلقائياً عند ترقية التطبيق دون الحاجة لكتابة
 * Migration يدوي.
 */
@Database(
    entities = {FontEntity.class},
    version = 4,          // ★ رُفع من 3 إلى 4 بسبب إضافة أعمدة سلة المحذوفات ★
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "oneui_fonts_database";
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    public abstract FontDao fontDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration() // يُعيد بناء قاعدة البيانات عند اختلاف الإصدار
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    public static void destroyInstance() {
        INSTANCE = null;
    }
}
