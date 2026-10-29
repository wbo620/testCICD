package org.example.grib.web.dto;

import java.time.Instant;
import java.util.List;

import org.example.grib.model.GridInfo;
import org.example.grib.model.GridPoint;
import org.example.grib.model.VariableInfo;

/** REST 响应对象。 */
public final class Responses {

    private Responses() {
    }

    /** 变量查询结果。 */
    public record ValueResponse(
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

        public static ValueResponse from(GridPoint p) {
            return new ValueResponse(p.variable(), p.requestedLat(), p.requestedLon(), p.requestedTime(),
                    p.matchedLat(), p.matchedLon(), p.matchedTime(), p.latIndex(), p.lonIndex(), p.timeIndex(),
                    p.value(), p.units(), p.exact());
        }
    }

    /** 变量列表响应。 */
    public record VariableResponse(String name, String longName, String units, List<String> dimensions,
                                   int[] shape, List<Instant> times, int latCount, int lonCount,
                                   double[] latRange, double[] lonRange) {

        public static VariableResponse from(VariableInfo v) {
            return new VariableResponse(v.name(), v.longName(), v.units(), v.dimensions(), v.shape(),
                    v.times(), v.latCount(), v.lonCount(), v.latRange(), v.lonRange());
        }
    }

    /** 网格信息响应。 */
    public record GridResponse(String variable, double latMin, double latMax, double lonMin, double lonMax,
                                int latCount, int lonCount, List<Instant> times, double[] lats, double[] lons) {

        public static GridResponse from(GridInfo g, boolean withAxes) {
            return new GridResponse(g.variable(), g.latMin(), g.latMax(), g.lonMin(), g.lonMax(),
                    g.lats().length, g.lons().length, g.times(),
                    withAxes ? g.lats() : new double[0],
                    withAxes ? g.lons() : new double[0]);
        }
    }
}