package org.example.grib.web;

import java.time.Instant;
import java.util.Map;

import org.example.grib.exception.GribReadException;
import org.example.grib.exception.VariableNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一异常处理。 */
@RestControllerAdvice
public class GribExceptionHandler {

    @ExceptionHandler(VariableNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(VariableNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "variable_not_found", "message", e.getMessage()));
    }

    @ExceptionHandler({GribReadException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "bad_request", "message", String.valueOf(e.getMessage()),
                        "timestamp", Instant.now().toString()));
    }
}