package org.example.grib.web.dto;

import java.time.Instant;

/**
 * 查询请求体。
 *
 * @param variable 变量名，默认 Visibility_surface
 * @param lat      纬度
 * @param lon      经度
 * @param time     时次（ISO-8601 或 epoch 毫秒），可为空
 */
public record QueryRequest(String variable, Double lat, Double lon, String time) {

    public Instant parseTime() {
        return TimeParser.parse(time);
    }
}