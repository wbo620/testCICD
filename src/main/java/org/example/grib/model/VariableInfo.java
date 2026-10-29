package org.example.grib.model;

import java.time.Instant;
import java.util.List;

/**
 * 一个 GRIB2 变量（要素）的元信息。
 *
 * @param name          变量名，例如 Visibility_surface
 * @param longName      变量长名称
 * @param units         单位
 * @param dimensions    维度名，例如 [time, lat, lon]
 * @param shape         各维度长度
 * @param times         该变量包含的时次
 * @param latCount      纬度轴长度
 * @param lonCount      经度轴长度
 * @param latRange      纬度范围 [min, max]
 * @param lonRange      经度范围 [min, max]
 * @param missingValue  缺测值
 */
public record VariableInfo(
        String name,
        String longName,
        String units,
        List<String> dimensions,
        int[] shape,
        List<Instant> times,
        int latCount,
        int lonCount,
        double[] latRange,
        double[] lonRange,
        double missingValue) {

    public long pointCount() {
        long n = 1;
        for (int s : shape) {
            n *= s;
        }
        return n;
    }
}