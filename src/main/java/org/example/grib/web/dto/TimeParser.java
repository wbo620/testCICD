package org.example.grib.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** 时次字符串解析：支持 ISO-8601 与 epoch 毫秒。 */
public final class TimeParser {

    private TimeParser() {
    }

    public static Instant parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        try {
            return Instant.parse(t);
        } catch (DateTimeParseException ignored) {
            // 尝试其它格式
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(t));
        } catch (NumberFormatException ignored) {
            // 尝试其它格式
        }
        try {
            return LocalDateTime.parse(t).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // 尝试其它格式
        }
        try {
            return LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("无法解析时次: " + text
                    + ", 支持 ISO-8601（如 2026-07-01T00:00:00Z）或 epoch 毫秒");
        }
    }
}