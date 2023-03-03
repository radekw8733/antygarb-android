package net.radekw8733.antygarb;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UsageTimeDao {
    @Insert
    void insert(UsageTimeEntry entry);
    @Query("SELECT * FROM UsageTimeEntry")
    List<UsageTimeEntry> getAllEntries();
}
