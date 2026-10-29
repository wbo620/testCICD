package org.example.grib.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.example.grib.config.GribProperties;
import org.example.grib.exception.GribReadException;
import org.example.grib.exception.VariableNotFoundException;
import org.example.grib.model.GridInfo;
import org.example.grib.model.GridPoint;
import org.example.grib.model.VariableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 * GRIB2 文件读取服务。
 *
 * <p>基于 netCDF-Java CDM 打开 GRIB2 文件，文件中的要素会被解析为同名变量
 * （例如 {@code Visibility_surface}，维度为 time/lat/lon），同时提供
 * {@code lat} / {@code lon} / {@code time} 三个坐标轴。
 * 业务上通过 {@link #query(String, double, double, Instant)} 按经纬度加时次取值。</p>
 */
@Service
public class Grib2Service implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Grib2Service.class);

    /** 坐标轴名称关键字。 */
    private static final String LAT = "lat";
    private static final String LON = "lon";
    private static final String TIME = "time";

    private final GribProperties properties;
    private final Object lock = new Object();

    private NetcdfFile ncFile;

    public Grib2Service(GribProperties properties) {
        this.properties = properties;
    }

    /** 打开并缓存 GRIB2 文件。 */
    public NetcdfFile open() {
        synchronized (lock) {
            if (ncFile != null) {
                return ncFile;
            }
            Path path = resolveFile();
            try {
                log.info("正在打开 GRIB2 文件: {}", path.toAbsolutePath());
                ncFile = NetcdfFile.open(path.toString());
                log.info("GRIB2 文件已打开, 变量数: {}, 维度数: {}", ncFile.getVariables().size(),
                        ncFile.getDimensions().size());
                return ncFile;
            } catch (IOException e) {
                throw new GribReadException("无法打开 GRIB2 文件: " + path.toAbsolutePath(), e);
            }
        }
    }

    /** 文件中所有变量（含坐标轴变量）。 */
    public List<VariableInfo> listVariables() {
        NetcdfFile nc = open();
        List<VariableInfo> list = new ArrayList<>();
        for (Variable v : nc.getVariables()) {
            list.add(describe(nc, v));
        }
        return list;
    }

    /** 变量的经纬度轴与时次。 */
    public GridInfo grid(String variableName) {
        NetcdfFile nc = open();
        Variable v = requireVariable(nc, variableName);
        List<String> dims = dimensionNames(v);
        double[] lats = readLatAxis(nc, dims);
        double[] lons = readLonAxis(nc, dims);
        List<Instant> times = readTimeAxis(nc, dims);
        return new GridInfo(v.getShortName(), lats, lons, times);
    }

    /**
     * 按经纬度加时次查询要素值。
     *
     * <p>经纬度采用最近邻匹配（本文件网格 0.25 度）；时次优先精确匹配，否则取最近时次。</p>
     *
     * @param variableName 变量名，例如 Visibility_surface
     * @param lat          纬度（度，北纬为正）
     * @param lon          经度（度，东经为正）
     * @param time         时次，可为 null（取文件中的第一个时次）
     */
    public GridPoint query(String variableName, double lat, double lon, Instant time) {
        NetcdfFile nc = open();
        Variable v = requireVariable(nc, variableName);
        List<String> dims = dimensionNames(v);

        double[] lats = readLatAxis(nc, dims);
        double[] lons = readLonAxis(nc, dims);
        List<Instant> times = readTimeAxis(nc, dims);

        int latIndex = nearestIndex(lats, lat);
        int lonIndex = nearestIndex(lons, lon);
        int timeIndex = 0;
        if (time != null && !times.isEmpty()) {
            timeIndex = nearestTimeIndex(times, time);
        }

        double value = readValue(v, dims, latIndex, lonIndex, timeIndex);

        Instant matchedTime = times.isEmpty() ? null : times.get(timeIndex);
        boolean exact = Math.abs(lats[latIndex] - lat) < 1e-9
                && Math.abs(lons[lonIndex] - lon) < 1e-9
                && (time == null || matchedTime == null
                        || Math.abs(matchedTime.toEpochMilli() - time.toEpochMilli()) < 1000);

        return new GridPoint(
                v.getShortName(),
                lat, lon, time,
                lats[latIndex], lons[lonIndex], matchedTime,
                latIndex, lonIndex, timeIndex,
                value,
                v.getUnitsString(),
                exact);
    }

    // ------------------------------------------------------------------ 内部实现

    private Variable requireVariable(NetcdfFile nc, String name) {
        Variable v = nc.findVariable(name);
        if (v == null) {
            for (Variable candidate : nc.getVariables()) {
                if (candidate.getShortName().equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
            throw new VariableNotFoundException("GRIB2 文件中不存在变量: " + name
                    + ", 可用变量: " + nc.getVariables().stream().map(Variable::getShortName).toList());
        }
        return v;
    }

    /** 变量的维度名列表。 */
    private List<String> dimensionNames(Variable v) {
        List<String> names = new ArrayList<>();
        for (Dimension d : v.getDimensions()) {
            names.add(d.getName());
        }
        return names;
    }

    /** 读取纬度轴（一维）。 */
    private double[] readLatAxis(NetcdfFile nc, List<String> dims) {
        Variable coord = findCoordinate(nc, dims, LAT);
        return coord == null ? new double[0] : readDoubles(coord);
    }

    /** 读取经度轴（一维）。 */
    private double[] readLonAxis(NetcdfFile nc, List<String> dims) {
        Variable coord = findCoordinate(nc, dims, LON);
        return coord == null ? new double[0] : readDoubles(coord);
    }

    /** 读取时次轴。 */
    private List<Instant> readTimeAxis(NetcdfFile nc, List<String> dims) {
        List<Instant> times = new ArrayList<>();
        Variable coord = findCoordinate(nc, dims, TIME);
        if (coord == null) {
            return times;
        }
        String units = coord.getUnitsString();
        Array arr;
        try {
            arr = coord.read();
        } catch (IOException e) {
            throw new GribReadException("读取时间轴失败", e);
        }
        for (int i = 0; i < arr.getSize(); i++) {
            times.add(TimeUnits.toInstant(arr.getDouble(i), units));
        }
        return times;
    }

    /** 在变量维度中寻找名字匹配关键字的坐标变量。 */
    private Variable findCoordinate(NetcdfFile nc, List<String> dims, String keyword) {
        for (String dim : dims) {
            if (dim.toLowerCase(Locale.ROOT).contains(keyword)) {
                Variable coord = nc.findVariable(dim);
                if (coord != null) {
                    return coord;
                }
            }
        }
        return nc.findVariable(keyword);
    }

    private double[] readDoubles(Variable v) {
        Array arr;
        try {
            arr = v.read();
        } catch (IOException e) {
            throw new GribReadException("读取坐标变量失败: " + v.getShortName(), e);
        }
        double[] out = new double[(int) arr.getSize()];
        for (int i = 0; i < out.length; i++) {
            out[i] = arr.getDouble(i);
        }
        return out;
    }

    /** 读取指定格点的数值。 */
    private double readValue(Variable v, List<String> dims, int latIndex, int lonIndex, int timeIndex) {
        int[] shape = v.getShape();
        int[] origin = new int[shape.length];
        int[] ones = new int[shape.length];
        Arrays.fill(ones, 1);
        for (int i = 0; i < dims.size(); i++) {
            String d = dims.get(i).toLowerCase(Locale.ROOT);
            if (d.contains(TIME)) {
                origin[i] = timeIndex;
            } else if (d.contains(LAT)) {
                origin[i] = latIndex;
            } else if (d.contains(LON)) {
                origin[i] = lonIndex;
            }
        }
        try {
            Array section = v.read(origin, ones);
            return section.getDouble(0);
        } catch (Exception e) {
            // 部分 GRIB2 实现不支持按段读取，退化为整体读取
            log.debug("按段读取失败, 改为整体读取: {}", e.getMessage());
            try {
                Array all = v.read();
                return all.getDouble(Index.factory(origin));
            } catch (IOException io) {
                throw new GribReadException("读取要素数据失败: " + v.getShortName(), io);
            }
        }
    }

    /** 最近邻：找到最接近 target 的下标。 */
    private int nearestIndex(double[] axis, double target) {
        if (axis.length == 0) {
            return 0;
        }
        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < axis.length; i++) {
            double diff = Math.abs(axis[i] - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    /** 最近时次（优先精确匹配）。 */
    private int nearestTimeIndex(List<Instant> times, Instant target) {
        long targetMillis = target.toEpochMilli();
        for (int i = 0; i < times.size(); i++) {
            if (Math.abs(times.get(i).toEpochMilli() - targetMillis) < 1000) {
                return i;
            }
        }
        int best = 0;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < times.size(); i++) {
            long diff = Math.abs(times.get(i).toEpochMilli() - targetMillis);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private VariableInfo describe(NetcdfFile nc, Variable v) {
        List<String> dims = dimensionNames(v);
        double[] lats = readLatAxis(nc, dims);
        double[] lons = readLonAxis(nc, dims);
        List<Instant> times = readTimeAxis(nc, dims);
        return new VariableInfo(
                v.getShortName(),
                v.getDescription(),
                v.getUnitsString(),
                dims,
                v.getShape(),
                times,
                lats.length,
                lons.length,
                range(lats),
                range(lons),
                missingValue(v));
    }

    private double missingValue(Variable v) {
        Attribute attr = v.findAttribute("missing_value");
        if (attr == null) {
            return Double.NaN;
        }
        Number n = attr.getNumericValue();
        return n == null ? Double.NaN : n.doubleValue();
    }

    private double[] range(double[] axis) {
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

    /** 解析配置的 GRIB2 文件路径。 */
    public Path resolveFile() {
        String configured = properties.getFile();
        if (configured == null || configured.isBlank()) {
            throw new GribReadException("未配置 grib.file");
        }
        Path p = Paths.get(configured);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p);
        }
        if (!Files.exists(p)) {
            throw new GribReadException("GRIB2 文件不存在: " + p.toAbsolutePath());
        }
        return p;
    }

    @Override
    public void destroy() {
        synchronized (lock) {
            if (ncFile != null) {
                try {
                    ncFile.close();
                } catch (IOException e) {
                    log.warn("关闭 GRIB2 文件失败", e);
                }
                ncFile = null;
            }
        }
    }
}