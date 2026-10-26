package com.hl.study.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

@Data
@Schema(description = "统一返回体")
public class R<T> implements Serializable {
    private Integer code;
    private String message;
    private T data;
    private String traceId; // 出问题让用户报这个 id，你就能定位日志
    private Long timestamp;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        r.setTraceId(MDC.get("traceId"));
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(Integer code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setTraceId(MDC.get("traceId"));
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
}
