package com.richie.component.web.config;

import com.richie.contract.exception.BaseException;
import com.richie.contract.exception.PlatformRuntimeException;
import com.richie.contract.model.ApiResult;
import com.richie.component.web.exception.EnumErrorMassage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 平台全局异常切面处理器。
 *
 * <p>不限制包扫描范围，自动覆盖所有 Spring MVC Controller。
 * 业务服务如需定制（如参数校验细节、日志格式），可在本项目内定义优先级更高的
 * {@code @RestControllerAdvice} 覆盖此处默认行为。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:29:23
 */
@Slf4j
@ConditionalOnWebApplication
@ControllerAdvice
public class GlobalExceptionControllerAdvice {

    /**
     * 异常状态编码
     */
    public static final String RD_API_RESPONSE_STATUS = "X-RD-API-Response-Status";

    /**
     * 异常消息
     */
    public static final String RD_API_RESPONSE_MESSAGE = "X-RD-API-Response-Message";

    /**
     * 异常消息编码（国际化使用）
     */
    public static final String RD_API_RESPONSE_CODE = "X-RD-API-Response-Code";

    // ── 业务异常（BaseException 体系，含 BusinessException）────────────────────

    /**
     * 处理业务异常（BaseException 及其子类 BusinessException 等）。
     *
     * <p>业务层通过 {@code throw new BusinessException("code", "message")} 抛出的业务规则违反，
     * 在此统一转为 {@code ApiResult.error()}，HTTP 200 返回。
     * 前端通过 {@code ApiResult.success=false} 判断失败，通过 {@code msg} 展示原因。
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResult<Void>> handleBaseException(BaseException exception) {
        log.warn("BaseException: code={}, msg={}", exception.getCode(), exception.getMessage());
        ApiResult<Void> result = ApiResult.error(
                exception.getCode() != null ? exception.getCode() : "500",
                exception.getMessage() != null ? exception.getMessage() : "未知业务异常");
        return ResponseEntity.ok(result);
    }

    // ── 平台内部异常（向后兼容 Header 模式）────────────────────────────────

    /**
     * 处理平台运行时异常和参数缺失异常。
     *
     * <p>保留原有 Header 响应模式以兼容平台内部服务调用。
     */
    @ExceptionHandler
    public ResponseEntity<?> defaultErrorHandler(Exception exception) {

        var headers = new HttpHeaders();
        if (PlatformRuntimeException.class.isAssignableFrom(exception.getClass())) {
            var e = (PlatformRuntimeException) exception;
            log.error("PlatformGlobalExceptionControllerAdvice.PlatformRuntimeException, exception = {}", exception.getMessage(), exception);
            headers.set(RD_API_RESPONSE_STATUS, EnumErrorMassage.REQUEST_PARAMS_INVALID.getStatusCode());
            headers.set(RD_API_RESPONSE_MESSAGE, e.getMessage());
            headers.set(RD_API_RESPONSE_CODE, EnumErrorMassage.REQUEST_PARAMS_INVALID.getI18nCode());
            return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
        } else if (MissingServletRequestParameterException.class.isAssignableFrom(exception.getClass())) {
            log.error("PlatformGlobalExceptionControllerAdvice.MissingServletRequestParameterException, exception = {}", exception.getMessage(), exception);

            headers.set(RD_API_RESPONSE_STATUS, EnumErrorMassage.REQUEST_PARAMS_INVALID.getStatusCode());
            headers.set(RD_API_RESPONSE_MESSAGE, EnumErrorMassage.REQUEST_PARAMS_INVALID.getDefaultMessage());
            headers.set(RD_API_RESPONSE_CODE, EnumErrorMassage.REQUEST_PARAMS_INVALID.getI18nCode());
            return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);

        } else {
            log.error("PlatformGlobalExceptionControllerAdvice.defaultErrorHandler, exception = {}", exception.getMessage(), exception);
            headers.set(RD_API_RESPONSE_STATUS, String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
            headers.set(RD_API_RESPONSE_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
            headers.set(RD_API_RESPONSE_CODE, HttpStatus.INTERNAL_SERVER_ERROR.toString());
            return new ResponseEntity<>(headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
