/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package cn.richie696.component.web.core.exception;

import cn.richie696.contract.exception.BaseException;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.contract.exception.I18nMessageKeyException;
import cn.richie696.contract.exception.PlatformDataAccessException;
import cn.richie696.contract.exception.PlatformRuntimeException;
import cn.richie696.contract.model.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Servlet MVC 的最后一道异常边界。
 *
 * <p>业务异常可以由业务服务定义更高优先级的 advice 覆盖；没有被覆盖的异常都会被转换成
 * {@link ApiResult}，因此不会回落到容器默认 HTML 错误页。详细堆栈只写入服务日志，响应体不暴露
 * SQL、堆栈或内部实现细节。</p>
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice
public class GlobalExceptionControllerAdvice {

    public static final String RD_API_RESPONSE_STATUS = "X-RD-API-Response-Status";
    public static final String RD_API_RESPONSE_MESSAGE = "X-RD-API-Response-Message";
    public static final String RD_API_RESPONSE_CODE = "X-RD-API-Response-Code";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(I18nMessageKeyException.class)
    public ResponseEntity<ApiResult<Void>> i18nMessageKey(I18nMessageKeyException exception,
                                                            HttpServletRequest request) {
        return response(HttpStatus.valueOf(exception.getStatusCode()), exception.getErrorCode(),
                exception.getMessageKey(), request, exception, false);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> business(BusinessException exception, HttpServletRequest request) {
        return response(HttpStatus.OK, code(exception.getCode(), "BUSINESS_ERROR"),
                safeMessage(exception.getMessage(), "请求无法完成"), request, exception, false);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResult<Void>> base(BaseException exception, HttpServletRequest request) {
        HttpStatus status = statusFromCode(exception.getCode(), HttpStatus.BAD_REQUEST);
        return response(status, code(exception.getCode(), "REQUEST_ERROR"),
                safeMessage(exception.getMessage(), "请求参数或业务状态不合法"), request, exception, false);
    }

    @ExceptionHandler(PlatformRuntimeException.class)
    public ResponseEntity<ApiResult<Void>> platformRuntime(PlatformRuntimeException exception,
                                                            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "REQUEST_ERROR",
                safeMessage(exception.getMessage(), "请求无法完成"), request, exception, false);
    }

    @ExceptionHandler(PlatformDataAccessException.class)
    public ResponseEntity<ApiResult<Void>> platformDataAccess(PlatformDataAccessException exception,
                                                                HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_ACCESS_ERROR", "数据访问失败，请稍后重试",
                request, exception, true);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MissingPathVariableException.class,
            MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResult<Void>> invalidRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, EnumErrorMassage.REQUEST_PARAMS_INVALID.getI18nCode(),
                validationMessage(exception), request, exception, false);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResult<Void>> responseStatus(ResponseStatusException exception,
                                                            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = status.is5xxServerError()
                ? "服务器暂时无法处理请求，请稍后重试"
                : safeMessage(exception.getReason(), status.getReasonPhrase());
        return response(status, String.valueOf(status.value()), message, request, exception,
                status.is5xxServerError());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> notFound(NoResourceFoundException exception,
                                                     HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "404", "请求资源不存在", request, exception, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> fallback(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "500", "服务器内部错误，请稍后重试",
                request, exception, true);
    }

    private ResponseEntity<ApiResult<Void>> response(HttpStatus status, String code, String message,
                                                      HttpServletRequest request, Throwable exception,
                                                      boolean error) {
        String requestId = requestId(request);
        if (error) {
            log.error("Unhandled web exception: method={} path={} requestId={} type={} message={}",
                    request.getMethod(), request.getRequestURI(), requestId,
                    exception.getClass().getName(), exception.getMessage(), exception);
        } else {
            log.warn("Handled web exception: method={} path={} requestId={} type={} message={}",
                    request.getMethod(), request.getRequestURI(), requestId,
                    exception.getClass().getName(), exception.getMessage());
        }

        ApiResult<Void> result = new ApiResult<>();
        result.setSuccess(false).setCode(code).setMsg(message).setRequestId(requestId);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(RD_API_RESPONSE_STATUS, String.valueOf(status.value()))
                .header(RD_API_RESPONSE_MESSAGE, message)
                .header(RD_API_RESPONSE_CODE, code);
        if (requestId != null) {
            builder.header(REQUEST_ID_HEADER, requestId);
        }
        return builder.body(result);
    }

    private static HttpStatus statusFromCode(String value, HttpStatus fallback) {
        HttpStatus status = HttpStatus.resolve(parseCode(value));
        return status == null || !status.isError() ? fallback : status;
    }

    private static int parseCode(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String code(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safeMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + " 格式不正确")
                    .orElse(EnumErrorMassage.REQUEST_PARAMS_INVALID.getDefaultMessage());
        }
        if (exception instanceof BindException bind) {
            return bind.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + " 格式不正确")
                    .orElse(EnumErrorMassage.REQUEST_PARAMS_INVALID.getDefaultMessage());
        }
        return EnumErrorMassage.REQUEST_PARAMS_INVALID.getDefaultMessage();
    }

    private static String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? null : requestId.trim();
    }
}
