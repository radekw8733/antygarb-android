package net.radekw8733.antygarb;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDateTime;

@Entity
public class UsageTimeEntry {
    @PrimaryKey
    public int uuid;

    @ColumnInfo(name = "app_started")
    public LocalDateTime appStarted;

    @ColumnInfo(name = "app_stopped")
    public LocalDateTime appStopped;
}
