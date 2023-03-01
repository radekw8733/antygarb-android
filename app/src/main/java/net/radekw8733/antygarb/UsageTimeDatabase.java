package net.radekw8733.antygarb;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {UsageTimeEntry.class}, version = 1)
@TypeConverters({net.radekw8733.antygarb.TypeConverters.class})
public abstract class UsageTimeDatabase extends RoomDatabase {
    public abstract UsageTimeDao usageTimeDao();
}
