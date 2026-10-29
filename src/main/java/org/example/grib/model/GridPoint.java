package org.example.grib.model;

import java.time.Instant;

/**
 * 一次查询的结果：请求的经纬度与时间，以及命中的格点与数值。
 *
 * @param variable      变量名
 * @param requestedLat  请求纬度
 * @param requestedLon  请求经度
 * @param requestedTime 请求时次
 * @param matchedLat    实际命中的纬度（最近格点）
 * @param matchedLon    实际命中的经度（最近格点）
 * @param matchedTime   实际命中的时次
 * @param latIndex      纬度索引
 * @param lonIndex      经度索引
 * @param timeIndex     时次索引
 * @param value         数值
 * @param units         单位
 * @param exact         经纬度与时次是否全部精确命中
 */
public record GridPoint(
        String variable,
        double requestedLat,
        double requestedLon,
        Instant requestedTime,
        double matchedLat,
        double matchedLon,
        Instant matchedTime,
        int latIndex,
        int lonIndex,
        int timeIndex,
        double value,
        String units,
        boolean exact) {
}