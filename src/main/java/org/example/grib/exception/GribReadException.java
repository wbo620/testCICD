package org.example.grib.exception;

/** GRIB2 文件打开或解析失败。 */
public class GribReadException extends RuntimeException {
    public GribReadException(String message, Throwable cause) {
        super(message, cause);
    }

    public GribReadException(String message) {
        super(message);
    }
}