package com.hl.study.common;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Hidden // 关键：防止 Swagger 扫描这个类导致文档报错
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：错误码映射成 HTTP 状态，1xxx 业务码统一 400 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> handleBiz(BusinessException e) {
        log.warn("business error, code={}, msg={}", e.getCode(), e.getMessage());
        HttpStatus status = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND; // 修复 404 被兜底成 400 的问题
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(R.fail(e.getCode(), e.getMessage()));
    }

    /** 参数校验异常：把字段级错误拼成一句话，并返回 HTTP 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        // 改为返回 ResponseEntity，状态码设为 400
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.PARAM_ERROR.getCode(), msg));
    }

    /** 兜底：绝不把堆栈抛给用户，返回 HTTP 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleEx(Exception e) {
        log.error("system error", e);
        // 统一改为返回 ResponseEntity，状态码设为 500
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }
}