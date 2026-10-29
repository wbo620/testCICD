package org.example.grib.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * GRIB2 / CDM 时间单位解析。
 *
 * <p>CDM 把时次表示为 &quot;毫秒/秒/小时 自 &lt;基准时刻&gt; 起&quot;，例如
 * {@code milliseconds since 2026-07-01T00:00:00Z}，这里把它换算成 {@link Instant}。</p>
 */
public final class TimeUnits {

    private TimeUnits() {
    }

    /** 按 units 把数值换算为时刻。 */
    public static Instant toInstant(double value, String units) {
        if (units == null || units.isBlank()) {
            return Instant.ofEpochMilli((long) value);
        }
        String lower = units.toLowerCase();
        int idx = lower.indexOf(" since ");
        if (idx < 0) {
            // 直接就是时间字符串
            Instant direct = parseInstant(units);
            return direct != null ? direct : Instant.ofEpochMilli((long) value);
        }
        String unit = lower.substring(0, idx).trim();
        String originText = units.substring(idx + " since ".length()).trim();
        Instant origin = parseInstant(originText);
        if (origin == null) {
            return Instant.ofEpochMilli((long) value);
        }
        long millis;
        switch (unit) {
            case "millisecond", "milliseconds", "ms" -> millis = (long) value;
            case "second", "seconds", "sec", "s" -> millis = (long) (value * 1000);
            case "minute", "minutes", "min" -> millis = (long) (value * 60_000);
            case "hour", "hours", "h" -> millis = (long) (value * 3_600_000);
            case "day", "days", "d" -> millis = (long) (value * 86_400_000);
            default -> millis = (long) value;
        }
        return origin.plusMillis(millis);
    }

    /** 解析基准时刻字符串。 */
    private static Instant parseInstant(String text) {
        String t = text.trim();
        try {
            return Instant.parse(t);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        try {
            return OffsetDateTime.parse(t).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDateTime.parse(t).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDate.parse(t).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}