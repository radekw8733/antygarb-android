package net.radekw8733.antygarb.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.LocalDateTime;

@Entity
public class UsageTimeEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "app_type")
    public Type type;

    @ColumnInfo(name = "app_started")
    public LocalDateTime appStarted;

    @ColumnInfo(name = "app_stopped")
    public LocalDateTime appStopped;

    @ColumnInfo(name = "correctToIncorrect")
    public float correctToIncorrectPoseNormalised;

    @ColumnInfo(name = "correctPoses")
    public long correctPoses;

    @ColumnInfo(name = "incorrectPoses")
    public long incorrectPoses;

    public enum Type {
        APP,
        SERVICE
    }
}
