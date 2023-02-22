package net.radekw8733.antygarb;

import androidx.room.Dao;
import androidx.room.Insert;

@Dao
public interface UsageTimeDao {
    @Insert
    void insert(UsageTimeEntry entry);
}
