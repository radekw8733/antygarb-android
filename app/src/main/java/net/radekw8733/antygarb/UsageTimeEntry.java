package net.radekw8733.antygarb;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDateTime;

@Entity
public class UsageTimeEntry {
    @PrimaryKey
    public long id;

    @ColumnInfo(name = "app_type")
    public Type type;

    @ColumnInfo(name = "app_started")
    public LocalDateTime appStarted;

    @ColumnInfo(name = "app_stopped")
    public LocalDateTime appStopped;

    public enum Type {
        APP,
        SERVICE
    }
}
