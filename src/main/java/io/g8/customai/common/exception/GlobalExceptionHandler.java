package io.g8.customai.common.exception;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        Map<String, String> response = new HashMap<>();

        if (ex instanceof AccessDeniedException) {
            response.put("message", "权限不足，无法访问");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        log.error("e: ", ex);
        response.put("message", "服务器内部错误");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}