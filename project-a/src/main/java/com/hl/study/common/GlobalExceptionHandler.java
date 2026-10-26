package com.hl.study.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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
            case 429 -> HttpStatus.TOO_MANY_REQUESTS; // 注意大纲这里拼写有误，正确的是 TOO_MANY_REQUESTS
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(R.fail(e.getCode(), e.getMessage()));
    }

    /** 参数校验异常：把字段级错误拼成一句话 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 兜底：绝不把堆栈抛给用户 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleEx(Exception e) {
        log.error("system error", e);
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}