package net.radekw8733.antygarb;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {UsageTimeEntry.class}, version = 1)
public abstract class UsageTimeDatabase extends RoomDatabase {
    public abstract UsageTimeDao usageTimeDao();
}
