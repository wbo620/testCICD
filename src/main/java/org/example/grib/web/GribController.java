package org.example.grib.web;

import java.time.Instant;
import java.util.List;

import org.example.grib.config.GribProperties;
import org.example.grib.service.Grib2Service;
import org.example.grib.web.dto.QueryRequest;
import org.example.grib.web.dto.Responses;
import org.example.grib.web.dto.TimeParser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GRIB2 要素查询接口。
 *
 * <pre>
 * GET  /api/grib/variables              列出文件中的所有变量
 * GET  /api/grib/grid/{variable}        查看网格（经纬度轴与时次）
 * GET  /api/grib/value?lat=&amp;lon=&amp;time=    按经纬度加时次取值
 * POST /api/grib/query                 按经纬度加时次取值（JSON 请求体）
 * </pre>
 */
@RestController
@RequestMapping(path = "/api/grib", produces = MediaType.APPLICATION_JSON_VALUE)
public class GribController {

    private final Grib2Service service;
    private final GribProperties properties;

    public GribController(Grib2Service service, GribProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    /** 列出 GRIB2 文件中的变量。 */
    @GetMapping("/variables")
    public List<Responses.VariableResponse> variables() {
        return service.listVariables().stream().map(Responses.VariableResponse::from).toList();
    }

    /** 查看某变量的网格信息。 */
    @GetMapping("/grid/{variable}")
    public Responses.GridResponse grid(@PathVariable String variable,
                                       @RequestParam(defaultValue = "false") boolean axes) {
        return Responses.GridResponse.from(service.grid(variable), axes);
    }

    /** 按经纬度加时次查询要素值（查询串方式）。 */
    @GetMapping("/value")
    public Responses.ValueResponse value(
            @RequestParam(required = false) String variable,
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) String time) {
        String name = (variable == null || variable.isBlank()) ? properties.getDefaultVariable() : variable;
        Instant t = TimeParser.parse(time);
        return Responses.ValueResponse.from(service.query(name, lat, lon, t));
    }

    /** 按经纬度加时次查询要素值（JSON 方式）。 */
    @PostMapping("/query")
    public Responses.ValueResponse query(@RequestBody QueryRequest request) {
        String name = (request.variable() == null || request.variable().isBlank())
                ? properties.getDefaultVariable() : request.variable();
        Instant t = request.parseTime();
        return Responses.ValueResponse.from(service.query(name, request.lat(), request.lon(), t));
    }
}