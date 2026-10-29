package org.example.grib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GRIB2 文件相关配置。
 */
@Component
@ConfigurationProperties(prefix = "grib")
public class GribProperties {

    /** GRIB2 文件路径（相对运行目录或绝对路径）。 */
    private String file = "gfs_20260701_00z_f000.grib2";

    /** 默认变量名。 */
    private String defaultVariable = "Visibility_surface";

    /** 是否在启动时立即打开并解析文件。 */
    private boolean preload = true;

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getDefaultVariable() {
        return defaultVariable;
    }

    public void setDefaultVariable(String defaultVariable) {
        this.defaultVariable = defaultVariable;
    }

    public boolean isPreload() {
        return preload;
    }

    public void setPreload(boolean preload) {
        this.preload = preload;
    }
}