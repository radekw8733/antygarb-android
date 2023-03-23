package net.radekw8733.antygarb.db;

import androidx.room.TypeConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class TypeConverters {
    @TypeConverter
    public static LocalDateTime fromTimestamp(String value) {
        return LocalDateTime.parse(value);
    }

    @TypeConverter
    public static String dateToTimestamp(LocalDateTime date) {
        return date.toString();
    }
}
