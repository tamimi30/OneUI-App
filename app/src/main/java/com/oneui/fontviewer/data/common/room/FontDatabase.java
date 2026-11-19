package com.oneui.fontviewer.data.common.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
    entities = {FontEntity.class},
    version = 4,          
    exportSchema = false
)
public abstract class FontDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "oneui_fonts_database";
    private static volatile FontDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    
    public abstract FontDao fontDao();
    
    public static FontDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (FontDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            FontDatabase.class,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration() 
                    .build();
                }
            }
        }
        return INSTANCE;
    }
    
    
}
