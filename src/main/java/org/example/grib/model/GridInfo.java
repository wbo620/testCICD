package org.example.grib.model;

import java.time.Instant;
import java.util.List;

/**
 * 网格（经纬度轴）描述。
 *
 * @param variable 变量名
 * @param lats     纬度轴（可能升序也可能降序，GFS 为 90 到 -90）
 * @param lons     经度轴
 * @param times    时次
 */
public record GridInfo(String variable, double[] lats, double[] lons, List<Instant> times) {

    public double latMin() {
        return minMax(lats)[0];
    }

    public double latMax() {
        return minMax(lats)[1];
    }

    public double lonMin() {
        return minMax(lons)[0];
    }

    public double lonMax() {
        return minMax(lons)[1];
    }

    private static double[] minMax(double[] axis) {
        if (axis.length == 0) {
            return new double[]{Double.NaN, Double.NaN};
        }
        double min = axis[0];
        double max = axis[0];
        for (double d : axis) {
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return new double[]{min, max};
    }
}