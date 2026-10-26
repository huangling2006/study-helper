package com.hl.study.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
    }

    public BusinessException(ErrorCode ec, String detail) {
        super(detail != null ? detail : ec.getMessage());
        this.code = ec.getCode();
    }
}