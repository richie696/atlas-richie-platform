package com.richie.contract.exception;

import lombok.Getter;

/**
 * 基础异常类
 */
public class BaseException extends RuntimeException {

    @Getter
    private String code;

    public BaseException() {
    }

    public BaseException(String message) {
        super(message);
        this.code = "500";
    }

    public BaseException(String message, Exception e) {
        super(message, e);
        this.code = "500";
    }

    public BaseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
