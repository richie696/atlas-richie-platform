package com.richie.component.web.config;

import com.richie.contract.exception.PlatformRuntimeException;
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
 * 平台全局异常切面处理器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:29:23
 */
@Slf4j
@ConditionalOnWebApplication
@ControllerAdvice("com.richie.service")
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

    /**
     * 默认异常处理器
     *
     * @param exception 异常信息
     * @return 返回应答数据
     */
    @ExceptionHandler
    public ResponseEntity<?> defaultErrorHandler(Exception exception) {

        var headers = new HttpHeaders();
        if (PlatformRuntimeException.class.isAssignableFrom(exception.getClass())) {
            var e = (PlatformRuntimeException) exception;
            log.error(String.format(
                    "PlatformGlobalExceptionControllerAdvice.PlatformRuntimeException, exception = %s",
                    exception.getMessage()), exception);
            headers.set(RD_API_RESPONSE_STATUS, EnumErrorMassage.REQUEST_PARAMS_INVALID.getStatusCode());
            headers.set(RD_API_RESPONSE_MESSAGE, e.getMessage());
            headers.set(RD_API_RESPONSE_CODE, EnumErrorMassage.REQUEST_PARAMS_INVALID.getI18nCode());
            return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
        } else if (MissingServletRequestParameterException.class.isAssignableFrom(exception.getClass())) {
            log.error(String.format(
                    "PlatformGlobalExceptionControllerAdvice.MissingServletRequestParameterException, exception = %s",
                    exception.getMessage()), exception);

            headers.set(RD_API_RESPONSE_STATUS, EnumErrorMassage.REQUEST_PARAMS_INVALID.getStatusCode());
            headers.set(RD_API_RESPONSE_MESSAGE, EnumErrorMassage.REQUEST_PARAMS_INVALID.getDefaultMessage());
            headers.set(RD_API_RESPONSE_CODE, EnumErrorMassage.REQUEST_PARAMS_INVALID.getI18nCode());
            return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);

        } else {
            log.error(String.format("PlatformGlobalExceptionControllerAdvice.defaultErrorHandler, exception = %s",
                    exception.getMessage()), exception);
            headers.set(RD_API_RESPONSE_STATUS, String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
            headers.set(RD_API_RESPONSE_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
            headers.set(RD_API_RESPONSE_CODE, HttpStatus.INTERNAL_SERVER_ERROR.toString());
            return new ResponseEntity<>(headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
