package org.example.grib.exception;

/** GRIB2 文件中找不到请求的变量。 */
public class VariableNotFoundException extends RuntimeException {
    public VariableNotFoundException(String message) {
        super(message);
    }
}