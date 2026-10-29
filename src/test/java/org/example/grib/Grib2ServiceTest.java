package org.example.grib;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.assertj.core.data.Offset;
import org.example.grib.config.GribProperties;
import org.example.grib.model.GridInfo;
import org.example.grib.model.GridPoint;
import org.example.grib.model.VariableInfo;
import org.example.grib.service.Grib2Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 读取 gfs_20260701_00z_f000.grib2 的结构与取值校验。
 *
 * <p>期望值来自 ECMWF eccodes 对同一文件的解码结果。</p>
 */
class Grib2ServiceTest {

    private Grib2Service service;

    @BeforeEach
    void setUp() {
        GribProperties properties = new GribProperties();
        properties.setFile("gfs_20260701_00z_f000.grib2");
        service = new Grib2Service(properties);
    }

    @Test
    @DisplayName("文件中包含 lat / lon / Visibility_surface / time")
    void listsVariables() {
        List<VariableInfo> variables = service.listVariables();
        List<String> names = variables.stream().map(VariableInfo::name).toList();
        assertThat(names).contains("lat", "lon", "Visibility_surface", "time");
    }

    @Test
    @DisplayName("网格为 0.25 度全球经纬网, 时次为 2026-07-01 00Z")
    void readsGrid() {
        GridInfo grid = service.grid("Visibility_surface");
        assertThat(grid.lats()).hasSize(721);
        assertThat(grid.lons()).hasSize(1440);
        assertThat(grid.latMax()).isEqualTo(90.0);
        assertThat(grid.latMin()).isEqualTo(-90.0);
        assertThat(grid.lonMax()).isEqualTo(359.75);
        assertThat(grid.times()).containsExactly(Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    @DisplayName("按经纬度加时次取到 Visibility_surface 的值")
    void queriesValue() {
        GridPoint p = service.query("Visibility_surface", 39.9, 116.4, Instant.parse("2026-07-01T00:00:00Z"));
        assertThat(p.value()).isEqualTo(24135.113261413575, Offset.offset(1e-3));
        assertThat(p.matchedLat()).isEqualTo(40.0, Offset.offset(1e-6));
        assertThat(p.matchedLon()).isEqualTo(116.5, Offset.offset(1e-6));
        assertThat(p.matchedTime()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    }

    @Test
    @DisplayName("北极点的能见度")
    void queriesNorthPole() {
        // 期望值取自 ECMWF eccodes 对同一文件的解码结果（90N, 0E 处 Visibility_surface = 36.71326141357422）。
        GridPoint p = service.query("Visibility_surface", 90.0, 0.0, null);
        assertThat(p.value()).isEqualTo(36.71326141357422, Offset.offset(1e-3));
        assertThat(p.matchedLat()).isEqualTo(90.0, Offset.offset(1e-6));
        assertThat(p.matchedLon()).isEqualTo(0.0, Offset.offset(1e-6));
    }
}